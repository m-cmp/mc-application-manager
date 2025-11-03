package kr.co.mcmp.softwarecatalog.kubernetes.service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import kr.co.mcmp.softwarecatalog.application.constants.DeploymentType;
import kr.co.mcmp.softwarecatalog.application.model.ApplicationStatus;
import kr.co.mcmp.softwarecatalog.application.model.DeploymentHistory;
import kr.co.mcmp.softwarecatalog.application.repository.ApplicationStatusRepository;
import kr.co.mcmp.softwarecatalog.application.repository.DeploymentHistoryRepository;
import kr.co.mcmp.softwarecatalog.application.repository.ScalingEventRepository;
import kr.co.mcmp.softwarecatalog.application.model.ScalingEvent;
import kr.co.mcmp.softwarecatalog.application.service.K8sAutoscaleService;
import kr.co.mcmp.ape.cbtumblebug.api.CbtumblebugRestApi;
import kr.co.mcmp.ape.cbtumblebug.dto.K8sClusterDto;
import kr.co.mcmp.softwarecatalog.kubernetes.config.KubernetesClientFactory;
import kr.co.mcmp.softwarecatalog.common.service.RabbitMqAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class KubernetesMonitoringService {

    private final KubernetesClientFactory clientFactory;
    private final DeploymentHistoryRepository historyRepository;
    private final ApplicationStatusRepository statusRepository;
    private final ScalingEventRepository scalingEventRepository;
    private final KubernetesLogCollector logCollector;
    private final K8sAutoscaleService k8sAutoscaleService;
    private final CbtumblebugRestApi cbtumblebugRestApi;
    private final RabbitMqAlertService rabbitMqAlertService;
    
    @Value("${k8s.autoscaling.default-max-nodes:3}")
    private int defaultMaxNodes;
    
    @Value("${k8s.autoscaling.scaling-timeout-seconds:300}")
    private int scalingTimeoutSeconds;
    
    @Value("${k8s.autoscaling.default-node-count:1}")
    private int defaultNodeCount;
    
    @Value("${k8s.autoscaling.test-mode:false}")
    private boolean testMode;
    
    @Value("${k8s.autoscaling.test-cpu-threshold:0.1}")
    private double testCpuThreshold;
    
    @Value("${k8s.autoscaling.test-memory-threshold:0.1}")
    private double testMemoryThreshold;

    
    @Scheduled(fixedRate = 60000) // 1분마다 실행
    public void monitorKubernetesResources() {
        log.debug("Starting Kubernetes resource monitoring");

        List<DeploymentHistory> activeDeployments = getActiveK8sDeployments();
        log.debug("Found {} active K8S deployments to monitor", activeDeployments.size());

        for (DeploymentHistory deployment : activeDeployments) {
            String namespace = deployment.getNamespace();
            String clusterName = deployment.getClusterName();
            log.debug("Monitoring deployment: ID={}, Namespace={}, Cluster={}, CatalogId={}", 
                    deployment.getId(), namespace, clusterName, deployment.getCatalog().getId());

            try (KubernetesClient client = clientFactory.getClient(namespace, clusterName)) {

                if (!isMetricsServerInstalled(client)) {
                    installMetricsServer(client);
                }

                updateApplicationStatus(deployment, client);
            } catch (Exception e) {
                log.error("Error monitoring Kubernetes resources for deployment: ID={}, Namespace={}, Cluster={}, CatalogId={}", 
                        deployment.getId(), namespace, clusterName, 
                        deployment.getCatalog() != null ? deployment.getCatalog().getId() : "null", e);
            }
        }
    }

    private List<DeploymentHistory> getActiveK8sDeployments() {
        List<DeploymentHistory> allDeployments = historyRepository.findAll();
        log.debug("Total deployments found: {}", allDeployments.size());
        
        List<DeploymentHistory> k8sDeployments = allDeployments.stream()
                .filter(d -> DeploymentType.K8S.equals(d.getDeploymentType()))
                .collect(Collectors.toList());
        log.debug("K8S deployments found: {}", k8sDeployments.size());
        
        List<DeploymentHistory> activeDeployments = k8sDeployments.stream()
                .collect(Collectors.groupingBy(DeploymentHistory::getClusterName))
                .values().stream()
                .flatMap(deployments -> deployments.stream()
                        .collect(Collectors.groupingBy(DeploymentHistory::getCatalog))
                        .values().stream()
                        .map(catalogDeployments -> catalogDeployments.stream()
                                .max(Comparator.comparing(DeploymentHistory::getExecutedAt))
                                .orElse(null)))
                .filter(Objects::nonNull)
                .filter(d -> ("RUN".equalsIgnoreCase(d.getActionType().name())
                        || "INSTALL".equalsIgnoreCase(d.getActionType().name()))
                        && ("SUCCESS".equalsIgnoreCase(d.getStatus()) || "RUNNING".equalsIgnoreCase(d.getStatus())))
                .collect(Collectors.toList());
        
        log.debug("Active K8S deployments found: {}", activeDeployments.size());
        activeDeployments.forEach(d -> log.debug("Active deployment: ID={}, ActionType={}, Status={}, CatalogId={}", 
                d.getId(), d.getActionType(), d.getStatus(), 
                d.getCatalog() != null ? d.getCatalog().getId() : "null"));
        
        return activeDeployments;
    }

    @Transactional
    private void updateApplicationStatus(DeploymentHistory deployment, KubernetesClient client) {
        // K8s 배포는 항상 default namespace에 배포됨
        String namespace = "default";
        String clusterName = deployment.getClusterName();
        
        // null 체크 추가
        if (deployment.getCatalog() == null) {
            log.error("Deployment catalog is null for deployment ID: {}", deployment.getId());
            return;
        }
        
        if (deployment.getCatalog().getHelmChart() == null) {
            log.error("HelmChart is null for catalog ID: {}", deployment.getCatalog().getId());
            return;
        }
        
        String appName = deployment.getCatalog().getHelmChart().getChartName();
        Long catalogId = deployment.getCatalog().getId();

        // 모든 Pod 목록 가져오기
        List<Pod> allPods = client.pods().inNamespace(namespace).list().getItems();

        // 앱 이름으로 필터링된 Pod 목록
        List<Pod> pods = allPods.stream()
                .filter(pod -> {
                    String podName = pod.getMetadata().getName();
                    // 더 유연한 매칭: appName으로 시작하거나 포함하는 모든 Pod
                    return podName.startsWith(appName) || 
                           podName.startsWith(appName.toLowerCase()) ||
                           podName.contains(appName) ||
                           podName.contains(appName.toLowerCase()) ||
                           podName.toLowerCase().startsWith(appName.toLowerCase());
                })
                .collect(Collectors.toList());
                
        // 매칭되지 않은 경우 로그 출력
        if (pods.isEmpty()) {
            log.warn("No pods found matching app name '{}' in namespace '{}'", appName, namespace);
        }
            

        // 최신 ApplicationStatus 조회 (기존 메서드 사용)
        ApplicationStatus status = statusRepository.findTopByCatalogIdOrderByCheckedAtDesc(catalogId)
                .orElse(ApplicationStatus.builder()
                        .deploymentType(DeploymentType.K8S)
                        .deploymentHistoryId(deployment.getId())
                        .build());
        
        // 기존 상태가 있더라도 deploymentType과 deploymentHistoryId 업데이트
        if (status.getDeploymentType() == null) {
            status.setDeploymentType(DeploymentType.K8S);
        }
        if (status.getDeploymentHistoryId() == null) {
            status.setDeploymentHistoryId(deployment.getId());
        }

        // Pod 상태 상세 분석
        long runningPods = 0;
        long pendingPods = 0;
        long failedPods = 0;
        long unknownPods = 0;
        
        for (Pod pod : pods) {
            String phase = pod.getStatus().getPhase();
            String podName = pod.getMetadata().getName();
            
            log.debug("Pod '{}' status: {}", podName, phase);
            
            // Pod 상태별 카운트
            switch (phase.toUpperCase()) {
                case "RUNNING":
                    runningPods++;
                    break;
                case "PENDING":
                    pendingPods++;
                    // Pending 상태의 이유 확인
                    if (pod.getStatus().getConditions() != null) {
                        pod.getStatus().getConditions().forEach(condition -> {
                            log.warn("Pod '{}' pending reason: {} - {}", podName, condition.getType(), condition.getMessage());
                        });
                    }
                    
                    // Container 상태 확인 (ImagePullBackOff 등)
                    if (pod.getStatus().getContainerStatuses() != null) {
                        pod.getStatus().getContainerStatuses().forEach(containerStatus -> {
                            if (containerStatus.getState() != null) {
                                if (containerStatus.getState().getWaiting() != null) {
                                    String reason = containerStatus.getState().getWaiting().getReason();
                                    String message = containerStatus.getState().getWaiting().getMessage();
                                    log.error("Pod '{}' container '{}' waiting: {} - {}", 
                                            podName, containerStatus.getName(), reason, message);
                                }
                                if (containerStatus.getState().getTerminated() != null) {
                                    String reason = containerStatus.getState().getTerminated().getReason();
                                    String message = containerStatus.getState().getTerminated().getMessage();
                                    log.error("Pod '{}' container '{}' terminated: {} - {}", 
                                            podName, containerStatus.getName(), reason, message);
                                }
                            }
                        });
                    }
                    break;
                case "FAILED":
                    failedPods++;
                    break;
                default:
                    unknownPods++;
                    log.warn("Pod '{}' has unknown status: {}", podName, phase);
            }
        }
        
        String podStatusSummary = String.format("%d/%d running (%d pending, %d failed, %d unknown)", 
                runningPods, pods.size(), pendingPods, failedPods, unknownPods);
        status.setPodStatus(podStatusSummary);
        
        log.debug("Pod status summary for app '{}': {}", appName, podStatusSummary);
        
        // 각 Pod가 어떤 노드에 스케줄링되어 있는지 로깅
        log.debug("Pod scheduling information for app '{}':", appName);
        for (Pod pod : pods) {
            String podName = pod.getMetadata().getName();
            String nodeName = pod.getSpec().getNodeName();
            String podPhase = pod.getStatus().getPhase();
            log.debug("  - Pod: {}, Node: {}, Status: {}", podName, nodeName != null ? nodeName : "Not scheduled", podPhase);
        }

        Map<String, Object> resourceUsage = getResourceUsagePercentage(client, namespace, appName, pods, runningPods, pendingPods, failedPods);
        status.setCpuUsage((Double) resourceUsage.get("cpuPercentage"));
        status.setMemoryUsage((Double) resourceUsage.get("memoryPercentage"));
        status.setNetworkIn((Double) resourceUsage.get("networkIn"));
        status.setNetworkOut((Double) resourceUsage.get("networkOut"));
        status.setStatus((String) resourceUsage.get("status"));
        status.setServicePort((Integer) resourceUsage.get("port"));
        status.setCheckedAt(LocalDateTime.now());
        status.setClusterName(clusterName);
        // K8s 배포는 항상 default namespace에 배포됨
        //status.setNamespace(namespace);
        status.setNodeGroupName(deployment.getNodeGroupName()); // 노드 그룹 이름 설정
        status.setDeploymentType(DeploymentType.K8S);
        status.setCatalog(deployment.getCatalog()); // catalog 정보 설정 추가

        // 로그 수집 및 저장
        collectAndSaveLogs(client, namespace, appName, status, pods);

        log.debug("Updating ApplicationStatus for catalogId={}, status={}, podStatus={}, cpuUsage={}, memoryUsage={}", 
                catalogId, status.getStatus(), status.getPodStatus(), status.getCpuUsage(), status.getMemoryUsage());
        
        // 오토스케일링 처리 (스케일 아웃만)
        log.debug("Calling handleK8sAutoscaling for deployment: {}, CatalogId: {}", deployment.getId(), catalogId);
        handleK8sAutoscaling(deployment, status, client);
        
        // 스케일 아웃 완료 감지 및 재배포 처리
        checkAndHandleScalingCompletion(deployment, client);
        
        statusRepository.save(status);
        log.debug("ApplicationStatus saved successfully for catalogId={}", catalogId);
    }

    /**
     * 로그를 수집하고 UnifiedLog에 저장합니다.
     * 기존의 개별 로그 테이블 대신 통합 로그 테이블을 사용합니다.
     */
    private void collectAndSaveLogs(KubernetesClient client, String namespace, String appName, 
                                   ApplicationStatus status, List<Pod> pods) {
        try {
            log.debug("UnifiedLog 수집 시작 - Namespace: {}, App: {}", namespace, appName);
            
            // 배포 ID 가져오기
            Long deploymentId = status.getDeploymentHistoryId();
            if (deploymentId == null) {
                log.warn("Deployment ID가 null입니다. 로그 수집을 건너뜁니다. - Namespace: {}, App: {}", namespace, appName);
                return;
            }
            
            // 각 Pod에 대해 로그 수집 및 저장
            for (Pod pod : pods) {
                String podName = pod.getMetadata().getName();
                String clusterName = client.getNamespace() != null ? client.getNamespace() : "default";
                
                // 컨테이너 이름 가져오기 (첫 번째 컨테이너 사용)
                String containerName = "default";
                if (pod.getStatus() != null && pod.getStatus().getContainerStatuses() != null && 
                    !pod.getStatus().getContainerStatuses().isEmpty()) {
                    containerName = pod.getStatus().getContainerStatuses().get(0).getName();
                }
                
                // KubernetesLogCollector를 사용하여 로그 수집 및 UnifiedLog에 저장
                logCollector.collectAndSaveLogs(client, deploymentId, namespace, podName, containerName, clusterName);
            }
            
            log.debug("UnifiedLog 수집 완료 - Namespace: {}, App: {}, Pods: {}", namespace, appName, pods.size());
            
        } catch (Exception e) {
            log.error("UnifiedLog 수집 중 오류 발생 - App: {}, Error: {}", appName, e.getMessage(), e);
        }
    }


    private void checkMetricsServerStatus(KubernetesClient client) {
        Deployment metricsServer = client.apps().deployments()
                .inNamespace("kube-system")
                .withName("metrics-server")
                .get();

        if (metricsServer == null) {
            log.error("Metrics Server not found. Please install it.");
        } else {
            Integer readyReplicas = metricsServer.getStatus().getReadyReplicas();
            if (readyReplicas == null || readyReplicas == 0) {
                log.error("Metrics Server is not ready. Check its status and logs.");
            } else {
                log.debug("Metrics Server is running with {} ready replicas", readyReplicas);
            }
        }
    }

    private Map<String, Object> getResourceUsagePercentage(KubernetesClient client, String namespace, String appName, List<Pod> pods, long runningPods, long pendingPods, long failedPods) {
        ResourceDefinitionContext context = new ResourceDefinitionContext.Builder()
                .withGroup("metrics.k8s.io")
                .withVersion("v1beta1")
                .withKind("PodMetrics")
                .withPlural("pods")
                .withNamespaced(true)
                .build();

        // log.debug("appName : " + appName);
        List<GenericKubernetesResource> podMetrics = client.genericKubernetesResources(context)
                .inNamespace(namespace)
                // .withLabel("app", appName)
                .list()
                .getItems();

        log.debug("Found {} pod metrics in namespace {}", podMetrics.size(), namespace);
        
        // Pod metrics 상세 정보 로깅
        if (!podMetrics.isEmpty()) {
            log.debug("Pod metrics details:");
            podMetrics.forEach(metric -> {
                String podName = metric.getMetadata().getName();
                log.debug("  - Pod: {}, starts with {}: {}", podName, appName.toLowerCase(), podName.startsWith(appName.toLowerCase()));
            });
        }

        if (podMetrics.isEmpty()) {
            log.warn("No pod metrics found. Checking if metrics-server is running...");
            checkMetricsServerStatus(client);
            Map<String, Object> result = new HashMap<>();
            result.put("cpuPercentage", 0.0);
            result.put("memoryPercentage", 0.0);
            result.put("networkIn", 0.0);
            result.put("networkOut", 0.0);
            result.put("status", "UNKNOWN");
            result.put("port", null);
            return result;
        }

        double totalCpuUsage = 0.0;
        double totalMemoryUsage = 0.0;
        double totalNetworkIn = 0.0;
        double totalNetworkOut = 0.0;
        int podCount = 0;

        for (GenericKubernetesResource podMetric : podMetrics) {
            String podName = podMetric.getMetadata().getName();
            boolean matchesApp = podName.startsWith(appName.toLowerCase()) || 
                               podName.startsWith(appName) ||
                               podName.contains(appName.toLowerCase()) ||
                               podName.contains(appName);
            
            log.debug("Processing pod metric: {} - matches app '{}': {}", podName, appName, matchesApp);
            
            if (matchesApp) {
                try {
                    List<Map<String, Object>> containers = (List<Map<String, Object>>) podMetric
                            .getAdditionalProperties().get("containers");
                    if (containers != null) {
                        for (Map<String, Object> container : containers) {
                            Map<String, Object> usage = (Map<String, Object>) container.get("usage");
                            if (usage != null) {
                                String cpuUsage = (String) usage.get("cpu");
                                String memoryUsage = (String) usage.get("memory");
                                // 네트워크 메트릭 추출
                                String networkRxBytes = (String) usage.get("rxBytes");
                                String networkTxBytes = (String) usage.get("txBytes");
                                
                                totalCpuUsage += parseCpuUsage(cpuUsage);
                                totalMemoryUsage += parseMemoryUsage(memoryUsage);
                                
                                if (networkRxBytes != null) {
                                    totalNetworkIn += parseNetworkBytes(networkRxBytes);
                                }
                                if (networkTxBytes != null) {
                                    totalNetworkOut += parseNetworkBytes(networkTxBytes);
                                }
                                
                                log.debug("Added usage for pod {}: CPU={}, Memory={}, NetworkIn={}, NetworkOut={}", 
                                        podName, cpuUsage, memoryUsage, networkRxBytes, networkTxBytes);
                            }
                        }
                        podCount++;
                        log.debug("Processed pod metric for app '{}': {} (total pods: {})", appName, podName, podCount);
                    }
                } catch (Exception e) {
                    log.error("Error processing pod metric for {}: {}", podName, e.getMessage(), e);
                }
            }
        }
        
        log.debug("Final pod count for app '{}': {}, total CPU usage: {}, total memory usage: {}, network in: {}, network out: {}", 
                appName, podCount, totalCpuUsage, totalMemoryUsage, totalNetworkIn, totalNetworkOut);

        double totalCpuCapacity = getTotalCpuCapacity(client);
        double totalMemoryCapacity = getTotalMemoryCapacity(client);

        // 0으로 나누기 방지
        if (totalCpuCapacity == 0) {
            log.warn("Total CPU capacity is 0, setting CPU usage to 0");
            totalCpuCapacity = 1.0; // 0으로 나누기 방지
        }
        if (totalMemoryCapacity == 0) {
            log.warn("Total memory capacity is 0, setting memory usage to 0");
            totalMemoryCapacity = 1.0; // 0으로 나누기 방지
        }

        double cpuUsagePercentage = (totalCpuUsage / totalCpuCapacity) * 100;
        double memoryUsagePercentage = (totalMemoryUsage / (totalMemoryCapacity * 1024)) * 100;

        // 소수점 2자리까지 반올림
        Double roundedCpuUsage = Math.round(cpuUsagePercentage * 100.0) / 100.0;
        Double roundedMemoryUsage = Math.round(memoryUsagePercentage * 100.0) / 100.0;

        // 서비스 포트 정보 수집
        List<Integer> ports = new ArrayList<>();
        client.services().inNamespace(namespace).list().getItems().stream()
                .filter(service -> service.getMetadata().getName().startsWith(appName.toLowerCase()))
                .forEach(service -> {
                    service.getSpec().getPorts().forEach(servicePort -> {
                        if (servicePort.getNodePort() != null) {
                            ports.add(servicePort.getNodePort());
                        }
                    });
                });
        Integer primaryPort = ports.isEmpty() ? null : ports.get(0);

        // 상태 결정 로직 개선
        String status;
        if (runningPods > 0) {
            status = "RUNNING";
        } else if (pods.isEmpty()) {
            status = "STOPPED";
        } else if (failedPods > 0) {
            status = "FAILED";
        } else if (pendingPods > 0) {
            // Pending 상태인 경우 ImagePullBackOff 등을 확인
            boolean hasImagePullError = pods.stream()
                    .filter(pod -> "PENDING".equalsIgnoreCase(pod.getStatus().getPhase()))
                    .anyMatch(pod -> pod.getStatus().getContainerStatuses() != null &&
                            pod.getStatus().getContainerStatuses().stream()
                                    .anyMatch(containerStatus -> 
                                            containerStatus.getState() != null &&
                                            containerStatus.getState().getWaiting() != null &&
                                            "ImagePullBackOff".equals(containerStatus.getState().getWaiting().getReason())));
            
            if (hasImagePullError) {
                status = "IMAGE_PULL_ERROR";
            } else {
                status = "PENDING";
            }
        } else {
            // Pod는 있지만 Metrics가 없는 경우
            status = "UNKNOWN";
        }

        Map<String, Object> result = new HashMap<>();
        result.put("cpuPercentage", roundedCpuUsage != null ? roundedCpuUsage : 0.0);
        result.put("memoryPercentage", roundedMemoryUsage != null ? roundedMemoryUsage : 0.0);
        result.put("networkIn", totalNetworkIn);
        result.put("networkOut", totalNetworkOut);
        result.put("status", status);
        result.put("port", primaryPort);

        log.debug("Final status for app '{}': {} (podCount: {}, pods.size: {})", 
                appName, status, podCount, pods.size());

        return result;
    }

    private double getTotalCpuCapacity(KubernetesClient client) {
        try {
            return client.nodes().list().getItems().stream()
                    .filter(node -> node.getStatus() != null && node.getStatus().getCapacity() != null)
                    .mapToDouble(node -> {
                        try {
                            return parseCpuUsage(node.getStatus().getCapacity().get("cpu").getAmount());
                        } catch (Exception e) {
                            log.warn("Error parsing CPU capacity for node: {}", node.getMetadata().getName(), e);
                            return 0.0;
                        }
                    })
                    .sum();
        } catch (Exception e) {
            log.error("Error getting total CPU capacity", e);
            return 1.0; // 기본값 반환
        }
    }

    private double getTotalMemoryCapacity(KubernetesClient client) {
        try {
            return client.nodes().list().getItems().stream()
                    .filter(node -> node.getStatus() != null && node.getStatus().getCapacity() != null)
                    .mapToDouble(node -> {
                        try {
                            return parseMemoryUsage(node.getStatus().getCapacity().get("memory").getAmount());
                        } catch (Exception e) {
                            log.warn("Error parsing memory capacity for node: {}", node.getMetadata().getName(), e);
                            return 0.0;
                        }
                    })
                    .sum();
        } catch (Exception e) {
            log.error("Error getting total memory capacity", e);
            return 1.0; // 기본값 반환
        }
    }

    private double parseCpuUsage(String cpuUsage) {
        if (cpuUsage == null || cpuUsage.trim().isEmpty()) {
            log.warn("CPU usage string is null or empty");
            return 0.0;
        }
        
        try {
            if (cpuUsage.endsWith("n")) {
                return Double.parseDouble(cpuUsage.substring(0, cpuUsage.length() - 1)) / 1_000_000_000.0;
            } else if (cpuUsage.endsWith("u")) {
                return Double.parseDouble(cpuUsage.substring(0, cpuUsage.length() - 1)) / 1_000_000.0;
            } else if (cpuUsage.endsWith("m")) {
                return Double.parseDouble(cpuUsage.substring(0, cpuUsage.length() - 1)) / 1_000.0;
            } else {
                return Double.parseDouble(cpuUsage);
            }
        } catch (NumberFormatException e) {
            log.warn("Error parsing CPU usage: {}", cpuUsage, e);
            return 0.0;
        }
    }

    private double parseMemoryUsage(String memoryUsage) {
        if (memoryUsage == null || memoryUsage.trim().isEmpty()) {
            log.warn("Memory usage string is null or empty");
            return 0.0;
        }
        
        try {
            if (memoryUsage.endsWith("Ki")) {
                return Double.parseDouble(memoryUsage.substring(0, memoryUsage.length() - 2)) / (1024.0 * 1024.0);
            } else if (memoryUsage.endsWith("Mi")) {
                return Double.parseDouble(memoryUsage.substring(0, memoryUsage.length() - 2)) / 1024.0;
            } else if (memoryUsage.endsWith("Gi")) {
                return Double.parseDouble(memoryUsage.substring(0, memoryUsage.length() - 2));
            } else {
                return Double.parseDouble(memoryUsage) / (1024.0 * 1024.0 * 1024.0);
            }
        } catch (NumberFormatException e) {
            log.warn("Error parsing memory usage: {}", memoryUsage, e);
            return 0.0;
        }
    }

    private double parseNetworkBytes(String networkBytes) {
        if (networkBytes == null || networkBytes.trim().isEmpty()) {
            log.warn("Network bytes string is null or empty");
            return 0.0;
        }
        
        try {
            // 바이트를 MB로 변환
            if (networkBytes.endsWith("Ki")) {
                return Double.parseDouble(networkBytes.substring(0, networkBytes.length() - 2)) / 1024.0;
            } else if (networkBytes.endsWith("Mi")) {
                return Double.parseDouble(networkBytes.substring(0, networkBytes.length() - 2));
            } else if (networkBytes.endsWith("Gi")) {
                return Double.parseDouble(networkBytes.substring(0, networkBytes.length() - 2)) * 1024.0;
            } else {
                // 기본값은 바이트 단위로 간주하고 MB로 변환
                return Double.parseDouble(networkBytes) / (1024.0 * 1024.0);
            }
        } catch (NumberFormatException e) {
            log.warn("Error parsing network bytes: {}", networkBytes, e);
            return 0.0;
        }
    }

    private boolean isMetricsServerInstalled(KubernetesClient client) {
        return client.apps().deployments().inNamespace("kube-system").withName("metrics-server").get() != null;
    }

    private void installMetricsServer(KubernetesClient client) throws IOException {
        log.debug("Metrics Server not found. Installing...");

        String metricsServerYaml = "";
        try {
            metricsServerYaml = downloadMetricsServerYaml();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        client.resourceList(metricsServerYaml).createOrReplace();

        log.debug("Metrics Server installation completed");
        try {
            waitForMetricsServerReady(client);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private String downloadMetricsServerYaml() throws IOException, InterruptedException {
        String url = "https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml";
        Process process = Runtime.getRuntime().exec("curl -s -L " + url);
        process.waitFor();
        return new String(process.getInputStream().readAllBytes());
    }

    private void waitForMetricsServerReady(KubernetesClient client) throws InterruptedException {
        log.debug("Waiting for Metrics Server to be ready...");
        int maxAttempts = 30;
        int attempt = 0;
        while (attempt < maxAttempts) {
            Deployment metricsServer = client.apps().deployments().inNamespace("kube-system").withName("metrics-server")
                    .get();
            if (metricsServer != null && metricsServer.getStatus().getReadyReplicas() != null
                    && metricsServer.getStatus().getReadyReplicas() > 0) {
                log.debug("Metrics Server is ready");
                return;
            }
            Thread.sleep(10000); // 10초 대기
            attempt++;
        }
        log.warn("Metrics Server did not become ready within the expected time");
    }
    
    /**
     * K8S 오토스케일링 처리 (스케일 아웃만)
     */
    private void handleK8sAutoscaling(DeploymentHistory deployment, ApplicationStatus status, KubernetesClient client) {
        try {
            log.debug("=== Starting autoscaling check for deployment: {} ===", deployment.getId());
            
            // K8S 배포가 아니면 스킵
            if (!DeploymentType.K8S.equals(deployment.getDeploymentType())) {
                log.debug("Skipping autoscaling: not a K8S deployment");
                return;
            }
            
            // 리소스 사용률이 null이면 스킵
            if (status.getCpuUsage() == null || status.getMemoryUsage() == null) {
                log.warn("Skipping autoscaling: CPU or Memory usage is null - CPU: {}, Memory: {}", 
                        status.getCpuUsage(), status.getMemoryUsage());
                return;
            }
            
            log.debug("Resource usage - CPU: {}%, Memory: {}%", status.getCpuUsage(), status.getMemoryUsage());
            
            String namespace = deployment.getNamespace();
            String clusterName = deployment.getClusterName();
            
            // 노드 그룹 이름 동적 조회
            String nodeGroupName = getNodeGroupName(deployment);
            
            // 노드 그룹 이름이 없으면 오토스케일링 불가
            if (nodeGroupName == null || nodeGroupName.isEmpty()) {
                log.warn("Cannot perform autoscaling: node group name is null or empty for deployment: {}", deployment.getId());
                
                // 테스트 모드에서는 기본 노드 그룹 이름 사용
                if (testMode) {
                    log.info("TEST MODE: Using default node group name for testing");
                    // 실제 node group 이름: k8sng01 (JSON에서 확인한 이름)
                    nodeGroupName = "k8sng01"; // TODO: 실제 node group 이름으로 수정 필요
                } else {
                    return;
                }
            }
            
            // 이미 진행 중인 스케일링 이벤트가 있는지 확인
            List<ScalingEvent> pendingEvents = scalingEventRepository.findPendingScalingEvents(deployment.getId());
            if (!pendingEvents.isEmpty()) {
                // 진행 중인 스케일링 이벤트가 있으면, 노드가 생성되었는지 확인하고 재배포
                ScalingEvent latestPendingEvent = pendingEvents.get(0);
                int targetNodeCount = latestPendingEvent.getNewNodeCount();
                int actualNodeCount = getCurrentNodeCount(deployment, client);
                
                log.info("Scaling event in progress: target={}, actual={}", targetNodeCount, actualNodeCount);
                
                // 노드가 목표 노드 수에 도달했으면 재배포
                if (actualNodeCount >= targetNodeCount) {
                    log.info("Target nodes created ({}/{}). Proceeding with redeployment.", actualNodeCount, targetNodeCount);
                    
                    // nodeSelector가 이미 설정되어 있는지 확인 (재배포 중복 방지)
                    if (isAlreadyScaledOut(deployment, client)) {
                        log.info("Already redeployed. Marking event as completed.");
                        latestPendingEvent.setStatus(ScalingEvent.ScalingStatus.COMPLETED);
                        latestPendingEvent.setCompletedAt(LocalDateTime.now());
                        scalingEventRepository.save(latestPendingEvent);
                        return;
                    }
                    
                    try {
                        redeployToNewNodes(deployment, targetNodeCount, client);
                        latestPendingEvent.setStatus(ScalingEvent.ScalingStatus.COMPLETED);
                        latestPendingEvent.setCompletedAt(LocalDateTime.now());
                        scalingEventRepository.save(latestPendingEvent);
                        log.info("Redeployment completed for event: {}", latestPendingEvent.getId());
                        
                        // 스케일 아웃 완료 알람 전송
                        sendScaleOutCompletedAlert(deployment, targetNodeCount);
                    } catch (Exception e) {
                        log.error("Error redeploying to new nodes: {}", e.getMessage(), e);
                    }
                } else {
                    log.info("Waiting for nodes to be created ({}/{})...", actualNodeCount, targetNodeCount);
                }
                return; // 진행 중인 이벤트가 있으면 새 스케일링 요청하지 않음
            }
            
            // 현재 실제 노드 수 확인
            int actualNodeCount = getCurrentNodeCount(deployment, client);
            log.info("Actual node count in cluster: {}", actualNodeCount);
            
            // 현재 노드 수를 실제 노드 수로 설정
            int currentSize = actualNodeCount;
            log.info("Current node count: {}", currentSize);
            
            // 목표 노드 수는 currentSize + 1
            int targetSize = currentSize + 1;
            log.info("Target node count: {}", targetSize);
            
            int maxSize = deployment.getCatalog().getMaxReplicas() != null ? deployment.getCatalog().getMaxReplicas() : defaultMaxNodes;
            
            // 이미 스케일 아웃이 완료되었고 nodeSelector가 설정되어 있는지 확인
            if (isAlreadyScaledOut(deployment, client)) {
                // 실제 노드 수가 목표 노드 수보다 적으면 nodeSelector 제거하고 계속 진행
                if (actualNodeCount < targetSize) {
                    log.info("Actual node count ({}) < target ({}). Removing nodeSelector to allow new scaling.", 
                            actualNodeCount, targetSize);
                    removeNodeSelectorFromDeployment(deployment, client);
                    // nodeSelector 제거 후 계속 진행하여 스케일 아웃 수행
                } else {
                    log.info("Already scaled out with nodeSelector. Skipping redeployment for deployment: {}", deployment.getId());
                    return;
                }
            }
            
            // CPU 또는 메모리 임계값 초과 시 스케일 아웃 (단, 실제 노드 수가 목표 노드 수보다 적을 때만)
            boolean cpuExceeded = false;
            boolean memoryExceeded = false;
            
            if (testMode) {
                // 테스트 모드: 낮은 임계값 사용
                cpuExceeded = status.getCpuUsage() > testCpuThreshold;
                memoryExceeded = status.getMemoryUsage() > testMemoryThreshold;
                log.debug("TEST MODE - Checking scaling trigger - CPU: {}% (threshold: {}%), Memory: {}% (threshold: {}%)", 
                        status.getCpuUsage(), testCpuThreshold, status.getMemoryUsage(), testMemoryThreshold);
            } else {
                // 운영 모드: 실제 임계값 사용
                cpuExceeded = deployment.getCatalog().getCpuThreshold() != null && 
                            status.getCpuUsage() > deployment.getCatalog().getCpuThreshold();
                memoryExceeded = deployment.getCatalog().getMemoryThreshold() != null && 
                               status.getMemoryUsage() > deployment.getCatalog().getMemoryThreshold();
                log.debug("Production mode - CPU: {}% (threshold: {}), Memory: {}% (threshold: {})", 
                        status.getCpuUsage(), deployment.getCatalog().getCpuThreshold(), 
                        status.getMemoryUsage(), deployment.getCatalog().getMemoryThreshold());
            }
            
            // 스케일 아웃 조건: 부하 초과 AND 목표 노드 수 < 최대 노드 수
            if ((cpuExceeded || memoryExceeded) && targetSize <= maxSize) {
                log.info("Resource threshold exceeded for K8S deployment: CPU={}%, Memory={}%, scaling out from {} to {}", 
                        status.getCpuUsage(), status.getMemoryUsage(), currentSize, targetSize);
                
                // 스케일링 이벤트 생성
                ScalingEvent scalingEvent = createScalingEvent(deployment, nodeGroupName, currentSize, targetSize);
                if (scalingEvent != null) {
                    scalingEventRepository.save(scalingEvent);
                } else {
                    log.error("Failed to create scaling event: node group name is null");
                    return;
                }
                
                try {
                    // 현재 노드 그룹의 desiredNodeSize 확인
                    int currentDesiredNodeSize = getCurrentDesiredNodeSize(deployment, client);
                    
                    log.debug("Current desired node size: {}, target: {}", currentDesiredNodeSize, targetSize);
                    
                    // 로직 1: desiredNodeSize < maxNodeSize인 경우, API 호출하지 않고 기다림
                    if (currentDesiredNodeSize < maxSize) {
                        log.debug("desiredNodeSize ({}) < maxNodeSize ({}). Waiting for desired size to reach max.", 
                                currentDesiredNodeSize, maxSize);
                        scalingEvent.setStatus(ScalingEvent.ScalingStatus.IN_PROGRESS);
                        scalingEventRepository.save(scalingEvent);
                        log.info("Will check for node creation in next cycle when desiredNodeSize reaches max.");
                        return;
                    }
                    
                    // 로직 2: desiredNodeSize == maxNodeSize인 경우, max+1로 API 호출
                    if (currentDesiredNodeSize >= maxSize) {
                        log.debug("desiredNodeSize ({}) >= maxNodeSize ({}). Scaling out to max+1", 
                                currentDesiredNodeSize, maxSize);
                        
                        int newMaxSize = currentDesiredNodeSize + 1;
                        targetSize = newMaxSize;
                        scalingEvent.setNewNodeCount(targetSize);
                        
                        // API 호출
                        boolean scaleResult = k8sAutoscaleService.scaleOutNodeGroup("default", clusterName, nodeGroupName, currentDesiredNodeSize, newMaxSize);
                        
                        if (scaleResult) {
                            log.debug("Scale out API returned true: {} -> {}", currentDesiredNodeSize, targetSize);
                            scalingEvent.setStatus(ScalingEvent.ScalingStatus.IN_PROGRESS);
                            scalingEventRepository.save(scalingEvent);
                            
                            log.debug("Scale out request completed. Will check for new nodes in next monitoring cycle.");
                        } else {
                            log.error("Failed to scale out K8S node group via API");
                            scalingEvent.setStatus(ScalingEvent.ScalingStatus.FAILED);
                            scalingEvent.setErrorMessage("Failed to initiate scale out via API");
                            scalingEventRepository.save(scalingEvent);
                        }
                    }
                } catch (Exception e) {
                    log.error("Error during scale out: {}", e.getMessage(), e);
                    scalingEvent.setStatus(ScalingEvent.ScalingStatus.FAILED);
                    scalingEvent.setErrorMessage("Error: " + e.getMessage());
                    scalingEventRepository.save(scalingEvent);
                }
                
            } else if (targetSize > maxSize) {
                log.warn("Cannot scale out: target size {} > max size {} (CPU={}%, Memory={}%)", 
                        targetSize, maxSize, status.getCpuUsage(), status.getMemoryUsage());
            } else {
                log.debug("No scaling needed: CPU={}%, Memory={}%, currentSize={}, targetSize={}, maxSize={}", 
                        status.getCpuUsage(), status.getMemoryUsage(), currentSize, targetSize, maxSize);
            }
            
        } catch (Exception e) {
            log.error("Error handling K8S autoscaling for deployment: {}", deployment.getId(), e);
        }
    }
    
    /**
     * 현재 노드 그룹의 desiredNodeSize 가져오기
     */
    private int getCurrentDesiredNodeSize(DeploymentHistory deployment, KubernetesClient client) {
        try {
            // Tumblebug API를 통해 노드 그룹 정보 가져오기
            K8sClusterDto clusterInfo = cbtumblebugRestApi.getK8sClusterByName(
                    deployment.getNamespace(), deployment.getClusterName());
            
            if (clusterInfo != null && clusterInfo.getCspViewK8sClusterDetail() != null) {
                List<K8sClusterDto.NodeGroup> nodeGroupList = clusterInfo.getCspViewK8sClusterDetail().getNodeGroupList();
                
                if (nodeGroupList != null && deployment.getNodeGroupName() != null) {
                    for (K8sClusterDto.NodeGroup nodeGroup : nodeGroupList) {
                        if (nodeGroup.getIid() != null && 
                            nodeGroup.getIid().getNameId() != null &&
                            deployment.getNodeGroupName().equals(nodeGroup.getIid().getNameId())) {
                            log.info("Current desired node size from cluster info: {}", nodeGroup.getDesiredNodeSize());
                            return nodeGroup.getDesiredNodeSize();
                        }
                    }
                }
            }
            
            // 기본값: 현재 노드 수
            return getCurrentNodeCount(deployment, client);
            
        } catch (Exception e) {
            log.error("Error getting current desired node size: {}", e.getMessage(), e);
            return getCurrentNodeCount(deployment, client);
        }
    }

    /**
     * 스케일 아웃 완료 알람 전송
     */
    private void sendScaleOutCompletedAlert(DeploymentHistory deployment, int newNodeCount) {
        try {
            String appName = deployment.getCatalog().getName();
            String namespace = deployment.getNamespace();
            String clusterName = deployment.getClusterName();
            
            String title = String.format("[K8s AutoScaling] Scale Out Completed: %s", appName);
            String message = String.format(
                    "Application '%s' has been successfully scaled out to %d nodes in cluster '%s' (namespace: %s).",
                    appName, newNodeCount, clusterName, namespace
            );
            
            // Slack 채널로 알람 전송 (환경변수에서 채널 ID 가져오기)
            String recipients = System.getenv("ALERT_SLACK_CHANNEL_ID");
            if (recipients == null) {
                recipients = "#kubernetes-alerts"; // 기본값
            }
            
            boolean sent = rabbitMqAlertService.sendScaleOutAlert(title, message, "slack", recipients);
            
            if (sent) {
                log.info("Scale out alert sent successfully for {} to {} nodes", appName, newNodeCount);
            } else {
                log.warn("Failed to send scale out alert for {}", appName);
            }
            
        } catch (Exception e) {
            log.error("Error sending scale out alert: {}", e.getMessage(), e);
        }
    }

    /**
     * Deployment에서 nodeSelector 제거
     */
    private void removeNodeSelectorFromDeployment(DeploymentHistory deployment, KubernetesClient client) {
        try {
            String namespace = "default";
            String appName = deployment.getCatalog().getName().toLowerCase();
            
            log.info("Looking for Deployment in namespace: {}, appName: {}", namespace, appName);
            
            // Deployment 찾기
            io.fabric8.kubernetes.api.model.apps.Deployment k8sDeployment = client.apps().deployments()
                    .inNamespace(namespace)
                    .withName(appName)
                    .get();
            
            if (k8sDeployment == null) {
                log.warn("Deployment '{}' not found, searching for matching deployment", appName);
                List<io.fabric8.kubernetes.api.model.apps.Deployment> deployments = client.apps().deployments()
                        .inNamespace(namespace)
                        .list()
                        .getItems();
                
                for (io.fabric8.kubernetes.api.model.apps.Deployment dep : deployments) {
                    if (dep.getMetadata().getName().contains(appName)) {
                        k8sDeployment = dep;
                        log.info("Found deployment in namespace {}: {}", namespace, dep.getMetadata().getName());
                        break;
                    }
                }
            }
            
            if (k8sDeployment == null) {
                log.error("Deployment not found for app: {} in namespace: {}", appName, namespace);
                return;
            }
            
            log.info("Using deployment: {}", k8sDeployment.getMetadata().getName());
            
            // nodeSelector 제거
            PodSpec podSpec = k8sDeployment.getSpec().getTemplate().getSpec();
            if (podSpec.getNodeSelector() != null) {
                podSpec.getNodeSelector().remove("autoscale-group");
                log.info("Removed autoscale-group nodeSelector from deployment");
            }
            
            // Rolling Update 트리거를 위한 annotation 추가
            String timestamp = String.valueOf(System.currentTimeMillis());
            Map<String, String> annotations = k8sDeployment.getSpec().getTemplate().getMetadata().getAnnotations();
            if (annotations == null) {
                annotations = new HashMap<>();
                k8sDeployment.getSpec().getTemplate().getMetadata().setAnnotations(annotations);
            }
            annotations.put("rollout.trigger.timestamp", timestamp);
            
            // Deployment 업데이트
            client.apps().deployments()
                    .inNamespace(namespace)
                    .createOrReplace(k8sDeployment);
            
            log.info("Successfully removed nodeSelector from deployment - Rolling Update triggered at timestamp: {}", timestamp);
            log.info("Pods will be rescheduled without nodeSelector constraints");
            
        } catch (Exception e) {
            log.error("Failed to remove nodeSelector from deployment: {}", deployment.getId(), e);
        }
    }
    
    /**
     * 이미 스케일 아웃되어 nodeSelector가 설정되어 있는지 확인
     */
    private boolean isAlreadyScaledOut(DeploymentHistory deployment, KubernetesClient client) {
        try {
            String namespace = "default";
            String appName = deployment.getCatalog().getName().toLowerCase().replaceAll("\\s+", "-");
            
            // Deployment 찾기
            Deployment k8sDeployment = client.apps().deployments()
                    .inNamespace(namespace)
                    .withName(appName)
                    .get();
            
            if (k8sDeployment == null) {
                // 전체 검색
                List<Deployment> allDeployments = 
                        client.apps().deployments().inNamespace(namespace).list().getItems();
                for (Deployment d : allDeployments) {
                    if (d.getMetadata().getName().startsWith(appName) || 
                        d.getMetadata().getName().contains(appName)) {
                        k8sDeployment = d;
                        break;
                    }
                }
            }
            
            if (k8sDeployment != null && k8sDeployment.getSpec().getTemplate().getSpec() != null) {
                PodSpec podSpec = k8sDeployment.getSpec().getTemplate().getSpec();
                if (podSpec.getNodeSelector() != null && 
                    podSpec.getNodeSelector().containsKey("autoscale-group")) {
                    log.info("Deployment already has nodeSelector: {}", podSpec.getNodeSelector());
                    return true;
                } else {
                    log.info("Deployment does not have autoscale-group nodeSelector. NodeSelector: {}", podSpec.getNodeSelector());
                }
            } else {
                log.info("Could not get deployment spec or pod spec");
            }
            return false;
        } catch (Exception e) {
            log.error("Error checking if already scaled out: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 스케일 아웃 완료 감지 및 재배포 처리
     */
    private void checkAndHandleScalingCompletion(DeploymentHistory deployment, KubernetesClient client) {
        try {
            List<ScalingEvent> pendingEvents = scalingEventRepository.findPendingScalingEvents(deployment.getId());
            
            for (ScalingEvent event : pendingEvents) {
                if (isScalingCompleted(event, client)) {
                    log.info("Scaling completed for deployment: {}, event: {}", deployment.getId(), event.getId());
                    
                    // 스케일링 이벤트 상태를 완료로 업데이트
                    event.setStatus(ScalingEvent.ScalingStatus.COMPLETED);
                    event.setCompletedAt(LocalDateTime.now());
                    scalingEventRepository.save(event);
                    
                    // 새로운 노드에 애플리케이션 재배포
                    redeployToNewNodes(deployment, event.getNewNodeCount(), client);
                }
            }
            
        } catch (Exception e) {
            log.error("Error checking scaling completion for deployment: {}", deployment.getId(), e);
        }
    }
    
    /**
     * 스케일링 완료 여부 확인
     * Rate limit 방지를 위해 타임아웃 기반만 사용
     */
    private boolean isScalingCompleted(ScalingEvent event, KubernetesClient client) {
        try {
            // 타임아웃 체크 (5분) - API 호출 없이 타임아웃으로만 판단
            long secondsSinceTriggered = java.time.Duration.between(event.getTriggeredAt(), LocalDateTime.now()).getSeconds();
            
            if (secondsSinceTriggered > scalingTimeoutSeconds) { 
                log.info("Scaling completed by timeout after {} seconds: target={}", 
                        scalingTimeoutSeconds, event.getNewNodeCount());
                return true; // 타임아웃 시 완료로 간주
            }
            
            log.debug("Scaling in progress: target={}, elapsed={}s", 
                    event.getNewNodeCount(), secondsSinceTriggered);
            return false;
            
        } catch (Exception e) {
            // Rate limit 에러는 무시하고 다음에 재시도
            if (e.getMessage() != null && e.getMessage().contains("rate limit")) {
                log.debug("Rate limit error during scaling check, will retry later: {}", event.getId());
                return false;
            }
            log.error("Error checking scaling completion for event: {}", event.getId(), e);
            return false;
        }
    }
    
    /**
     * 스케일링 이벤트 생성
     */
    private ScalingEvent createScalingEvent(DeploymentHistory deployment, String nodeGroupName, int oldCount, int newCount) {
        if (nodeGroupName == null || nodeGroupName.isEmpty()) {
            log.error("Cannot create scaling event: node group name is null or empty");
            return null;
        }
        
        return ScalingEvent.builder()
                .deploymentId(deployment.getId())
                .namespace(deployment.getNamespace())
                .clusterName(deployment.getClusterName())
                .nodeGroupName(nodeGroupName)
                .oldNodeCount(oldCount)
                .newNodeCount(newCount)
                .scalingType(ScalingEvent.ScalingType.SCALE_OUT)
                .status(ScalingEvent.ScalingStatus.PENDING)
                .triggeredAt(LocalDateTime.now())
                .build();
    }
    
    /**
     * 새로운 노드에 애플리케이션 재배포
     * 의도한 방식:
     * 1. 새 노드에 라벨 추가
     * 2. Deployment에 nodeSelector 추가
     * 3. 새 노드에만 Pod 배포
     */
    private void redeployToNewNodes(DeploymentHistory deployment, int newNodeCount, KubernetesClient client) {
        try {
            log.info("Redeploying application to new nodes: deploymentId={}, newNodeCount={}", 
                    deployment.getId(), newNodeCount);
            
            // K8s 배포는 항상 default namespace에 배포됨
            String namespace = "default";
            String appName = deployment.getCatalog().getName().toLowerCase().replaceAll("\\s+", "-");
            String nodeGroupName = deployment.getNodeGroupName();
            
            // 1. 기존 노드에서 라벨 제거하고 새 노드에만 라벨 추가
            String labelKey = "autoscale-group";
            String labelValue = nodeGroupName != null ? nodeGroupName : "scaled";
            
            log.info("Step 1: Removing label from existing nodes and adding label to new nodes: {}={}", labelKey, labelValue);
            
            // 기존 노드에서 라벨 제거
            removeLabelFromOldNodes(client, labelKey);
            
            // 새 노드에 라벨 추가 (항상 1개의 최근 생성된 노드에만)
            labelNewNodes(client, labelKey, labelValue, 1);
            
            // 2. 기존 Deployment 가져오기
            log.info("Looking for Deployment in namespace: {}, appName: {}", namespace, appName);
            
            // 먼저 정확한 이름으로 찾기
            Deployment k8sDeployment = client.apps().deployments()
                    .inNamespace(namespace)
                    .withName(appName)
                    .get();
            
            // 없으면 모든 Deployment를 나열해서 매칭
            if (k8sDeployment == null) {
                log.warn("Deployment '{}' not found, searching for matching deployment", appName);
                List<Deployment> allDeployments = 
                        client.apps().deployments().inNamespace(namespace).list().getItems();
                
                for (Deployment d : allDeployments) {
                    log.info("Found deployment in namespace {}: {}", namespace, d.getMetadata().getName());
                    // appName으로 시작하거나 포함하는 Deployment 찾기
                    if (d.getMetadata().getName().startsWith(appName) || 
                        d.getMetadata().getName().contains(appName)) {
                        k8sDeployment = d;
                        log.info("Using deployment: {}", d.getMetadata().getName());
                        break;
                    }
                }
            }
            
            if (k8sDeployment != null) {
                // 2. nodeSelector 추가 - 새 노드에만 Pod 배포
                log.info("Step 2: Adding nodeSelector to Deployment: {}={}", labelKey, labelValue);
                
                // Pod template spec 가져오기
                if (k8sDeployment.getSpec().getTemplate().getSpec() == null) {
                    log.error("Pod spec is null in deployment: {}", appName);
                    return;
                }
                
                PodSpec podSpec = k8sDeployment.getSpec().getTemplate().getSpec();
                
                // nodeSelector 생성 또는 업데이트
                if (podSpec.getNodeSelector() == null) {
                    podSpec.setNodeSelector(new HashMap<>());
                }
                podSpec.getNodeSelector().put(labelKey, labelValue);
                
                // 3. Rolling Update 트리거를 위한 annotation 추가
                String timestamp = String.valueOf(System.currentTimeMillis());
                Map<String, String> annotations = k8sDeployment.getSpec().getTemplate().getMetadata().getAnnotations();
                if (annotations == null) {
                    annotations = new HashMap<>();
                    k8sDeployment.getSpec().getTemplate().getMetadata().setAnnotations(annotations);
                }
                annotations.put("rollout.trigger.timestamp", timestamp);
                
                // Deployment 업데이트 (replicas는 변경하지 않음)
                client.apps().deployments()
                        .inNamespace(namespace)
                        .createOrReplace(k8sDeployment);
                
                log.info("Successfully updated deployment - nodeSelector: {}={}", labelKey, labelValue);
                log.info("Rolling Update triggered at timestamp: {}", timestamp);
                
                // 4. 기존 Pod 삭제하여 즉시 새 노드로 이동
                log.info("Step 4: Deleting existing pods to force migration to new nodes");
                client.pods()
                        .inNamespace(namespace)
                        .withLabel("app.kubernetes.io/instance", k8sDeployment.getMetadata().getName())
                        .delete();
                log.info("Deleted existing pods. New pods will be created on nodes with label {}={}", labelKey, labelValue);
                
                // 새로 생성된 노드에 대한 추가 정보 로깅
                logNewlyCreatedNodesInfo(deployment, newNodeCount);
                
            } else {
                log.warn("Deployment not found for app: {} in namespace: {}", appName, namespace);
            }
            
        } catch (Exception e) {
            log.error("Error redeploying application to new nodes", e);
        }
    }
    
    /**
     * 기존 노드에서 라벨 제거
     */
    private void removeLabelFromOldNodes(KubernetesClient client, String labelKey) {
        try {
            List<Node> allNodes = client.nodes().list().getItems();
            
            for (Node node : allNodes) {
                Map<String, String> labels = node.getMetadata().getLabels();
                if (labels != null && labels.containsKey(labelKey)) {
                    String nodeName = node.getMetadata().getName();
                    labels.remove(labelKey);
                    client.nodes().createOrReplace(node);
                    log.info("Removed label {} from old node: {}", labelKey, nodeName);
                }
            }
        } catch (Exception e) {
            log.error("Error removing label from old nodes: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 새로 생성된 노드에 라벨 추가
     * 생성 시간을 기준으로 최근 생성된 노드에만 라벨 추가
     * targetNodeCount는 현재 단계에서 추가할 노드 수 (항상 1개)
     */
    private void labelNewNodes(KubernetesClient client, String labelKey, String labelValue, int targetNodeCount) {
        try {
            // 모든 노드 목록 가져오기
            List<Node> allNodes = client.nodes().list().getItems();
            
            if (allNodes.isEmpty()) {
                log.warn("No nodes found in cluster");
                return;
            }
            
            log.info("Total nodes in cluster: {}", allNodes.size());
            
            // 노드를 생성 시간으로 정렬 (최근 것부터)
            List<Node> sortedNodes = new ArrayList<>(allNodes);
            sortedNodes.sort((a, b) -> {
                try {
                    String timestampA = a.getMetadata().getCreationTimestamp();
                    String timestampB = b.getMetadata().getCreationTimestamp();
                    if (timestampA == null && timestampB == null) return 0;
                    if (timestampA == null) return 1;
                    if (timestampB == null) return -1;
                    return timestampB.compareTo(timestampA); // 최근 것부터
                } catch (Exception e) {
                    return 0;
                }
            });
            
            int labeledCount = 0;
            for (Node node : sortedNodes) {
                String nodeName = node.getMetadata().getName();
                Map<String, String> labels = node.getMetadata().getLabels();
                String creationTime = node.getMetadata().getCreationTimestamp();
                
                log.info("Checking node: {} (created: {}), current labels: {}", nodeName, creationTime, labels);
                
                // 이미 원하는 라벨이 있는 노드는 스킵
                if (labels != null && labelValue.equals(labels.get(labelKey))) {
                    log.info("Node {} already has label {}={}, skipping", nodeName, labelKey, labelValue);
                    continue;
                }
                
                // 라벨 추가
                if (labels == null) {
                    labels = new HashMap<>();
                    node.getMetadata().setLabels(labels);
                }
                
                labels.put(labelKey, labelValue);
                
                // 노드 업데이트
                client.nodes().createOrReplace(node);
                log.info("Added label {}={} on node: {} (created: {})", labelKey, labelValue, nodeName, creationTime);
                labeledCount++;
                
                // 항상 1개의 노드에만 라벨 추가 (가장 최근에 생성된 노드)
                if (labeledCount >= 1) {
                    log.info("Reached target label count: 1 (most recently created node)");
                    break;
                }
            }
            
            if (labeledCount == 0) {
                log.info("No nodes needed labeling. All {} nodes already have the label {}={}", allNodes.size(), labelKey, labelValue);
            } else {
                log.info("Successfully labeled {} nodes with {}={}", labeledCount, labelKey, labelValue);
            }
            
        } catch (Exception e) {
            log.error("Error labeling new nodes: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 새로 생성된 노드 정보 로깅
     */
    private void logNewlyCreatedNodesInfo(DeploymentHistory deployment, int newNodeCount) {
        try {
            // 스케일링 이벤트 조회
            List<ScalingEvent> recentEvents = scalingEventRepository.findPendingScalingEvents(deployment.getId());
            
            if (!recentEvents.isEmpty()) {
                ScalingEvent latestEvent = recentEvents.get(0);
                
                // 새로 생성된 노드 목록 조회
                List<String> newlyCreatedNodes = getNewlyCreatedNodes(latestEvent, null);
                
                if (!newlyCreatedNodes.isEmpty()) {
                    log.info("Newly created nodes for deployment {}: {}", 
                            deployment.getId(), newlyCreatedNodes);
                    
                    // 각 새 노드에 대한 상세 정보 로깅
                    for (String nodeName : newlyCreatedNodes) {
                        log.info("New node {} is ready for application deployment", nodeName);
                    }
                } else {
                    log.warn("No newly created nodes identified for deployment {}", deployment.getId());
                }
            }
            
        } catch (Exception e) {
            log.error("Error logging newly created nodes info", e);
        }
    }
    
    /**
     * 노드 그룹 이름을 동적으로 조회합니다.
     */
    private String getNodeGroupName(DeploymentHistory deployment) {
        try {
            // DeploymentHistory에 노드 그룹 이름이 저장되어 있으면 사용
            if (deployment.getNodeGroupName() != null && !deployment.getNodeGroupName().isEmpty()) {
                return deployment.getNodeGroupName();
            }
            
            // Tumblebug API에서 클러스터 정보 조회
            K8sClusterDto clusterInfo = cbtumblebugRestApi.getK8sClusterByName(
                    deployment.getNamespace(), 
                    deployment.getClusterName()
            );
            
            if (clusterInfo == null) {
                log.error("Cluster info is null for namespace: {}, clusterName: {}", 
                        deployment.getNamespace(), deployment.getClusterName());
                return null;
            }
            
            // 클러스터 정보 상세 로깅
            log.info("Cluster info retrieved: name={}, id={}", clusterInfo.getName(), clusterInfo.getId());
            log.info("CspViewK8sClusterDetail is null: {}", clusterInfo.getCspViewK8sClusterDetail() == null);
            log.info("SpiderViewK8sClusterDetail is null: {}", clusterInfo.getSpiderViewK8sClusterDetail() == null);
            
            // 전체 클러스터 정보 로깅 (디버깅용)
            if (clusterInfo.getSpiderViewK8sClusterDetail() != null) {
                log.info("SpiderViewK8sClusterDetail - NodeGroupList size: {}", 
                        clusterInfo.getSpiderViewK8sClusterDetail().getNodeGroupList() != null 
                        ? clusterInfo.getSpiderViewK8sClusterDetail().getNodeGroupList().size() : 0);
            }
            if (clusterInfo.getCspViewK8sClusterDetail() != null) {
                log.info("CspViewK8sClusterDetail - NodeGroupList size: {}", 
                        clusterInfo.getCspViewK8sClusterDetail().getNodeGroupList() != null 
                        ? clusterInfo.getCspViewK8sClusterDetail().getNodeGroupList().size() : 0);
            }
            
            // SpiderViewK8sClusterDetail 또는 CspViewK8sClusterDetail에서 노드 그룹 정보 가져오기
            List<K8sClusterDto.NodeGroup> nodeGroups = null;
            
            if (clusterInfo.getSpiderViewK8sClusterDetail() != null) {
                nodeGroups = clusterInfo.getSpiderViewK8sClusterDetail().getNodeGroupList();
                log.info("Using SpiderViewK8sClusterDetail for node groups, found {} groups", 
                        nodeGroups != null ? nodeGroups.size() : 0);
            } else if (clusterInfo.getCspViewK8sClusterDetail() != null) {
                nodeGroups = clusterInfo.getCspViewK8sClusterDetail().getNodeGroupList();
                log.info("Using CspViewK8sClusterDetail for node groups, found {} groups", 
                        nodeGroups != null ? nodeGroups.size() : 0);
            }
            
            if (nodeGroups != null && !nodeGroups.isEmpty()) {
                log.info("Found {} node groups in cluster:", nodeGroups.size());
                for (K8sClusterDto.NodeGroup ng : nodeGroups) {
                    log.info("  - NodeGroup: {}, AutoScaling: {}", 
                            ng.getIid().getNameId(), ng.isOnAutoScaling());
                }
                
                // 적절한 노드 그룹 선택 (예: 오토스케일링이 활성화된 그룹)
                K8sClusterDto.NodeGroup selectedNodeGroup = selectAppropriateNodeGroup(nodeGroups);
                
                if (selectedNodeGroup != null) {
                    String nodeGroupName = selectedNodeGroup.getIid().getNameId();
                    
                    // 조회된 노드 그룹 이름을 DeploymentHistory에 저장
                    deployment.setNodeGroupName(nodeGroupName);
                    historyRepository.save(deployment);
                    
                    log.info("Selected node group: {} for cluster: {}", nodeGroupName, deployment.getClusterName());
                    return nodeGroupName;
                } else {
                    log.error("No appropriate node group found for cluster: {}", deployment.getClusterName());
                    return null;
                }
            } else {
                log.warn("No node groups found for cluster: {} (NodeGroupList is null or empty)", deployment.getClusterName());
                log.warn("Please create a node group first before deploying applications");
                return null;
            }
            
        } catch (Exception e) {
            log.error("Error getting node group name for deployment: {}", deployment.getId(), e);
            return null;
        }
    }
    
    /**
     * 적절한 노드 그룹을 선택합니다.
     */
    private K8sClusterDto.NodeGroup selectAppropriateNodeGroup(List<K8sClusterDto.NodeGroup> nodeGroups) {
        // 1. 오토스케일링이 활성화된 노드 그룹 우선 선택
        for (K8sClusterDto.NodeGroup nodeGroup : nodeGroups) {
            if (nodeGroup.isOnAutoScaling()) {
                log.info("Selected auto-scaling enabled node group: {}", nodeGroup.getIid().getNameId());
                return nodeGroup;
            }
        }
        
        // 2. 오토스케일링이 활성화된 그룹이 없으면 첫 번째 그룹 선택
        if (!nodeGroups.isEmpty()) {
            log.info("No auto-scaling enabled node group found, using first group: {}", 
                    nodeGroups.get(0).getIid().getNameId());
            return nodeGroups.get(0);
        }
        
        return null;
    }
    
    /**
     * 현재 노드 수 조회 (DeploymentHistory 기반)
     */
    private int getCurrentNodeCount(DeploymentHistory deployment, KubernetesClient client) {
        try {
            return getCurrentNodeCountFromCluster(deployment.getNamespace(), deployment.getClusterName(), 
                    deployment.getNodeGroupName(), client);
        } catch (Exception e) {
            log.error("Error getting current node count for deployment: {}", deployment.getId(), e);
            return defaultNodeCount; // 설정 가능한 기본값
        }
    }
    
    /**
     * 클러스터에서 실제 노드 수 조회
     */
    private int getCurrentNodeCountFromCluster(String namespace, String clusterName, String nodeGroupName, KubernetesClient client) {
        try {
            // Tumblebug API를 통해 클러스터 정보 조회
            log.info("Fetching cluster info for namespace: {}, clusterName: {}", namespace, clusterName);
            K8sClusterDto clusterInfo = cbtumblebugRestApi.getK8sClusterByName(namespace, clusterName);
            
            if (clusterInfo == null) {
                log.error("Cluster info is null for namespace: {}, clusterName: {}", namespace, clusterName);
                log.warn("Will use default node count: {}", defaultNodeCount);
                return defaultNodeCount;
            }
            
            log.info("Cluster info retrieved successfully: {}", clusterInfo.getName());
            
            if (clusterInfo.getCspViewK8sClusterDetail() == null) {
                log.error("CspViewK8sClusterDetail is null for cluster: {}", clusterName);
                log.warn("Will use default node count: {}", defaultNodeCount);
                return defaultNodeCount;
            }
            
            log.info("CspViewK8sClusterDetail retrieved successfully");
            
            List<K8sClusterDto.NodeGroup> nodeGroups = clusterInfo.getCspViewK8sClusterDetail().getNodeGroupList();
            
            log.info("Node groups found: {}", nodeGroups != null ? nodeGroups.size() : 0);
            
            if (nodeGroups != null) {
                for (K8sClusterDto.NodeGroup nodeGroup : nodeGroups) {
                    String groupName = nodeGroup.getIid().getNameId();
                    log.debug("Checking node group: {}", groupName);
                    if (nodeGroupName.equals(groupName)) {
                        // 노드 리스트에서 실제 노드 수 확인
                        List<K8sClusterDto.IID> nodes = nodeGroup.getNodes();
                        if (nodes != null) {
                            int nodeCount = nodes.size();
                            log.debug("Current node count for group {}: {}", nodeGroupName, nodeCount);
                            return nodeCount;
                        }
                    }
                }
            }
            
            log.warn("Could not determine current node count for group: {}, using default value", nodeGroupName);
            return defaultNodeCount; // 설정 가능한 기본값
            
        } catch (Exception e) {
            // Rate limit 에러는 자동으로 재시도되므로 조용히 처리
            if (e.getMessage() != null && e.getMessage().contains("rate limit")) {
                log.debug("Rate limit during node count check, using default: {}", defaultNodeCount);
                return defaultNodeCount;
            }
            log.error("Error getting current node count from cluster", e);
            return defaultNodeCount; // 설정 가능한 기본값
        }
    }
    
    /**
     * 테스트용: 클러스터 정보 상세 조회 (디버깅용)
     */
    public K8sClusterDto getClusterInfoForTesting(String namespace, String clusterName) {
        try {
            log.info("Testing cluster info API: namespace={}, clusterName={}", namespace, clusterName);
            
            // 기존 API 사용
            return cbtumblebugRestApi.getK8sClusterByName(namespace, clusterName);
            
        } catch (Exception e) {
            log.error("Error getting cluster info for testing", e);
            return null;
        }
    }
    
    /**
     * 새로 생성된 노드 목록 조회 (스케일링 이벤트 기반)
     */
    public List<String> getNewlyCreatedNodes(ScalingEvent event, KubernetesClient client) {
        try {
            K8sClusterDto clusterInfo = cbtumblebugRestApi.getK8sClusterByName(
                    event.getNamespace(), event.getClusterName());
            
            if (clusterInfo != null && clusterInfo.getCspViewK8sClusterDetail() != null) {
                List<K8sClusterDto.NodeGroup> nodeGroups = clusterInfo.getCspViewK8sClusterDetail().getNodeGroupList();
                
                if (nodeGroups != null) {
                    for (K8sClusterDto.NodeGroup nodeGroup : nodeGroups) {
                        String groupName = nodeGroup.getIid().getNameId();
                        if (event.getNodeGroupName().equals(groupName)) {
                            // 노드 목록에서 새로 생성된 노드 필터링
                            List<K8sClusterDto.IID> allNodes = nodeGroup.getNodes();
                            if (allNodes != null) {
                                return filterNewlyCreatedNodes(allNodes, event);
                            }
                        }
                    }
                }
            }
            
            return Collections.emptyList();
            
        } catch (Exception e) {
            log.error("Error getting newly created nodes", e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 새로 생성된 노드 필터링 (노드 수 증가 기반)
     */
    private List<String> filterNewlyCreatedNodes(List<K8sClusterDto.IID> allNodes, ScalingEvent event) {
        int expectedNewNodeCount = event.getNewNodeCount() - event.getOldNodeCount();
        
        if (expectedNewNodeCount <= 0) {
            log.debug("No new nodes expected: old={}, new={}", event.getOldNodeCount(), event.getNewNodeCount());
            return Collections.emptyList();
        }
        
        if (allNodes.size() < event.getNewNodeCount()) {
            log.warn("Current node count {} is less than expected new count {}", 
                    allNodes.size(), event.getNewNodeCount());
            return Collections.emptyList();
        }
        
        // 노드를 이름으로 정렬하여 마지막 N개 노드가 새로 생성된 것으로 간주
        List<String> newlyCreatedNodes = allNodes.stream()
                .sorted((n1, n2) -> n1.getNameId().compareTo(n2.getNameId()))
                .skip(Math.max(0, allNodes.size() - expectedNewNodeCount))
                .map(K8sClusterDto.IID::getNameId)
                .collect(Collectors.toList());
        
        log.info("Identified {} newly created nodes: {}", newlyCreatedNodes.size(), newlyCreatedNodes);
        return newlyCreatedNodes;
    }

}