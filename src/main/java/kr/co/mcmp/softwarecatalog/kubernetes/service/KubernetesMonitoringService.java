package kr.co.mcmp.softwarecatalog.kubernetes.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.mcmp.softwarecatalog.SoftwareCatalog;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.LabelSelector;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DaemonSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.PatchContext;
import io.fabric8.kubernetes.client.dsl.base.PatchType;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import io.fabric8.kubernetes.client.impl.BaseClient;
import kr.co.mcmp.softwarecatalog.application.constants.DeploymentType;
import kr.co.mcmp.softwarecatalog.application.constants.ApplicationStatusValues;
import kr.co.mcmp.softwarecatalog.application.model.ApplicationStatus;
import kr.co.mcmp.softwarecatalog.application.model.AutoscaleCheckSample;
import kr.co.mcmp.softwarecatalog.application.model.DeploymentHistory;
import kr.co.mcmp.softwarecatalog.application.model.AbnormalEvent;
import kr.co.mcmp.softwarecatalog.application.model.ResourceMetricsHistory;
import kr.co.mcmp.softwarecatalog.application.repository.ApplicationStatusRepository;
import kr.co.mcmp.softwarecatalog.application.repository.AutoscaleCheckSampleRepository;
import kr.co.mcmp.softwarecatalog.application.repository.DeploymentHistoryRepository;
import kr.co.mcmp.softwarecatalog.application.repository.AbnormalEventRepository;
import kr.co.mcmp.softwarecatalog.application.repository.ResourceMetricsHistoryRepository;
import kr.co.mcmp.softwarecatalog.application.repository.ScalingEventRepository;
import kr.co.mcmp.softwarecatalog.application.model.ScalingEvent;
import kr.co.mcmp.softwarecatalog.application.service.K8sAutoscaleService;
import kr.co.mcmp.ape.cbtumblebug.api.CbtumblebugRestApi;
import kr.co.mcmp.ape.cbtumblebug.dto.K8sClusterDto;
import kr.co.mcmp.ape.cbtumblebug.dto.K8sNodeGroupAutoscaleRequest;
import kr.co.mcmp.softwarecatalog.kubernetes.config.KubernetesClientFactory;
import kr.co.mcmp.softwarecatalog.kubernetes.config.KubernetesNamespaces;
import kr.co.mcmp.softwarecatalog.common.service.RabbitMqAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class KubernetesMonitoringService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String DEFAULT_WORKLOAD_NAMESPACE = KubernetesNamespaces.APPLICATION_WORKLOAD;
    private static final String HELM_INSTANCE_LABEL = "app.kubernetes.io/instance";

    private final KubernetesClientFactory clientFactory;
    private final DeploymentHistoryRepository historyRepository;
    private final ApplicationStatusRepository statusRepository;
    private final ScalingEventRepository scalingEventRepository;
    private final ResourceMetricsHistoryRepository metricsHistoryRepository;
    private final AutoscaleCheckSampleRepository autoscaleCheckSampleRepository;
    private final AbnormalEventRepository abnormalEventRepository;
    private final KubernetesLogCollector logCollector;
    private final K8sAutoscaleService k8sAutoscaleService;
    private final CbtumblebugRestApi cbtumblebugRestApi;
    private final RabbitMqAlertService rabbitMqAlertService;
    private final KubernetesMetricsApiService kubernetesMetricsApiService;

    /** Last snapshot timestamp per deployment ID — controls the 10-minute interval */
    private final java.util.concurrent.ConcurrentHashMap<Long, LocalDateTime> lastSnapshotTime =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** Last observed total restart count per deployment ID — detects restart surges */
    private final java.util.concurrent.ConcurrentHashMap<Long, Integer> lastRestartCount =
            new java.util.concurrent.ConcurrentHashMap<>();

    private LocalDateTime lastAutoscaleSampleCleanupAt;

    @Value("${k8s.autoscaling.default-max-nodes:3}")
    private int defaultMaxNodes;
    
    @Value("${k8s.autoscaling.scaling-timeout-seconds:300}")
    private int scalingTimeoutSeconds;
    
    @Value("${k8s.autoscaling.test-mode:false}")
    private boolean testMode;
    
    @Value("${k8s.autoscaling.test-cpu-threshold:0.1}")
    private double testCpuThreshold;
    
    @Value("${k8s.autoscaling.test-memory-threshold:0.1}")
    private double testMemoryThreshold;

    @Value("${k8s.autoscaling.sample-interval-seconds:10}")
    private int autoscaleSampleIntervalSeconds;

    @Value("${k8s.autoscaling.evaluation-window-seconds:300}")
    private int autoscaleEvaluationWindowSeconds;

    @Value("${k8s.autoscaling.min-sample-coverage-ratio:0.8}")
    private double autoscaleMinSampleCoverageRatio;

    @Value("${k8s.autoscaling.required-exceedance-ratio:0.7}")
    private double autoscaleRequiredExceedanceRatio;

    @Value("${k8s.autoscaling.required-consecutive-exceedance-seconds:120}")
    private int autoscaleRequiredConsecutiveExceedanceSeconds;

    @Value("${k8s.autoscaling.sample-retention-hours:24}")
    private int autoscaleSampleRetentionHours;

    @Value("${k8s.monitoring.health-startup-grace-seconds:180}")
    private long healthStartupGraceSeconds;

    @Value("${rabbitmq.alert.slack-channel-id}")
    private String defaultSlackChannelId;
    @Value("${rabbitmq.alert.email}")
    private String defaultEmail;
    @Value("${rabbitmq.alert.sms-phonenumber}")
    private String defaultSmsPhonenumber;
    @Value("${rabbitmq.alert.kakao-phonenumber}")
    private String defaultKakaoPhonenumber;
    
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

                if (!isMetricsApiAvailable(client)) {
                    log.debug("metrics.k8s.io API is unavailable. Pod status monitoring will continue without CPU/memory metrics.");
                }

                updateApplicationStatus(deployment, client);
            } catch (Exception e) {
                log.error("Error monitoring Kubernetes resources for deployment: ID={}, Namespace={}, Cluster={}, CatalogId={}", 
                        deployment.getId(), namespace, clusterName, 
                        deployment.getCatalog() != null ? deployment.getCatalog().getId() : "null", e);
            }
        }
    }

    @Scheduled(fixedRateString = "${k8s.autoscaling.sample-interval-ms:10000}")
    @Transactional
    public void collectAutoscaleCheckSamples() {
        List<DeploymentHistory> activeDeployments = getActiveK8sDeployments();
        cleanupOldAutoscaleSamplesIfDue();

        for (DeploymentHistory deployment : activeDeployments) {
            String namespace = deployment.getNamespace();
            String clusterName = deployment.getClusterName();

            try (KubernetesClient client = clientFactory.getClient(namespace, clusterName)) {
                if (!isMetricsApiAvailable(client)) {
                    log.debug("Skipping autoscale sample collection because metrics.k8s.io API is unavailable: deployment={}", deployment.getId());
                    continue;
                }

                collectAutoscaleCheckSample(deployment, client);
            } catch (Exception e) {
                log.warn("Failed to collect autoscale check sample: deployment={}, namespace={}, cluster={}",
                        deployment.getId(), namespace, clusterName, e);
            }
        }
    }

    private boolean isManagedJupyter(DeploymentHistory deployment) {
        return deployment.getReleaseName() != null && deployment.getReleaseName().startsWith("mcmp-jupyter-");
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

    private void collectAutoscaleCheckSample(DeploymentHistory deployment, KubernetesClient client) {
        if (deployment.getCatalog() == null || deployment.getCatalog().getHelmChart() == null) {
            log.debug("Skipping autoscale sample collection because catalog or helm chart is missing: deployment={}", deployment.getId());
            return;
        }

        String namespace = isManagedJupyter(deployment)
                ? KubernetesNamespaces.jupyterWorkloadNamespace(client, deployment.getNamespace(), deployment.getReleaseName())
                : DEFAULT_WORKLOAD_NAMESPACE;
        String appName = isManagedJupyter(deployment) ? "jupyter" : deployment.getCatalog().getHelmChart().getChartName();
        String lookupName = resolveWorkloadLookupName(deployment, appName);
        List<Pod> pods = findPodsForDeployment(client, namespace, deployment, appName);
        if (pods.isEmpty()) {
            log.debug("Skipping autoscale sample collection because no pods are running for deployment={}", deployment.getId());
            return;
        }
        long runningPods = countPodsByPhase(pods, "RUNNING");
        long pendingPods = countPodsByPhase(pods, "PENDING");
        long failedPods = countPodsByPhase(pods, "FAILED");
        Map<String, Object> resourceUsage = getResourceUsagePercentage(client, namespace, appName, lookupName, pods, runningPods, pendingPods, failedPods);

        Double cpuUsage = (Double) resourceUsage.get("cpuPercentage");
        Double memoryUsage = (Double) resourceUsage.get("memoryPercentage");
        Double cpuThreshold = getAutoscaleCpuThreshold(deployment);
        Double memoryThreshold = getAutoscaleMemoryThreshold(deployment);
        boolean cpuExceeded = isThresholdExceeded(cpuUsage, cpuThreshold);
        boolean memoryExceeded = isThresholdExceeded(memoryUsage, memoryThreshold);

        autoscaleCheckSampleRepository.save(AutoscaleCheckSample.builder()
                .deploymentId(deployment.getId())
                .checkedAt(LocalDateTime.now())
                .cpuUsagePct(cpuUsage)
                .memoryUsagePct(memoryUsage)
                .cpuThreshold(cpuThreshold)
                .memoryThreshold(memoryThreshold)
                .cpuExceeded(cpuExceeded)
                .memoryExceeded(memoryExceeded)
                .thresholdExceeded(cpuExceeded || memoryExceeded)
                .deploymentType(DeploymentType.K8S.name())
                .build());
    }

    private List<Pod> findPodsForDeployment(KubernetesClient client, String namespace, DeploymentHistory deployment, String appName) {
        String lookupName = resolveWorkloadLookupName(deployment, appName);
        Map<String, Pod> matchedPods = new LinkedHashMap<>();

        if (StringUtils.isNotBlank(deployment.getReleaseName())) {
            client.pods().inNamespace(namespace)
                    .withLabel(HELM_INSTANCE_LABEL, deployment.getReleaseName())
                    .list()
                    .getItems()
                    .forEach(pod -> addPod(matchedPods, pod));
        }

        addPodsFromWorkloadSelectors(client, namespace, lookupName, matchedPods);

        if (matchedPods.isEmpty()) {
            client.pods().inNamespace(namespace).list().getItems().stream()
                    .filter(pod -> isNameMatch(pod.getMetadata().getName(), lookupName)
                            || isNameMatch(pod.getMetadata().getName(), appName))
                    .forEach(pod -> addPod(matchedPods, pod));
        }

        return new ArrayList<>(matchedPods.values());
    }

    private void addPodsFromWorkloadSelectors(KubernetesClient client, String namespace, String workloadName, Map<String, Pod> matchedPods) {
        if (StringUtils.isBlank(workloadName)) {
            return;
        }

        Deployment namedDeployment = client.apps().deployments().inNamespace(namespace).withName(workloadName).get();
        if (namedDeployment != null) {
            addPodsBySelector(client, namespace, selectorOf(namedDeployment), matchedPods);
        }

        client.apps().deployments().inNamespace(namespace)
                .withLabel(HELM_INSTANCE_LABEL, workloadName)
                .list()
                .getItems()
                .forEach(deployment -> addPodsBySelector(client, namespace, selectorOf(deployment), matchedPods));

        StatefulSet namedStatefulSet = client.apps().statefulSets().inNamespace(namespace).withName(workloadName).get();
        if (namedStatefulSet != null) {
            addPodsBySelector(client, namespace, selectorOf(namedStatefulSet), matchedPods);
        }

        client.apps().statefulSets().inNamespace(namespace)
                .withLabel(HELM_INSTANCE_LABEL, workloadName)
                .list()
                .getItems()
                .forEach(statefulSet -> addPodsBySelector(client, namespace, selectorOf(statefulSet), matchedPods));

        DaemonSet namedDaemonSet = client.apps().daemonSets().inNamespace(namespace).withName(workloadName).get();
        if (namedDaemonSet != null) {
            addPodsBySelector(client, namespace, selectorOf(namedDaemonSet), matchedPods);
        }

        client.apps().daemonSets().inNamespace(namespace)
                .withLabel(HELM_INSTANCE_LABEL, workloadName)
                .list()
                .getItems()
                .forEach(daemonSet -> addPodsBySelector(client, namespace, selectorOf(daemonSet), matchedPods));
    }

    private LabelSelector selectorOf(Deployment deployment) {
        return deployment != null && deployment.getSpec() != null ? deployment.getSpec().getSelector() : null;
    }

    private LabelSelector selectorOf(StatefulSet statefulSet) {
        return statefulSet != null && statefulSet.getSpec() != null ? statefulSet.getSpec().getSelector() : null;
    }

    private LabelSelector selectorOf(DaemonSet daemonSet) {
        return daemonSet != null && daemonSet.getSpec() != null ? daemonSet.getSpec().getSelector() : null;
    }

    private void addPodsBySelector(KubernetesClient client, String namespace, LabelSelector selector, Map<String, Pod> matchedPods) {
        if (selector == null || selector.getMatchLabels() == null || selector.getMatchLabels().isEmpty()) {
            return;
        }

        client.pods().inNamespace(namespace)
                .withLabels(selector.getMatchLabels())
                .list()
                .getItems()
                .forEach(pod -> addPod(matchedPods, pod));
    }

    private void addPod(Map<String, Pod> matchedPods, Pod pod) {
        if (pod == null || pod.getMetadata() == null || pod.getMetadata().getName() == null) {
            return;
        }
        matchedPods.putIfAbsent(pod.getMetadata().getName(), pod);
    }

    private String resolveWorkloadLookupName(DeploymentHistory deployment, String appName) {
        return StringUtils.defaultIfBlank(deployment.getReleaseName(), appName);
    }

    private boolean isNameMatch(String candidate, String expected) {
        if (StringUtils.isBlank(candidate) || StringUtils.isBlank(expected)) {
            return false;
        }
        String lowerCandidate = candidate.toLowerCase();
        String lowerExpected = expected.toLowerCase();
        return lowerCandidate.startsWith(lowerExpected) || lowerCandidate.contains(lowerExpected);
    }

    private long countPodsByPhase(List<Pod> pods, String phase) {
        return pods.stream()
                .filter(pod -> phase.equalsIgnoreCase(pod.getStatus().getPhase()))
                .count();
    }

    private Double getAutoscaleCpuThreshold(DeploymentHistory deployment) {
        if (testMode && deployment.getRebalancingCpuThreshold() == null) {
            return testCpuThreshold;
        }
        return deployment.resolveRebalancingCpuThreshold();
    }

    private Double getAutoscaleMemoryThreshold(DeploymentHistory deployment) {
        if (testMode && deployment.getRebalancingMemoryThreshold() == null) {
            return testMemoryThreshold;
        }
        return deployment.resolveRebalancingMemoryThreshold();
    }

    private boolean isThresholdExceeded(Double usage, Double threshold) {
        return usage != null && threshold != null && usage > threshold;
    }

    @Transactional
    private void updateApplicationStatus(DeploymentHistory deployment, KubernetesClient client) {
        // K8s 배포는 항상 default namespace에 배포됨
        String namespace = isManagedJupyter(deployment)
                ? KubernetesNamespaces.jupyterWorkloadNamespace(client, deployment.getNamespace(), deployment.getReleaseName())
                : DEFAULT_WORKLOAD_NAMESPACE;
        String clusterName = deployment.getClusterName();
        
        // null 체크 추가
        if (deployment.getCatalog() == null) {
            log.error("Deployment catalog is null for deployment ID: {}", deployment.getId());
            return;
        }
        
        if (deployment.getCatalog().getHelmChart() == null && !isManagedJupyter(deployment)) {
            log.error("HelmChart is null for catalog ID: {}", deployment.getCatalog().getId());
            return;
        }

        if (ApplicationStatusValues.UNINSTALLED.equalsIgnoreCase(deployment.getStatus())) {
            log.debug("Skipping Kubernetes monitoring for uninstalled deployment history: {}", deployment.getId());
            return;
        }
        
        String appName = isManagedJupyter(deployment) ? "jupyter" : deployment.getCatalog().getHelmChart().getChartName();
        String lookupName = resolveWorkloadLookupName(deployment, appName);
        Long catalogId = deployment.getCatalog().getId();

        // 모든 Pod 목록 가져오기
        List<Pod> pods = findPodsForDeployment(client, namespace, deployment, appName);

                
        // 매칭되지 않은 경우 로그 출력
        if (pods.isEmpty()) {
            log.warn("No pods found for deployment ID {} using lookup '{}' in namespace '{}'", deployment.getId(), lookupName, namespace);
        }
            

        // 최신 ApplicationStatus 조회 (기존 메서드 사용)
        ApplicationStatus status = statusRepository.findByDeploymentHistoryId(deployment.getId())
                .orElseGet(() -> statusRepository.findLatestByNamespaceAndClusterNameAndCatalogId(
                                deployment.getNamespace(), clusterName, catalogId)
                        .orElseGet(() -> ApplicationStatus.builder()
                                .deploymentType(DeploymentType.K8S)
                                .deploymentHistoryId(deployment.getId())
                                .build()));

        if (ApplicationStatusValues.UNINSTALLED.equalsIgnoreCase(status.getStatus())
                && Objects.equals(status.getDeploymentHistoryId(), deployment.getId())) {
            log.debug("Skipping Kubernetes monitoring for uninstalled application status: {}", status.getId());
            return;
        }
        
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

        Map<String, Object> resourceUsage = getResourceUsagePercentage(client, namespace, appName, lookupName, pods, runningPods, pendingPods, failedPods);
        status.setCpuUsage((Double) resourceUsage.get("cpuPercentage"));
        status.setMemoryUsage((Double) resourceUsage.get("memoryPercentage"));
        status.setNetworkIn((Double) resourceUsage.get("networkIn"));
        status.setNetworkOut((Double) resourceUsage.get("networkOut"));
        status.setStatus((String) resourceUsage.get("status"));
        status.setIsHealthCheck(KubernetesPodHealthEvaluator.evaluate(
                pods,
                Duration.ofSeconds(Math.max(healthStartupGraceSeconds, 0L)),
                Instant.now()));
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
        if (isWorkloadRebalancingEnabled(deployment)) {
            log.debug("Calling handleK8sAutoscaling for deployment: {}, CatalogId: {}", deployment.getId(), catalogId);
            handleK8sAutoscaling(deployment, status, client);

        } else {
            log.debug("Skipping workload rebalancing for deployment: {}, CatalogId: {}", deployment.getId(), catalogId);
        }

        // Persist 10-minute metrics snapshot if interval has elapsed
        saveMetricsSnapshotIfDue(deployment, status, pods, resourceUsage);

        // Detect and save Pod lifecycle events (OOM, CrashLoop, etc.)
        detectAndSaveAbnormalPodEvents(deployment, pods);

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
     * Saves a snapshot to resource_metrics_history only if 10 minutes have elapsed since the last one.
     * For K8s, CPU/Memory values are taken directly from ApplicationStatus (Pod metrics-based),
     * not relative to total cluster node capacity.
     */
    private void saveMetricsSnapshotIfDue(DeploymentHistory deployment, ApplicationStatus status, List<Pod> pods, Map<String, Object> resourceUsage) {
        Long deploymentId = deployment.getId();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime last = lastSnapshotTime.get(deploymentId);

        if (last != null && now.isBefore(last.plusMinutes(10))) {
            return;
        }

        try {
            int totalRestartCount = pods.stream()
                    .flatMap(pod -> pod.getStatus() != null && pod.getStatus().getContainerStatuses() != null
                            ? pod.getStatus().getContainerStatuses().stream() : java.util.stream.Stream.empty())
                    .mapToInt(cs -> cs.getRestartCount() != null ? cs.getRestartCount() : 0)
                    .sum();

            ResourceMetricsHistory snapshot = ResourceMetricsHistory.builder()
                    .deploymentId(deploymentId)
                    .recordedAt(now)
                    .cpuUsagePct(status.getCpuUsage())
                    .memoryUsagePct(status.getMemoryUsage())
                    .memoryBytes(asLong(resourceUsage.get("memoryBytes")))
                    .memoryLimitBytes(asLong(resourceUsage.get("memoryLimitBytes")))
                    .networkInBytes(status.getNetworkIn() != null ? status.getNetworkIn().longValue() : null)
                    .networkOutBytes(status.getNetworkOut() != null ? status.getNetworkOut().longValue() : null)
                    .restartCount(totalRestartCount)
                    .oomKilled(false)
                    .status(status.getStatus())
                    .resourceType(deployment.getResourceType())
                    .deploymentType("K8S")
                    .build();

            metricsHistoryRepository.save(snapshot);
            lastSnapshotTime.put(deploymentId, now);
            log.debug("Saved K8s metrics snapshot for deployment: {}", deploymentId);
        } catch (Exception e) {
            log.error("Failed to save K8s metrics snapshot for deployment: {}", deploymentId, e);
        }
    }

    /**
     * Detects and saves OOMKilled, CrashLoopBackOff, and restart surge events from Pod containerStatuses.
     */
    private void detectAndSaveAbnormalPodEvents(DeploymentHistory deployment, List<Pod> pods) {
        Long deploymentId = deployment.getId();
        LocalDateTime now = LocalDateTime.now();

        int totalRestarts = 0;

        for (Pod pod : pods) {
            if (pod.getStatus() == null || pod.getStatus().getContainerStatuses() == null) continue;

            String podName = pod.getMetadata().getName();

            for (ContainerStatus cs : pod.getStatus().getContainerStatuses()) {
                String containerName = cs.getName();
                int restartCount = cs.getRestartCount() != null ? cs.getRestartCount() : 0;
                totalRestarts += restartCount;

                // OOMKilled detection via lastState.terminated.reason
                if (cs.getLastState() != null
                        && cs.getLastState().getTerminated() != null
                        && "OOMKilled".equals(cs.getLastState().getTerminated().getReason())) {
                    try {
                        abnormalEventRepository.save(AbnormalEvent.builder()
                                .deploymentId(deploymentId)
                                .eventType("OOM_KILLED")
                                .severity("CRITICAL")
                                .occurredAt(now)
                                .podName(podName)
                                .containerName(containerName)
                                .exitCode(cs.getLastState().getTerminated().getExitCode())
                                .detailMessage("OOMKilled detected in lastState.terminated")
                                .createdAt(now)
                                .build());
                        log.warn("OOM_KILLED event recorded for deployment: {}, pod: {}", deploymentId, podName);
                    } catch (Exception e) {
                        log.error("Failed to save OOM_KILLED event", e);
                    }
                }

                // CrashLoopBackOff detection via state.waiting.reason
                if (cs.getState() != null
                        && cs.getState().getWaiting() != null
                        && "CrashLoopBackOff".equals(cs.getState().getWaiting().getReason())) {
                    try {
                        abnormalEventRepository.save(AbnormalEvent.builder()
                                .deploymentId(deploymentId)
                                .eventType("CRASH_LOOP")
                                .severity("CRITICAL")
                                .occurredAt(now)
                                .podName(podName)
                                .containerName(containerName)
                                .detailMessage("CrashLoopBackOff detected: " + cs.getState().getWaiting().getMessage())
                                .createdAt(now)
                                .build());
                        log.warn("CRASH_LOOP event recorded for deployment: {}, pod: {}", deploymentId, podName);
                    } catch (Exception e) {
                        log.error("Failed to save CRASH_LOOP event", e);
                    }
                }

                // ImagePullBackOff detection
                if (cs.getState() != null
                        && cs.getState().getWaiting() != null
                        && "ImagePullBackOff".equals(cs.getState().getWaiting().getReason())) {
                    try {
                        abnormalEventRepository.save(AbnormalEvent.builder()
                                .deploymentId(deploymentId)
                                .eventType("IMAGE_PULL_ERROR")
                                .severity("WARNING")
                                .occurredAt(now)
                                .podName(podName)
                                .containerName(containerName)
                                .detailMessage("ImagePullBackOff: " + cs.getState().getWaiting().getMessage())
                                .createdAt(now)
                                .build());
                    } catch (Exception e) {
                        log.error("Failed to save IMAGE_PULL_ERROR event", e);
                    }
                }
            }
        }

        // Restart surge detection based on total across all Pods
        Integer prevTotal = lastRestartCount.get(deploymentId);
        if (prevTotal != null && totalRestarts > prevTotal) {
            int increment = totalRestarts - prevTotal;
            if (increment >= 1) {
                String eventType = increment >= 3 ? "CRASH_LOOP" : "RESTART";
                String severity = increment >= 3 ? "CRITICAL" : "WARNING";
                try {
                    abnormalEventRepository.save(AbnormalEvent.builder()
                            .deploymentId(deploymentId)
                            .eventType(eventType)
                            .severity(severity)
                            .occurredAt(now)
                            .detailMessage(String.format("Total restart count increased by %d (total: %d)", increment, totalRestarts))
                            .createdAt(now)
                            .build());
                } catch (Exception e) {
                    log.error("Failed to save restart event for deployment: {}", deploymentId, e);
                }
            }
        }
        lastRestartCount.put(deploymentId, totalRestarts);
    }

    private Map<String, Object> getResourceUsagePercentage(KubernetesClient client, String namespace, String appName, String lookupName, List<Pod> pods, long runningPods, long pendingPods, long failedPods) {
        ResourceDefinitionContext context = new ResourceDefinitionContext.Builder()
                .withGroup("metrics.k8s.io")
                .withVersion("v1beta1")
                .withKind("PodMetrics")
                .withPlural("pods")
                .withNamespaced(true)
                .build();

        List<GenericKubernetesResource> podMetrics = Collections.emptyList();
        try {
            podMetrics = client.genericKubernetesResources(context)
                    .inNamespace(namespace)
                    // .withLabel("app", appName)
                    .list()
                    .getItems();
        } catch (Exception e) {
            log.warn("Pod metrics API is unavailable for namespace '{}'. Runtime status will be based on pod phase only.", namespace);
        }

        Set<String> targetPodNames = pods.stream()
                .filter(pod -> pod.getMetadata() != null && pod.getMetadata().getName() != null)
                .map(pod -> pod.getMetadata().getName())
                .collect(Collectors.toSet());

        log.debug("Found {} pod metrics in namespace {}", podMetrics.size(), namespace);
        
        // Pod metrics 상세 정보 로깅
        if (!podMetrics.isEmpty()) {
            log.debug("Pod metrics details:");
            podMetrics.forEach(metric -> {
                String podName = metric.getMetadata().getName();
                log.debug("  - Pod: {}, selected for {}: {}", podName, lookupName, targetPodNames.contains(podName));
            });
        }

        Map<String, PodSummaryStats> podSummaryStats = getPodSummaryStats(client, namespace, pods);
        Long summaryMemoryBytes = sumPodSummaryValue(podSummaryStats, PodSummaryStats::getMemoryBytes);
        Long summaryNetworkInBytes = sumPodSummaryValue(podSummaryStats, PodSummaryStats::getNetworkInBytes);
        Long summaryNetworkOutBytes = sumPodSummaryValue(podSummaryStats, PodSummaryStats::getNetworkOutBytes);
        Long memoryLimitBytes = calculateMemoryLimitBytes(pods);

        if (podMetrics.isEmpty()) {
            log.warn("No pod metrics found. Runtime status will be based on pod phase only.");
            Map<String, Object> result = new HashMap<>();
            result.put("cpuPercentage", 0.0);
            result.put("memoryPercentage", 0.0);
            result.put("memoryBytes", summaryMemoryBytes);
            result.put("memoryLimitBytes", memoryLimitBytes);
            result.put("networkIn", summaryNetworkInBytes != null ? summaryNetworkInBytes.doubleValue() : null);
            result.put("networkOut", summaryNetworkOutBytes != null ? summaryNetworkOutBytes.doubleValue() : null);
            result.put("status", resolveRuntimeStatus(pods, runningPods, pendingPods, failedPods));
            result.put("port", findPrimaryServicePort(client, namespace, lookupName, appName));
            return result;
        }

        double totalCpuUsage = 0.0;
        double totalMemoryUsage = 0.0;
        double totalNetworkIn = 0.0;
        double totalNetworkOut = 0.0;
        long totalMemoryBytes = 0L;
        boolean hasMemoryBytes = false;
        boolean hasNetworkMetrics = false;
        int podCount = 0;

        for (GenericKubernetesResource podMetric : podMetrics) {
            String podName = podMetric.getMetadata().getName();
            boolean matchesApp = targetPodNames.contains(podName);
            
            log.debug("Processing pod metric: {} - selected for '{}': {}", podName, lookupName, matchesApp);
            
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
                                Long memoryBytes = parseMemoryBytes(memoryUsage);
                                if (memoryBytes != null) {
                                    totalMemoryBytes += memoryBytes;
                                    hasMemoryBytes = true;
                                }
                                
                                if (networkRxBytes != null) {
                                    totalNetworkIn += parseNetworkBytes(networkRxBytes);
                                    hasNetworkMetrics = true;
                                }
                                if (networkTxBytes != null) {
                                    totalNetworkOut += parseNetworkBytes(networkTxBytes);
                                    hasNetworkMetrics = true;
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
        // Both usage and capacity are in GiB. Do not add a unit conversion here.
        double memoryUsagePercentage = (totalMemoryUsage / totalMemoryCapacity) * 100;

        // 소수점 2자리까지 반올림
        Double roundedCpuUsage = Math.round(cpuUsagePercentage * 100.0) / 100.0;
        Double roundedMemoryUsage = Math.round(memoryUsagePercentage * 100.0) / 100.0;

        // 서비스 포트 정보 수집
        Integer primaryPort = findPrimaryServicePort(client, namespace, lookupName, appName);

        String status = resolveRuntimeStatus(pods, runningPods, pendingPods, failedPods);
        Long memoryBytes;
        if (hasMemoryBytes) {
            memoryBytes = totalMemoryBytes;
        } else {
            memoryBytes = summaryMemoryBytes;
        }
        Long networkInBytes = summaryNetworkInBytes != null ? summaryNetworkInBytes : (hasNetworkMetrics ? Math.round(totalNetworkIn) : null);
        Long networkOutBytes = summaryNetworkOutBytes != null ? summaryNetworkOutBytes : (hasNetworkMetrics ? Math.round(totalNetworkOut) : null);

        Map<String, Object> result = new HashMap<>();
        result.put("cpuPercentage", roundedCpuUsage != null ? roundedCpuUsage : 0.0);
        result.put("memoryPercentage", roundedMemoryUsage != null ? roundedMemoryUsage : 0.0);
        result.put("memoryBytes", memoryBytes);
        result.put("memoryLimitBytes", memoryLimitBytes);
        result.put("networkIn", networkInBytes != null ? networkInBytes.doubleValue() : null);
        result.put("networkOut", networkOutBytes != null ? networkOutBytes.doubleValue() : null);
        result.put("status", status);
        result.put("port", primaryPort);

        log.debug("Final status for app '{}': {} (podCount: {}, pods.size: {})", 
                appName, status, podCount, pods.size());

        return result;
    }

    private String resolveRuntimeStatus(List<Pod> pods, long runningPods, long pendingPods, long failedPods) {
        if (runningPods > 0) {
            return "RUNNING";
        }
        if (pods.isEmpty()) {
            return "STOPPED";
        }
        if (failedPods > 0) {
            return "FAILED";
        }
        if (pendingPods > 0) {
            boolean hasImagePullError = pods.stream()
                    .filter(pod -> pod.getStatus() != null && "PENDING".equalsIgnoreCase(pod.getStatus().getPhase()))
                    .anyMatch(pod -> pod.getStatus().getContainerStatuses() != null &&
                            pod.getStatus().getContainerStatuses().stream()
                                    .anyMatch(containerStatus ->
                                            containerStatus.getState() != null &&
                                                    containerStatus.getState().getWaiting() != null &&
                                                    "ImagePullBackOff".equals(containerStatus.getState().getWaiting().getReason())));

            return hasImagePullError ? "IMAGE_PULL_ERROR" : "PENDING";
        }
        return "UNKNOWN";
    }

    private Map<String, PodSummaryStats> getPodSummaryStats(KubernetesClient client, String namespace, List<Pod> pods) {
        if (!(client instanceof BaseClient baseClient) || pods == null || pods.isEmpty()) {
            return Collections.emptyMap();
        }

        Set<String> targetPodNames = pods.stream()
                .filter(pod -> pod.getMetadata() != null && pod.getMetadata().getName() != null)
                .map(pod -> pod.getMetadata().getName())
                .collect(Collectors.toSet());

        if (targetPodNames.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, PodSummaryStats> statsByPodName = new HashMap<>();
        List<String> nodeNames = pods.stream()
                .filter(pod -> pod.getSpec() != null && pod.getSpec().getNodeName() != null)
                .map(pod -> pod.getSpec().getNodeName())
                .distinct()
                .collect(Collectors.toList());

        for (String nodeName : nodeNames) {
            try {
                String raw = baseClient.raw("/api/v1/nodes/" + nodeName + "/proxy/stats/summary");
                JsonNode root = OBJECT_MAPPER.readTree(raw);
                for (JsonNode podNode : root.path("pods")) {
                    JsonNode podRef = podNode.path("podRef");
                    String podNamespace = podRef.path("namespace").asText(null);
                    String podName = podRef.path("name").asText(null);
                    if (!namespace.equals(podNamespace) || !targetPodNames.contains(podName)) {
                        continue;
                    }

                    statsByPodName.put(podName, new PodSummaryStats(
                            asNullableLong(podNode.path("memory").get("workingSetBytes")),
                            asNullableLong(podNode.path("network").get("rxBytes")),
                            asNullableLong(podNode.path("network").get("txBytes"))));
                }
            } catch (Exception e) {
                log.debug("Unable to read kubelet stats summary for node '{}'. Continuing without pod network summary.", nodeName, e);
            }
        }

        return statsByPodName;
    }

    private Long calculateMemoryLimitBytes(List<Pod> pods) {
        if (pods == null || pods.isEmpty()) {
            return null;
        }

        long total = 0L;
        boolean found = false;
        for (Pod pod : pods) {
            if (pod.getSpec() == null || pod.getSpec().getContainers() == null) {
                continue;
            }

            for (var container : pod.getSpec().getContainers()) {
                if (container.getResources() == null
                        || container.getResources().getLimits() == null
                        || !container.getResources().getLimits().containsKey("memory")) {
                    continue;
                }

                Long bytes = parseMemoryQuantityBytes(container.getResources().getLimits().get("memory"));
                if (bytes != null) {
                    total += bytes;
                    found = true;
                }
            }
        }

        return found ? total : null;
    }

    private Integer findPrimaryServicePort(KubernetesClient client, String namespace, String lookupName, String appName) {
        try {
            Integer releasePort = client.services().inNamespace(namespace)
                    .withLabel(HELM_INSTANCE_LABEL, lookupName)
                    .list()
                    .getItems()
                    .stream()
                    .flatMap(service -> service.getSpec() != null && service.getSpec().getPorts() != null
                            ? service.getSpec().getPorts().stream()
                            : java.util.stream.Stream.empty())
                    .map(servicePort -> servicePort.getNodePort() != null ? servicePort.getNodePort() : servicePort.getPort())
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
            if (releasePort != null) {
                return releasePort;
            }

            return client.services().inNamespace(namespace).list().getItems().stream()
                    .filter(service -> service.getMetadata() != null
                            && service.getMetadata().getName() != null
                            && (service.getMetadata().getName().startsWith(lookupName.toLowerCase())
                            || service.getMetadata().getName().startsWith(appName.toLowerCase())))
                    .flatMap(service -> service.getSpec() != null && service.getSpec().getPorts() != null
                            ? service.getSpec().getPorts().stream()
                            : java.util.stream.Stream.empty())
                    .map(servicePort -> servicePort.getNodePort() != null ? servicePort.getNodePort() : servicePort.getPort())
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            log.warn("Failed to resolve service port for app '{}': {}", lookupName, e.getMessage());
            return null;
        }
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
                            // Quantity.getAmount() drops Ki/Mi/Gi; preserve the unit.
                            return parseMemoryUsage(node.getStatus().getCapacity().get("memory").toString());
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
            // Kubernetes Quantity supports binary, decimal and exponent formats.
            // For memory its numerical amount is bytes; normalize once to GiB.
            return new Quantity(memoryUsage.trim()).getNumericalAmount().doubleValue()
                    / (1024.0 * 1024.0 * 1024.0);
        } catch (IllegalArgumentException | ArithmeticException e) {
            log.warn("Error parsing memory usage: {}", memoryUsage, e);
            return 0.0;
        }
    }

    private Long parseMemoryBytes(String memoryUsage) {
        if (memoryUsage == null || memoryUsage.trim().isEmpty()) {
            return null;
        }

        String value = memoryUsage.trim();
        try {
            if (value.endsWith("Ki")) {
                return Math.round(Double.parseDouble(value.substring(0, value.length() - 2)) * 1024.0);
            } else if (value.endsWith("Mi")) {
                return Math.round(Double.parseDouble(value.substring(0, value.length() - 2)) * 1024.0 * 1024.0);
            } else if (value.endsWith("Gi")) {
                return Math.round(Double.parseDouble(value.substring(0, value.length() - 2)) * 1024.0 * 1024.0 * 1024.0);
            } else if (value.endsWith("Ti")) {
                return Math.round(Double.parseDouble(value.substring(0, value.length() - 2)) * 1024.0 * 1024.0 * 1024.0 * 1024.0);
            } else if (value.endsWith("K")) {
                return Math.round(Double.parseDouble(value.substring(0, value.length() - 1)) * 1000.0);
            } else if (value.endsWith("M")) {
                return Math.round(Double.parseDouble(value.substring(0, value.length() - 1)) * 1000.0 * 1000.0);
            } else if (value.endsWith("G")) {
                return Math.round(Double.parseDouble(value.substring(0, value.length() - 1)) * 1000.0 * 1000.0 * 1000.0);
            } else {
                return Math.round(Double.parseDouble(value));
            }
        } catch (NumberFormatException e) {
            log.warn("Error parsing memory bytes: {}", memoryUsage, e);
            return null;
        }
    }

    private Long parseMemoryQuantityBytes(Quantity quantity) {
        if (quantity == null || quantity.getAmount() == null) {
            return null;
        }

        String format = quantity.getFormat() != null ? quantity.getFormat() : "";
        return parseMemoryBytes(quantity.getAmount() + format);
    }

    private double parseNetworkBytes(String networkBytes) {
        if (networkBytes == null || networkBytes.trim().isEmpty()) {
            log.warn("Network bytes string is null or empty");
            return 0.0;
        }
        
        try {
            // Keep network values in bytes because resource_metrics_history stores bytes.
            if (networkBytes.endsWith("Ki")) {
                return Double.parseDouble(networkBytes.substring(0, networkBytes.length() - 2)) * 1024.0;
            } else if (networkBytes.endsWith("Mi")) {
                return Double.parseDouble(networkBytes.substring(0, networkBytes.length() - 2)) * 1024.0 * 1024.0;
            } else if (networkBytes.endsWith("Gi")) {
                return Double.parseDouble(networkBytes.substring(0, networkBytes.length() - 2)) * 1024.0 * 1024.0 * 1024.0;
            } else {
                // No suffix means bytes.
                return Double.parseDouble(networkBytes);
            }
        } catch (NumberFormatException e) {
            log.warn("Error parsing network bytes: {}", networkBytes, e);
            return 0.0;
        }
    }

    private Long sumPodSummaryValue(Map<String, PodSummaryStats> statsByPodName, java.util.function.Function<PodSummaryStats, Long> extractor) {
        long total = 0L;
        boolean found = false;
        for (PodSummaryStats stats : statsByPodName.values()) {
            Long value = extractor.apply(stats);
            if (value != null) {
                total += value;
                found = true;
            }
        }
        return found ? total : null;
    }

    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Long longValue) {
            return longValue;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    private Long asNullableLong(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isIntegralNumber()) {
            return node.asLong();
        }
        if (node.isNumber()) {
            return BigDecimal.valueOf(node.asDouble()).setScale(0, RoundingMode.HALF_UP).longValue();
        }
        if (node.isTextual()) {
            try {
                return new BigDecimal(node.asText()).setScale(0, RoundingMode.HALF_UP).longValue();
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static class PodSummaryStats {
        private final Long memoryBytes;
        private final Long networkInBytes;
        private final Long networkOutBytes;

        private PodSummaryStats(Long memoryBytes, Long networkInBytes, Long networkOutBytes) {
            this.memoryBytes = memoryBytes;
            this.networkInBytes = networkInBytes;
            this.networkOutBytes = networkOutBytes;
        }

        private Long getMemoryBytes() {
            return memoryBytes;
        }

        private Long getNetworkInBytes() {
            return networkInBytes;
        }

        private Long getNetworkOutBytes() {
            return networkOutBytes;
        }
    }

    private boolean isMetricsApiAvailable(KubernetesClient client) {
        try {
            return kubernetesMetricsApiService.check(client)
                    == KubernetesMetricsApiService.Availability.AVAILABLE;
        } catch (Exception e) {
            log.warn("Unable to query metrics.k8s.io API. Continuing without CPU/memory metrics: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * K8S 오토스케일링 처리 (스케일 아웃만)
     */
    private boolean isWorkloadRebalancingEnabled(DeploymentHistory deployment) {
        return Boolean.TRUE.equals(deployment.getWorkloadRebalancingEnabled());
    }

    private void handleK8sAutoscaling(DeploymentHistory deployment, ApplicationStatus status, KubernetesClient client) {
        try {
            log.debug("=== Starting autoscaling check for deployment: {} ===", deployment.getId());
            
            // K8S 배포가 아니면 스킵
            if (!DeploymentType.K8S.equals(deployment.getDeploymentType())) {
                log.debug("Skipping autoscaling: not a K8S deployment");
                return;
            }
            
            log.debug("Resource usage - CPU: {}%, Memory: {}%", status.getCpuUsage(), status.getMemoryUsage());
            
            String namespace = deployment.getNamespace();
            String clusterName = deployment.getClusterName();
            
            // 노드 그룹 이름 동적 조회
            String nodeGroupName = getNodeGroupName(deployment);
            
            // 노드 그룹 이름이 없으면 오토스케일링 불가
            if (StringUtils.isBlank(nodeGroupName)) {
                log.warn("Cannot perform autoscaling: node group name is null or empty for deployment: {}", deployment.getId());
                return;
            }
            
            // 이미 진행 중인 스케일링 이벤트가 있는지 확인
            List<ScalingEvent> pendingEvents = scalingEventRepository.findPendingScalingEvents(deployment.getId());
            if (!pendingEvents.isEmpty()) {
                // 진행 중인 스케일링 이벤트가 있으면, 노드가 생성되었는지 확인하고 재배포
                ScalingEvent latestPendingEvent = pendingEvents.get(0);
                int targetNodeCount = latestPendingEvent.getNewNodeCount();
                var group = findRebalancingNodeGroup(deployment, nodeGroupName);
                int actualNodeCount = group == null ? 0 : group.getEffectiveNodes().size();
                List<Node> readyWorkers = group == null ? List.of() : client.nodes().list().getItems().stream()
                        .filter(this::isReadyWorker)
                        .filter(node -> belongsToNodeGroup(node, group))
                        .toList();
                int readyNodeCount = readyWorkers.size();
                
                log.info("Scaling event in progress: target={}, actual={}, ready={}", targetNodeCount, actualNodeCount, readyNodeCount);
                
                // 노드가 목표 노드 수에 도달했으면 재배포
                if (actualNodeCount >= targetNodeCount && readyNodeCount >= targetNodeCount) {
                    log.info("Target nodes created ({}/{}). Proceeding with redeployment.", actualNodeCount, targetNodeCount);
                    
                    try {
                        if (!reconcileRebalancingRollout(deployment, latestPendingEvent, client, readyWorkers)) {
                            if (rebalancingTimedOut(latestPendingEvent)) {
                                clearTimedOutPlacement(deployment, latestPendingEvent, client);
                            }
                            failRebalancingIfTimedOut(latestPendingEvent, "Timed out waiting for the rebalanced Pods to become Ready.");
                            return;
                        }
                        latestPendingEvent.setStatus(ScalingEvent.ScalingStatus.COMPLETED);
                        latestPendingEvent.setCompletedAt(LocalDateTime.now());
                        scalingEventRepository.save(latestPendingEvent);
                        sendScaleOutCompletedAlert(deployment, targetNodeCount);
                    } catch (Exception e) {
                        log.error("Error redeploying to new nodes: {}", e.getMessage(), e);
                        failRebalancingIfTimedOut(latestPendingEvent, "Could not apply workload rebalancing to the expanded node group.");
                    }
                } else {
                    log.info("Waiting for nodes to be created ({}/{})...", actualNodeCount, targetNodeCount);
                    failRebalancingIfTimedOut(latestPendingEvent, "Timed out waiting for the expanded node group and Ready workers.");
                }
                return; // 진행 중인 이벤트가 있으면 새 스케일링 요청하지 않음
            }
            if (status.getCpuUsage() == null || status.getMemoryUsage() == null) return;
            // Use the same thresholds for the current check and the stored samples.
            boolean exceeded = isThresholdExceeded(status.getCpuUsage(), getAutoscaleCpuThreshold(deployment))
                    || isThresholdExceeded(status.getMemoryUsage(), getAutoscaleMemoryThreshold(deployment));
            if (!hasSustainedAutoscalePressure(deployment.getId(), exceeded)
                    || findRebalancingDeployment(deployment, client) == null) return;

            var group = findRebalancingNodeGroup(deployment, nodeGroupName);
            if (group == null) return;
            int current = group.getEffectiveNodes().size();
            int desired = group.getDesiredNodeSize();
            int maxNodes = group.getMaxNodeSize() > 0 ? Math.min(defaultMaxNodes, group.getMaxNodeSize()) : defaultMaxNodes;
            // The HPA replica cap is not a worker-node cap. Also wait for a preceding
            // node request to settle before asking the provider to expand again.
            if (desired < 1 || current != desired || desired >= maxNodes
                    || !scalingEventRepository.findPendingScalingEventsByCluster(namespace, clusterName).isEmpty()) return;
            var lastEvent = scalingEventRepository.findTopByDeploymentIdOrderByTriggeredAtDesc(deployment.getId());
            if (lastEvent.filter(last -> last.getStatus() == ScalingEvent.ScalingStatus.FAILED
                    && !rebalancingTimedOut(last)).isPresent()) return;
            ScalingEvent event = createScalingEvent(deployment, nodeGroupName, current, desired + 1);
            if (event == null) return;
            scalingEventRepository.save(event);
            try {
                var sizes = new K8sNodeGroupAutoscaleRequest(String.valueOf(desired + 1),
                        group.isOnAutoScaling() ? String.valueOf(group.getMaxNodeSize() > 0 ? group.getMaxNodeSize() : maxNodes) : null,
                        group.isOnAutoScaling() ? String.valueOf(group.getMinNodeSize()) : null);
                boolean accepted = k8sAutoscaleService.changeNodeGroupAutoscaleSize(namespace, clusterName, nodeGroupName, sizes);
                event.setStatus(accepted ? ScalingEvent.ScalingStatus.IN_PROGRESS : ScalingEvent.ScalingStatus.FAILED);
                if (!accepted) event.setErrorMessage("Failed to initiate scale out via API.");
            } catch (Exception e) {
                log.error("Failed to request a new worker for deployment {}", deployment.getId(), e);
                event.setStatus(ScalingEvent.ScalingStatus.FAILED);
                event.setErrorMessage("Failed to initiate scale out via API.");
            }
            scalingEventRepository.save(event);
            
        } catch (Exception e) {
            log.error("Error handling K8S autoscaling for deployment: {}", deployment.getId(), e);
        }
    }
    
    /**
     * Confirms that resource pressure is sustained, not just a short spike.
     */
    private boolean hasSustainedAutoscalePressure(Long deploymentId, boolean currentThresholdExceeded) {
        if (!currentThresholdExceeded) {
            return false;
        }

        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusSeconds(getAutoscaleEvaluationWindowSeconds());
        List<AutoscaleCheckSample> samples = autoscaleCheckSampleRepository
                .findByDeploymentIdAndCheckedAtBetweenOrderByCheckedAtAsc(deploymentId, start, end);

        int sampleIntervalSeconds = getAutoscaleSampleIntervalSeconds();
        int expectedSamples = Math.max(1, getAutoscaleEvaluationWindowSeconds() / sampleIntervalSeconds);
        int requiredSamples = Math.max(1, (int) Math.ceil(expectedSamples * normalizeRatio(autoscaleMinSampleCoverageRatio)));

        if (samples.size() < requiredSamples) {
            log.info("Autoscale pressure not confirmed: deployment={}, samples={}/{} in {} seconds",
                    deploymentId, samples.size(), requiredSamples, getAutoscaleEvaluationWindowSeconds());
            return false;
        }

        long exceededSamples = samples.stream()
                .filter(sample -> Boolean.TRUE.equals(sample.getThresholdExceeded()))
                .count();
        double exceedanceRatio = samples.isEmpty() ? 0.0 : (double) exceededSamples / samples.size();
        int longestExceededRun = calculateLongestExceededRun(samples);
        int requiredExceededRun = Math.max(1,
                (int) Math.ceil((double) getAutoscaleRequiredConsecutiveExceedanceSeconds() / sampleIntervalSeconds));
        boolean sustained = exceedanceRatio >= normalizeRatio(autoscaleRequiredExceedanceRatio)
                && longestExceededRun >= requiredExceededRun;

        log.info("Autoscale pressure evaluation: deployment={}, sustained={}, samples={}, exceeded={}, ratio={}, longestRun={}, requiredRun={}",
                deploymentId, sustained, samples.size(), exceededSamples,
                Math.round(exceedanceRatio * 100.0) / 100.0, longestExceededRun, requiredExceededRun);
        return sustained;
    }

    private int calculateLongestExceededRun(List<AutoscaleCheckSample> samples) {
        int longestRun = 0;
        int currentRun = 0;

        for (AutoscaleCheckSample sample : samples) {
            if (Boolean.TRUE.equals(sample.getThresholdExceeded())) {
                currentRun++;
                longestRun = Math.max(longestRun, currentRun);
            } else {
                currentRun = 0;
            }
        }

        return longestRun;
    }

    private int getAutoscaleSampleIntervalSeconds() {
        return Math.max(1, autoscaleSampleIntervalSeconds);
    }

    private int getAutoscaleEvaluationWindowSeconds() {
        return Math.max(getAutoscaleSampleIntervalSeconds(), autoscaleEvaluationWindowSeconds);
    }

    private int getAutoscaleRequiredConsecutiveExceedanceSeconds() {
        return Math.max(getAutoscaleSampleIntervalSeconds(), autoscaleRequiredConsecutiveExceedanceSeconds);
    }

    private double normalizeRatio(double ratio) {
        return Math.max(0.0, Math.min(1.0, ratio));
    }

    private void cleanupOldAutoscaleSamplesIfDue() {
        LocalDateTime now = LocalDateTime.now();
        if (lastAutoscaleSampleCleanupAt != null && now.isBefore(lastAutoscaleSampleCleanupAt.plusHours(1))) {
            return;
        }

        LocalDateTime cutoff = now.minusHours(Math.max(1, autoscaleSampleRetentionHours));
        int deletedCount = autoscaleCheckSampleRepository.deleteByCheckedAtBefore(cutoff);
        lastAutoscaleSampleCleanupAt = now;
        log.debug("Cleaned up old autoscale samples: cutoff={}, deleted={}", cutoff, deletedCount);
    }

    private K8sClusterDto.NodeGroup findRebalancingNodeGroup(DeploymentHistory deployment, String name) {
        K8sClusterDto cluster = cbtumblebugRestApi.getK8sClusterByName(deployment.getNamespace(), deployment.getClusterName());
        if (cluster == null) return null;
        return cluster.getEffectiveNodeGroups().stream()
                .filter(group -> name.equals(group.getEffectiveName()))
                .findFirst().orElse(null);
    }

    private boolean belongsToNodeGroup(Node node, K8sClusterDto.NodeGroup group) {
        String name = node.getMetadata().getName();
        String providerId = node.getSpec().getProviderID();
        return group.getEffectiveNodes().stream().anyMatch(id -> name.equals(id.getNameId())
                || name.equals(id.getSystemId())
                || (StringUtils.isNotBlank(providerId) && StringUtils.isNotBlank(id.getSystemId())
                    && (providerId.equals(id.getSystemId()) || providerId.endsWith("/" + id.getSystemId()))));
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
            String recipients = defaultSlackChannelId;
            if (StringUtils.isNotBlank(recipients)) {
                boolean sent = rabbitMqAlertService.sendScaleOutAlert(title, message, "slack", recipients);
                if (sent) {
                    log.info("Scale out alert (slack) sent successfully for {} to {} nodes", appName, newNodeCount);
                } else {
                    log.warn("Failed to send scale out alert (slack) for {}", appName);
                }
            }

            // Email
            recipients = defaultEmail;
            if (StringUtils.isNotBlank(recipients)) {
                boolean sent = rabbitMqAlertService.sendScaleOutAlert(title, message, "email", recipients);
                if (sent) {
                    log.info("Scale out alert (email) sent successfully for {} to {} nodes", appName, newNodeCount);
                } else {
                    log.warn("Failed to send scale out alert (email) for {}", appName);
                }
            }

            // SMS - 글자제약으로 메시지 내용 축약
            recipients = defaultSmsPhonenumber;
            String smsTitle = String.format("Scale-out OK. %s", appName);
            String smsMessage = String.format("Node: %d, Cluster: '%s', NS: %s",
                    newNodeCount, clusterName, namespace
            );
            if (StringUtils.isNotBlank(recipients)) {
                boolean sent = rabbitMqAlertService.sendScaleOutAlert(smsTitle, smsMessage, "sms", recipients);
                if (sent) {
                    log.info("Scale out alert (sms) sent successfully for {} to {} nodes", appName, newNodeCount);
                } else {
                    log.warn("Failed to send scale out alert (sms) for {}", appName);
                }
            }

            // kako
            recipients = defaultKakaoPhonenumber;
            if (StringUtils.isNotBlank(recipients)) {
                boolean sent = rabbitMqAlertService.sendScaleOutAlert(title, message, "kakao", recipients);
                if (sent) {
                    log.info("Scale out alert (kakao) sent successfully for {} to {} nodes", appName, newNodeCount);
                } else {
                    log.warn("Failed to send scale out alert (kakao) for {}", appName);
                }
            }

        } catch (Exception e) {
            log.error("Error sending scale out alert: {}", e.getMessage(), e);
        }
    }

    private Deployment findRebalancingDeployment(DeploymentHistory deployment, KubernetesClient client) {
        String release = deployment.getReleaseName();
        if (StringUtils.isBlank(release)) return null;
        var workloads = client.apps().deployments().inNamespace(DEFAULT_WORKLOAD_NAMESPACE);
        List<Deployment> matches = workloads.withLabel(HELM_INSTANCE_LABEL, release).list().getItems();
        if (matches.size() == 1) return matches.get(0);
        return matches.isEmpty() ? workloads.withName(release).get() : null;
    }

    private boolean rebalancingTimedOut(ScalingEvent event) {
        return Duration.between(event.getTriggeredAt(), LocalDateTime.now()).getSeconds() > scalingTimeoutSeconds;
    }

    private void failRebalancingIfTimedOut(ScalingEvent event, String message) {
        if (rebalancingTimedOut(event)) {
            event.setStatus(ScalingEvent.ScalingStatus.FAILED);
            event.setErrorMessage(message);
            scalingEventRepository.save(event);
        }
    }

    private String rebalancingLabelKey(DeploymentHistory deployment) {
        return "mcmp.io/rebalance-" + deployment.getId();
    }

    private void clearTimedOutPlacement(DeploymentHistory deployment, ScalingEvent event, KubernetesClient client) {
        Deployment workload = findRebalancingDeployment(deployment, client);
        if (workload == null) return;
        String labelKey = rebalancingLabelKey(deployment);
        Map<String, String> selector = workload.getSpec().getTemplate().getSpec().getNodeSelector();
        if (selector != null && ("event-" + event.getId()).equals(selector.get(labelKey))) {
            patchRebalancingPlacement(client, workload, labelKey, null);
        }
    }

    private void patchRebalancingPlacement(KubernetesClient client, Deployment workload, String labelKey, String group) {
        // Patch only the Pod template. Do not replace replicas managed concurrently by HPA.
        var resource = client.apps().deployments().inNamespace(DEFAULT_WORKLOAD_NAMESPACE)
                .withName(workload.getMetadata().getName());
        if (group == null) {
            String path = "/spec/template/spec/nodeSelector/" + labelKey.replace("~", "~0").replace("/", "~1");
            var operations = OBJECT_MAPPER.createArrayNode();
            operations.addObject().put("op", "test").put("path", path)
                    .put("value", workload.getSpec().getTemplate().getSpec().getNodeSelector().get(labelKey));
            operations.addObject().put("op", "remove").put("path", path);
            resource.patch(PatchContext.of(PatchType.JSON), operations.toString());
            return;
        }
        var patch = OBJECT_MAPPER.createObjectNode();
        var template = patch.putObject("spec").putObject("template");
        var selector = template.putObject("spec").putObject("nodeSelector");
        selector.put(labelKey, group);
        var existing = workload.getSpec().getTemplate().getSpec().getNodeSelector();
        if (existing != null && existing.containsKey("autoscale-group")) selector.putNull("autoscale-group");
        resource.patch(PatchContext.of(PatchType.JSON_MERGE), patch.toString());
    }
    
    private boolean isReadyWorker(Node node) {
        return node.getSpec() != null && !Boolean.TRUE.equals(node.getSpec().getUnschedulable())
                && !Objects.requireNonNullElse(node.getMetadata().getLabels(), Map.of()).containsKey("node-role.kubernetes.io/control-plane")
                && !Objects.requireNonNullElse(node.getMetadata().getLabels(), Map.of()).containsKey("node-role.kubernetes.io/master")
                && node.getStatus() != null && node.getStatus().getConditions() != null
                && node.getStatus().getConditions().stream()
                    .anyMatch(condition -> "Ready".equals(condition.getType()) && "True".equals(condition.getStatus()));
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
    
    private boolean reconcileRebalancingRollout(DeploymentHistory deployment, ScalingEvent event,
            KubernetesClient client, List<Node> readyWorkers) {
        Deployment workload = findRebalancingDeployment(deployment, client);
        if (workload == null) throw new IllegalStateException("Could not identify the workload for rebalancing.");
        String labelKey = rebalancingLabelKey(deployment);
        String labelValue = "event-" + event.getId();
        Map<String, String> selector = workload.getSpec().getTemplate().getSpec().getNodeSelector();
        if (selector != null && labelValue.equals(selector.get(labelKey))) {
            return isRebalancingRolloutReady(workload);
        }
        Node worker = readyWorkers.stream()
                .filter(node -> node.getMetadata().getCreationTimestamp() != null)
                .filter(node -> !Instant.parse(node.getMetadata().getCreationTimestamp())
                        .isBefore(event.getTriggeredAt().atZone(java.time.ZoneId.systemDefault()).toInstant().minusSeconds(1)))
                .max(Comparator.comparing(node -> node.getMetadata().getCreationTimestamp()))
                .orElseThrow(() -> new IllegalStateException("No Ready worker is available for rebalancing."));
        // A deployment-specific label avoids changing the placement of other applications.
        var nodePatch = OBJECT_MAPPER.createObjectNode();
        nodePatch.putObject("metadata").putObject("labels").put(labelKey, labelValue);
        client.nodes().withName(worker.getMetadata().getName())
                .patch(PatchContext.of(PatchType.JSON_MERGE), nodePatch.toString());
        // Deployment rollout handles replacement. Never delete all existing Pods at once.
        patchRebalancingPlacement(client, workload, labelKey, labelValue);
        return false;
    }

    static boolean isRebalancingRolloutReady(Deployment workload) {
        var status = workload.getStatus();
        int replicas = Objects.requireNonNullElse(workload.getSpec().getReplicas(), 1);
        return status != null && replicas > 0
                && status.getObservedGeneration() != null && workload.getMetadata().getGeneration() != null
                && status.getObservedGeneration() >= workload.getMetadata().getGeneration()
                && Objects.requireNonNullElse(status.getUpdatedReplicas(), 0) == replicas
                && Objects.requireNonNullElse(status.getReadyReplicas(), 0) == replicas
                && Objects.requireNonNullElse(status.getReplicas(), 0) == replicas;
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
            
            List<K8sClusterDto.NodeGroup> nodeGroups = clusterInfo.getEffectiveNodeGroups();
            log.info("Resolved {} node groups from the Tumblebug cluster response", nodeGroups.size());
            
            if (!nodeGroups.isEmpty()) {
                log.info("Found {} node groups in cluster:", nodeGroups.size());
                for (K8sClusterDto.NodeGroup ng : nodeGroups) {
                    log.info("  - NodeGroup: {}, AutoScaling: {}", 
                            ng.getEffectiveName(), ng.isOnAutoScaling());
                }
                
                // 적절한 노드 그룹 선택 (예: 오토스케일링이 활성화된 그룹)
                K8sClusterDto.NodeGroup selectedNodeGroup = selectAppropriateNodeGroup(nodeGroups);
                
                if (selectedNodeGroup != null) {
                    String nodeGroupName = selectedNodeGroup.getEffectiveName();
                    
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
                log.info("Selected auto-scaling enabled node group: {}", nodeGroup.getEffectiveName());
                return nodeGroup;
            }
        }
        
        // 2. 오토스케일링이 활성화된 그룹이 없으면 첫 번째 그룹 선택
        if (!nodeGroups.isEmpty()) {
            log.info("No auto-scaling enabled node group found, using first group: {}", 
                    nodeGroups.get(0).getEffectiveName());
            return nodeGroups.get(0);
        }
        
        return null;
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

            if (clusterInfo != null) {
                List<K8sClusterDto.NodeGroup> nodeGroups = clusterInfo.getEffectiveNodeGroups();

                if (!nodeGroups.isEmpty()) {
                    for (K8sClusterDto.NodeGroup nodeGroup : nodeGroups) {
                        String groupName = nodeGroup.getEffectiveName();
                        if (event.getNodeGroupName().equals(groupName)) {
                            // 노드 목록에서 새로 생성된 노드 필터링
                            List<K8sClusterDto.IID> allNodes = nodeGroup.getEffectiveNodes();
                            if (!allNodes.isEmpty()) {
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
