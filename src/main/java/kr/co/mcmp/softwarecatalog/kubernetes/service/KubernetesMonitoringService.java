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

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

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
    private final KubernetesLogCollector logCollector;

    @Scheduled(fixedRate = 60000) // 1분마다 실행
    public void monitorKubernetesResources() {
        log.info("Starting Kubernetes resource monitoring");

        List<DeploymentHistory> activeDeployments = getActiveK8sDeployments();
        log.info("Found {} active K8S deployments to monitor", activeDeployments.size());

        for (DeploymentHistory deployment : activeDeployments) {
            String namespace = deployment.getNamespace();
            String clusterName = deployment.getClusterName();
            log.info("Monitoring deployment: ID={}, Namespace={}, Cluster={}, CatalogId={}", 
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
        log.info("All pod names in namespace '{}': {}", namespace, 
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
                
        log.info("Filtered pods for app '{}': {}", appName, 
                pods.stream()
                    .map(pod -> pod.getMetadata().getName())
                    .collect(Collectors.joining(", ")));
            

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
            
            log.info("Pod '{}' status: {}", podName, phase);
            
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
        
        log.info("Pod status summary for app '{}': {}", appName, podStatusSummary);

        Map<String, Object> resourceUsage = getResourceUsagePercentage(client, namespace, appName, pods, runningPods, pendingPods, failedPods);
        status.setCpuUsage((Double) resourceUsage.get("cpuPercentage"));
        status.setMemoryUsage((Double) resourceUsage.get("memoryPercentage"));
        status.setStatus((String) resourceUsage.get("status"));
        status.setServicePort((Integer) resourceUsage.get("port"));
        status.setCheckedAt(LocalDateTime.now());
        status.setClusterName(clusterName);
        status.setNamespace(namespace);
        status.setExecutedBy(user);
        status.setDeploymentType(DeploymentType.K8S);
        status.setCatalog(deployment.getCatalog()); // catalog 정보 설정 추가

        // 로그 수집 및 저장
        collectAndSaveLogs(client, namespace, appName, status, pods);

        log.info("Updating ApplicationStatus for catalogId={}, status={}, podStatus={}, cpuUsage={}, memoryUsage={}", 
                catalogId, status.getStatus(), status.getPodStatus(), status.getCpuUsage(), status.getMemoryUsage());
        
        statusRepository.save(status);
        log.info("ApplicationStatus saved successfully for catalogId={}", catalogId);
    }

    /**
     * 로그를 수집하고 ApplicationStatus에 저장합니다.
     * 중복 로그를 방지하기 위해 해시를 사용합니다.
     */
    private void collectAndSaveLogs(KubernetesClient client, String namespace, String appName, 
                                   ApplicationStatus status, List<Pod> pods) {
        try {
            log.debug("로그 수집 시작 - Namespace: {}, App: {}", namespace, appName);
            
            // LazyInitializationException 방지를 위해 로그 리스트를 미리 초기화
            List<String> existingErrorLogs = new ArrayList<>(status.getErrorLogs());
            List<String> existingInfoLogs = new ArrayList<>(status.getInfoLogs());
            List<String> existingDebugLogs = new ArrayList<>(status.getDebugLogs());
            List<String> existingPodLogs = new ArrayList<>(status.getPodLogs());
            
            // 에러 로그 수집 및 증분 저장
            List<String> newErrorLogs = logCollector.collectAppLogs(client, namespace, appName, 
                    KubernetesLogCollector.LogLevel.ERROR);
            if (!newErrorLogs.isEmpty() && LogHashUtil.hasNewLogs(newErrorLogs, existingErrorLogs)) {
                List<String> uniqueNewErrorLogs = LogHashUtil.extractNewLogs(newErrorLogs, existingErrorLogs);
                
                // 기존 로그에 새로운 로그 추가 (최대 50개 유지)
                List<String> combinedErrorLogs = new ArrayList<>(existingErrorLogs);
                combinedErrorLogs.addAll(uniqueNewErrorLogs);
                
                // 최대 50개로 제한
                if (combinedErrorLogs.size() > 50) {
                    combinedErrorLogs = combinedErrorLogs.subList(combinedErrorLogs.size() - 50, combinedErrorLogs.size());
                }
                
                status.setErrorLogs(combinedErrorLogs);
                status.setErrorLogsHash(LogHashUtil.calculateLogsHash(combinedErrorLogs, 20));
                log.debug("에러 로그 {} 줄 추가 완료 (새로운 로그: {} 줄)", combinedErrorLogs.size(), uniqueNewErrorLogs.size());
            } else {
                log.debug("에러 로그 변경 없음 - 건너뜀");
            }
            
            // INFO 레벨 로그 수집 및 증분 저장
            List<String> newInfoLogs = logCollector.collectAppLogs(client, namespace, appName, 
                    KubernetesLogCollector.LogLevel.INFO);
            if (!newInfoLogs.isEmpty() && LogHashUtil.hasNewLogs(newInfoLogs, existingInfoLogs)) {
                List<String> uniqueNewInfoLogs = LogHashUtil.extractNewLogs(newInfoLogs, existingInfoLogs);
                
                // 기존 로그에 새로운 로그 추가 (최대 50개 유지)
                List<String> combinedInfoLogs = new ArrayList<>(existingInfoLogs);
                combinedInfoLogs.addAll(uniqueNewInfoLogs);
                
                // 최대 50개로 제한
                if (combinedInfoLogs.size() > 50) {
                    combinedInfoLogs = combinedInfoLogs.subList(combinedInfoLogs.size() - 50, combinedInfoLogs.size());
                }
                
                status.setInfoLogs(combinedInfoLogs);
                status.setInfoLogsHash(LogHashUtil.calculateLogsHash(combinedInfoLogs, 20));
                log.debug("INFO 로그 {} 줄 추가 완료 (새로운 로그: {} 줄)", combinedInfoLogs.size(), uniqueNewInfoLogs.size());
            } else {
                log.debug("INFO 로그 변경 없음 - 건너뜀");
            }
            
            // DEBUG 레벨 로그 수집 및 증분 저장
            List<String> newDebugLogs = logCollector.collectAppLogs(client, namespace, appName, 
                    KubernetesLogCollector.LogLevel.DEBUG);
            if (!newDebugLogs.isEmpty() && LogHashUtil.hasNewLogs(newDebugLogs, existingDebugLogs)) {
                List<String> uniqueNewDebugLogs = LogHashUtil.extractNewLogs(newDebugLogs, existingDebugLogs);
                
                // 기존 로그에 새로운 로그 추가 (최대 30개 유지)
                List<String> combinedDebugLogs = new ArrayList<>(existingDebugLogs);
                combinedDebugLogs.addAll(uniqueNewDebugLogs);
                
                // 최대 30개로 제한
                if (combinedDebugLogs.size() > 30) {
                    combinedDebugLogs = combinedDebugLogs.subList(combinedDebugLogs.size() - 30, combinedDebugLogs.size());
                }
                
                status.setDebugLogs(combinedDebugLogs);
                status.setDebugLogsHash(LogHashUtil.calculateLogsHash(combinedDebugLogs, 20));
                log.debug("DEBUG 로그 {} 줄 추가 완료 (새로운 로그: {} 줄)", combinedDebugLogs.size(), uniqueNewDebugLogs.size());
            } else {
                log.debug("DEBUG 로그 변경 없음 - 건너뜀");
            }
            
            // Pod별 상세 로그 수집 및 중복 확인
            collectPodSpecificLogsWithHash(client, namespace, pods, status);
            
        } catch (Exception e) {
            log.error("로그 수집 중 오류 발생 - App: {}, Error: {}", appName, e.getMessage(), e);
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
            }
            
            // Pod 로그 변경 확인 및 저장
            if (!podLogs.isEmpty() && LogHashUtil.hasLogsChanged(podLogs, status.getPodLogsHash())) {
                status.setPodLogs(podLogs);
                status.setPodLogsHash(LogHashUtil.calculateLogsHash(podLogs, 20));
                log.debug("Pod별 로그 {} 줄 수집 완료 (변경됨)", podLogs.size());
            } else {
                log.debug("Pod 로그 변경 없음 - 건너뜀");
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
            }
            
            if (!podLogs.isEmpty()) {
                status.setPodLogs(podLogs);
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
                log.info("Metrics Server is running with {} ready replicas", readyReplicas);
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

        // log.info("appName : " + appName);
        List<GenericKubernetesResource> podMetrics = client.genericKubernetesResources(context)
                .inNamespace(namespace)
                // .withLabel("app", appName)
                .list()
                .getItems();

        log.info("Found {} pod metrics in namespace {}", podMetrics.size(), namespace);
        
        // Pod metrics 상세 정보 로깅
        if (!podMetrics.isEmpty()) {
            log.info("Pod metrics details:");
            podMetrics.forEach(metric -> {
                String podName = metric.getMetadata().getName();
                log.info("  - Pod: {}, starts with {}: {}", podName, appName.toLowerCase(), podName.startsWith(appName.toLowerCase()));
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
                        log.info("Processed pod metric for app '{}': {} (total pods: {})", appName, podName, podCount);
                    }
                } catch (Exception e) {
                    log.error("Error processing pod metric for {}: {}", podName, e.getMessage(), e);
                }
            }
        }
        
        log.info("Final pod count for app '{}': {}, total CPU usage: {}, total memory usage: {}", 
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

        log.info("Final status for app '{}': {} (podCount: {}, pods.size: {})", 
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
        log.info("Metrics Server not found. Installing...");

        String metricsServerYaml = "";
        try {
            metricsServerYaml = downloadMetricsServerYaml();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        client.resourceList(metricsServerYaml).createOrReplace();

        log.info("Metrics Server installation completed");
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
        log.info("Waiting for Metrics Server to be ready...");
        int maxAttempts = 30;
        int attempt = 0;
        while (attempt < maxAttempts) {
            Deployment metricsServer = client.apps().deployments().inNamespace("kube-system").withName("metrics-server")
                    .get();
            if (metricsServer != null && metricsServer.getStatus().getReadyReplicas() != null
                    && metricsServer.getStatus().getReadyReplicas() > 0) {
                log.info("Metrics Server is ready");
                return;
            }
            Thread.sleep(10000); // 10초 대기
            attempt++;
        }
        log.warn("Metrics Server did not become ready within the expected time");
    }

}