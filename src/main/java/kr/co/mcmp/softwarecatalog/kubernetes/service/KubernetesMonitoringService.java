package kr.co.mcmp.softwarecatalog.kubernetes.service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.Pod;
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
import kr.co.mcmp.softwarecatalog.kubernetes.util.LogHashUtil;
import kr.co.mcmp.softwarecatalog.users.Entity.User;
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
    
    @Value("${k8s.autoscaling.default-max-nodes:3}")
    private int defaultMaxNodes;
    
    @Value("${k8s.autoscaling.scaling-timeout-seconds:300}")
    private int scalingTimeoutSeconds;
    
    @Value("${k8s.autoscaling.default-node-count:1}")
    private int defaultNodeCount;

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
        String namespace = deployment.getNamespace();
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
        User user = deployment.getExecutedBy();

        // 모든 Pod 목록 가져오기
        List<Pod> allPods = client.pods().inNamespace(namespace).list().getItems();
        log.debug("All pod names in namespace '{}': {}", namespace, 
                allPods.stream()
                    .map(pod -> pod.getMetadata().getName())
                    .collect(Collectors.joining(", ")));

        // 앱 이름으로 필터링된 Pod 목록
        List<Pod> pods = allPods.stream()
                .filter(pod -> {
                    String podName = pod.getMetadata().getName();
                    boolean matches = podName.startsWith(appName) || 
                                   podName.startsWith(appName.toLowerCase()) ||
                                   podName.contains(appName) ||
                                   podName.contains(appName.toLowerCase());
                    log.debug("Pod '{}' matches app '{}': {}", podName, appName, matches);
                    return matches;
                })
                .collect(Collectors.toList());
                
        log.debug("Filtered pods for app '{}': {}", appName, 
                pods.stream()
                    .map(pod -> pod.getMetadata().getName())
                    .collect(Collectors.joining(", ")));
            

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

        Map<String, Object> resourceUsage = getResourceUsagePercentage(client, namespace, appName, pods, runningPods, pendingPods, failedPods);
        status.setCpuUsage((Double) resourceUsage.get("cpuPercentage"));
        status.setMemoryUsage((Double) resourceUsage.get("memoryPercentage"));
        status.setStatus((String) resourceUsage.get("status"));
        status.setServicePort((Integer) resourceUsage.get("port"));
        status.setCheckedAt(LocalDateTime.now());
        status.setClusterName(clusterName);
        status.setNamespace(namespace);
        status.setNodeGroupName(deployment.getNodeGroupName()); // 노드 그룹 이름 설정
        status.setExecutedBy(user);
        status.setDeploymentType(DeploymentType.K8S);
        status.setCatalog(deployment.getCatalog()); // catalog 정보 설정 추가

        // 로그 수집 및 저장
        collectAndSaveLogs(client, namespace, appName, status, pods);

        log.debug("Updating ApplicationStatus for catalogId={}, status={}, podStatus={}, cpuUsage={}, memoryUsage={}", 
                catalogId, status.getStatus(), status.getPodStatus(), status.getCpuUsage(), status.getMemoryUsage());
        
        // 오토스케일링 처리 (스케일 아웃만)
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

    /**
     * Pod별 상세 로그를 수집합니다. (해시 기반 중복 방지)
     */
    private void collectPodSpecificLogsWithHash(KubernetesClient client, String namespace, 
                                              List<Pod> pods, ApplicationStatus status) {
        try {
            List<String> podLogs = new ArrayList<>();
            
            for (Pod pod : pods) {
                String podName = pod.getMetadata().getName();
                String podPhase = pod.getStatus().getPhase();
                
                // Pod 기본 상태 정보
                podLogs.add(String.format("[%s] Pod Status: %s", podName, podPhase));
                
                // Pod 상태에 따른 상세 정보 수집
                if ("Running".equals(podPhase)) {
                    podLogs.add(String.format("[%s] Pod is running normally", podName));
                    
                    // 컨테이너 상태 확인
                    if (pod.getStatus().getContainerStatuses() != null) {
                        for (var containerStatus : pod.getStatus().getContainerStatuses()) {
                            String containerName = containerStatus.getName();
                            boolean ready = containerStatus.getReady();
                            String state = containerStatus.getState() != null ? 
                                (containerStatus.getState().getRunning() != null ? "Running" :
                                 containerStatus.getState().getWaiting() != null ? "Waiting" :
                                 containerStatus.getState().getTerminated() != null ? "Terminated" : "Unknown") : "Unknown";
                            
                            podLogs.add(String.format("[%s] Container %s: %s (Ready: %s)", 
                                podName, containerName, state, ready));
                        }
                    }
                    
                } else if ("Pending".equals(podPhase)) {
                    podLogs.add(String.format("[%s] Pod is pending - waiting for scheduling", podName));
                    
                    if (pod.getStatus().getConditions() != null) {
                        for (var condition : pod.getStatus().getConditions()) {
                            if ("PodScheduled".equals(condition.getType()) && "False".equals(condition.getStatus())) {
                                podLogs.add(String.format("[%s] Scheduling issue: %s", podName, condition.getMessage()));
                            }
                        }
                    }
                    
                } else if ("Failed".equals(podPhase)) {
                    podLogs.add(String.format("[%s] Pod has failed", podName));
                    
                    if (pod.getStatus().getContainerStatuses() != null) {
                        for (var containerStatus : pod.getStatus().getContainerStatuses()) {
                            if (containerStatus.getState() != null && containerStatus.getState().getTerminated() != null) {
                                var terminated = containerStatus.getState().getTerminated();
                                podLogs.add(String.format("[%s] Container %s terminated: Exit Code %d, Reason: %s", 
                                    podName, containerStatus.getName(), 
                                    terminated.getExitCode() != null ? terminated.getExitCode() : -1,
                                    terminated.getReason() != null ? terminated.getReason() : "Unknown"));
                            }
                        }
                    }
                    
                } else if ("Succeeded".equals(podPhase)) {
                    podLogs.add(String.format("[%s] Pod completed successfully", podName));
                    
                } else {
                    podLogs.add(String.format("[%s] Pod in unknown state: %s", podName, podPhase));
                }
                
                // Pod 이벤트 정보 수집
                podLogs.add(String.format("[%s] Last transition time: %s", podName, 
                    pod.getStatus().getStartTime() != null ? pod.getStatus().getStartTime() : "Unknown"));
                
                // UnifiedLog를 사용하여 Pod 로그 수집
                String containerName = "default";
                if (pod.getStatus().getContainerStatuses() != null && !pod.getStatus().getContainerStatuses().isEmpty()) {
                    containerName = pod.getStatus().getContainerStatuses().get(0).getName();
                }
                String clusterName = client.getNamespace() != null ? client.getNamespace() : "default";
                
                logCollector.collectAndSaveLogs(client, status.getDeploymentHistoryId(), namespace, podName, containerName, clusterName);
            }
            
            // UnifiedLog를 사용하여 Pod 로그 수집
            if (!podLogs.isEmpty()) {
                log.debug("Pod별 로그 {} 줄 수집 완료", podLogs.size());
            } else {
                log.debug("Pod 로그 없음 - 건너뜀");
            }
            
        } catch (Exception e) {
            log.error("Pod별 로그 수집 중 오류 발생: {}", e.getMessage(), e);
        }
    }

    /**
     * Pod별 상세 로그를 수집합니다.
     * - Pod 상태 정보
     * - Pod 이벤트 정보  
     * - Pod별 특수 로그 (시스템 메시지, 상태 변화 등)
     */
    private void collectPodSpecificLogs(KubernetesClient client, String namespace, 
                                      List<Pod> pods, ApplicationStatus status) {
        try {
            List<String> podLogs = new ArrayList<>();
            
            for (Pod pod : pods) {
                String podName = pod.getMetadata().getName();
                String podPhase = pod.getStatus().getPhase();
                
                // Pod 기본 상태 정보
                podLogs.add(String.format("[%s] Pod Status: %s", podName, podPhase));
                
                // Pod 상태에 따른 상세 정보 수집
                if ("Running".equals(podPhase)) {
                    // Running 상태: Pod가 정상 동작 중
                    podLogs.add(String.format("[%s] Pod is running normally", podName));
                    
                    // 컨테이너 상태 확인
                    if (pod.getStatus().getContainerStatuses() != null) {
                        for (var containerStatus : pod.getStatus().getContainerStatuses()) {
                            String containerName = containerStatus.getName();
                            boolean ready = containerStatus.getReady();
                            String state = containerStatus.getState() != null ? 
                                (containerStatus.getState().getRunning() != null ? "Running" :
                                 containerStatus.getState().getWaiting() != null ? "Waiting" :
                                 containerStatus.getState().getTerminated() != null ? "Terminated" : "Unknown") : "Unknown";
                            
                            podLogs.add(String.format("[%s] Container %s: %s (Ready: %s)", 
                                podName, containerName, state, ready));
                        }
                    }
                    
                } else if ("Pending".equals(podPhase)) {
                    // Pending 상태: 스케줄링 대기 중
                    podLogs.add(String.format("[%s] Pod is pending - waiting for scheduling", podName));
                    
                    // Pending 이유 확인
                    if (pod.getStatus().getConditions() != null) {
                        for (var condition : pod.getStatus().getConditions()) {
                            if ("PodScheduled".equals(condition.getType()) && "False".equals(condition.getStatus())) {
                                podLogs.add(String.format("[%s] Scheduling issue: %s", podName, condition.getMessage()));
                            }
                        }
                    }
                    
                } else if ("Failed".equals(podPhase)) {
                    // Failed 상태: 실행 실패
                    podLogs.add(String.format("[%s] Pod has failed", podName));
                    
                    // 실패 이유 확인
                    if (pod.getStatus().getContainerStatuses() != null) {
                        for (var containerStatus : pod.getStatus().getContainerStatuses()) {
                            if (containerStatus.getState() != null && containerStatus.getState().getTerminated() != null) {
                                var terminated = containerStatus.getState().getTerminated();
                                podLogs.add(String.format("[%s] Container %s terminated: Exit Code %d, Reason: %s", 
                                    podName, containerStatus.getName(), 
                                    terminated.getExitCode() != null ? terminated.getExitCode() : -1,
                                    terminated.getReason() != null ? terminated.getReason() : "Unknown"));
                            }
                        }
                    }
                    
                } else if ("Succeeded".equals(podPhase)) {
                    // Succeeded 상태: 정상 완료
                    podLogs.add(String.format("[%s] Pod completed successfully", podName));
                    
                } else {
                    // 기타 상태
                    podLogs.add(String.format("[%s] Pod in unknown state: %s", podName, podPhase));
                }
                
                // Pod 이벤트 정보 수집 (간단한 형태)
                podLogs.add(String.format("[%s] Last transition time: %s", podName, 
                    pod.getStatus().getStartTime() != null ? pod.getStatus().getStartTime() : "Unknown"));
                
                // UnifiedLog를 사용하여 Pod 로그 수집
                String containerName = "default";
                if (pod.getStatus().getContainerStatuses() != null && !pod.getStatus().getContainerStatuses().isEmpty()) {
                    containerName = pod.getStatus().getContainerStatuses().get(0).getName();
                }
                String clusterName = client.getNamespace() != null ? client.getNamespace() : "default";
                
                logCollector.collectAndSaveLogs(client, status.getDeploymentHistoryId(), namespace, podName, containerName, clusterName);
            }
            
            if (!podLogs.isEmpty()) {
                log.debug("Pod별 로그 {} 줄 수집 완료", podLogs.size());
            }
            
        } catch (Exception e) {
            log.error("Pod별 로그 수집 중 오류 발생: {}", e.getMessage(), e);
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
            result.put("status", "UNKNOWN");
            result.put("port", null);
            return result;
        }

        double totalCpuUsage = 0.0;
        double totalMemoryUsage = 0.0;
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
                                totalCpuUsage += parseCpuUsage(cpuUsage);
                                totalMemoryUsage += parseMemoryUsage(memoryUsage);
                                log.debug("Added usage for pod {}: CPU={}, Memory={}", podName, cpuUsage, memoryUsage);
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
        
        log.debug("Final pod count for app '{}': {}, total CPU usage: {}, total memory usage: {}", 
                appName, podCount, totalCpuUsage, totalMemoryUsage);

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
        Process process = Runtime.getRuntime().exec("curl -s " + url);
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
            // K8S 배포가 아니면 스킵
            if (!DeploymentType.K8S.equals(deployment.getDeploymentType())) {
                return;
            }
            
            // 리소스 사용률이 null이면 스킵
            if (status.getCpuUsage() == null || status.getMemoryUsage() == null) {
                return;
            }
            
            String namespace = deployment.getNamespace();
            String clusterName = deployment.getClusterName();
            
            // 노드 그룹 이름 동적 조회
            String nodeGroupName = getNodeGroupName(deployment);
            
            // 노드 그룹 이름이 없으면 오토스케일링 불가
            if (nodeGroupName == null || nodeGroupName.isEmpty()) {
                log.warn("Cannot perform autoscaling: node group name is null for deployment: {}", deployment.getId());
                return;
            }
            
            // 현재 노드 수 조회
            int currentSize = getCurrentNodeCount(deployment, client);
            int maxSize = deployment.getCatalog().getMaxReplicas() != null ? deployment.getCatalog().getMaxReplicas() : defaultMaxNodes;
            
            // CPU 또는 메모리 임계값 초과 시 스케일 아웃
            boolean cpuExceeded = deployment.getCatalog().getCpuThreshold() != null && 
                                status.getCpuUsage() > deployment.getCatalog().getCpuThreshold();
            
            boolean memoryExceeded = deployment.getCatalog().getMemoryThreshold() != null && 
                                   status.getMemoryUsage() > deployment.getCatalog().getMemoryThreshold();
            
            if ((cpuExceeded || memoryExceeded) && currentSize < maxSize) {
                log.info("Resource threshold exceeded for K8S deployment: CPU={}%, Memory={}%, scaling out from {} to {}", 
                        status.getCpuUsage(), status.getMemoryUsage(), currentSize, currentSize + 1);
                
                // 스케일링 이벤트 생성
                ScalingEvent scalingEvent = createScalingEvent(deployment, nodeGroupName, currentSize, currentSize + 1);
                if (scalingEvent != null) {
                    scalingEventRepository.save(scalingEvent);
                } else {
                    log.error("Failed to create scaling event: node group name is null");
                    return;
                }
                
                boolean scaleResult = k8sAutoscaleService.scaleOutNodeGroup(namespace, clusterName, nodeGroupName, currentSize, maxSize);
                if (scaleResult) {
                    log.info("Successfully initiated K8S node group scale out: {} -> {}", currentSize, currentSize + 1);
                    scalingEvent.setStatus(ScalingEvent.ScalingStatus.IN_PROGRESS);
                    scalingEventRepository.save(scalingEvent);
                } else {
                    log.error("Failed to scale out K8S node group");
                    scalingEvent.setStatus(ScalingEvent.ScalingStatus.FAILED);
                    scalingEvent.setErrorMessage("Failed to initiate scale out");
                    scalingEventRepository.save(scalingEvent);
                }
            } else if (currentSize >= maxSize) {
                log.warn("Cannot scale out: current size {} >= max size {} (CPU={}%, Memory={}%)", 
                        currentSize, maxSize, status.getCpuUsage(), status.getMemoryUsage());
            }
            
        } catch (Exception e) {
            log.error("Error handling K8S autoscaling for deployment: {}", deployment.getId(), e);
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
     */
    private boolean isScalingCompleted(ScalingEvent event, KubernetesClient client) {
        try {
            // 현재 노드 수 조회
            int currentNodeCount = getCurrentNodeCountFromEvent(event, client);
            
            // 목표 노드 수와 현재 노드 수 비교
            if (currentNodeCount >= event.getNewNodeCount()) {
                log.info("Scaling completed: current={}, target={}", currentNodeCount, event.getNewNodeCount());
                return true;
            }
            
            // 타임아웃 체크 (5분)
            long secondsSinceTriggered = java.time.Duration.between(event.getTriggeredAt(), LocalDateTime.now()).getSeconds();
            if (secondsSinceTriggered > scalingTimeoutSeconds) { 
                log.warn("Scaling timeout after {} seconds: current={}, target={}", scalingTimeoutSeconds, currentNodeCount, event.getNewNodeCount());
                return true; // 타임아웃도 완료로 간주
            }
            
            log.debug("Scaling in progress: current={}, target={}, elapsed={}s", 
                    currentNodeCount, event.getNewNodeCount(), secondsSinceTriggered);
            return false;
            
        } catch (Exception e) {
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
     */
    private void redeployToNewNodes(DeploymentHistory deployment, int newNodeCount, KubernetesClient client) {
        try {
            log.info("Redeploying application to new nodes: deploymentId={}, newNodeCount={}", 
                    deployment.getId(), newNodeCount);
            
            String namespace = deployment.getNamespace();
            String appName = deployment.getCatalog().getName().toLowerCase().replaceAll("\\s+", "-");
            
            // 기존 Deployment의 replicas 수를 증가시켜 새로운 노드에 Pod 배포
            Deployment k8sDeployment = client.apps().deployments()
                    .inNamespace(namespace)
                    .withName(appName)
                    .get();
            
            if (k8sDeployment != null) {
                // replicas 수를 새로운 노드 수에 맞게 조정
                k8sDeployment.getSpec().setReplicas(newNodeCount);
                
                client.apps().deployments()
                        .inNamespace(namespace)
                        .createOrReplace(k8sDeployment);
                
                log.info("Successfully updated deployment replicas to {} for app: {}", newNodeCount, appName);
                
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
            
            if (clusterInfo != null && clusterInfo.getCspViewK8sClusterDetail() != null) {
                List<K8sClusterDto.NodeGroup> nodeGroups = clusterInfo.getCspViewK8sClusterDetail().getNodeGroupList();
                
                if (nodeGroups != null && !nodeGroups.isEmpty()) {
                    log.debug("Found {} node groups in cluster: {}", nodeGroups.size(), 
                            nodeGroups.stream().map(ng -> ng.getIid().getNameId()).collect(Collectors.toList()));
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
            } else {
                log.error("Failed to get cluster info or cluster detail is null");
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
     * 현재 노드 수 조회 (ScalingEvent 기반)
     */
    private int getCurrentNodeCountFromEvent(ScalingEvent event, KubernetesClient client) {
        try {
            return getCurrentNodeCountFromCluster(event.getNamespace(), event.getClusterName(), 
                    event.getNodeGroupName(), client);
        } catch (Exception e) {
            log.error("Error getting current node count for event: {}", event.getId(), e);
            return event.getOldNodeCount(); // 이전 노드 수 반환
        }
    }
    
    /**
     * 클러스터에서 실제 노드 수 조회
     */
    private int getCurrentNodeCountFromCluster(String namespace, String clusterName, String nodeGroupName, KubernetesClient client) {
        try {
            // Tumblebug API를 통해 클러스터 정보 조회
            K8sClusterDto clusterInfo = cbtumblebugRestApi.getK8sClusterByName(namespace, clusterName);
            
            if (clusterInfo != null && clusterInfo.getCspViewK8sClusterDetail() != null) {
                List<K8sClusterDto.NodeGroup> nodeGroups = clusterInfo.getCspViewK8sClusterDetail().getNodeGroupList();
                
                if (nodeGroups != null) {
                    for (K8sClusterDto.NodeGroup nodeGroup : nodeGroups) {
                        String groupName = nodeGroup.getIid().getNameId();
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
            }
            
            log.warn("Could not determine current node count for group: {}, using default value", nodeGroupName);
            return defaultNodeCount; // 설정 가능한 기본값
            
        } catch (Exception e) {
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