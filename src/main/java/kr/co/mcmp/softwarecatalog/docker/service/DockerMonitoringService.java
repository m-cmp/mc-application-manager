package kr.co.mcmp.softwarecatalog.docker.service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.co.mcmp.softwarecatalog.SoftwareCatalog;
import kr.co.mcmp.softwarecatalog.application.constants.DeploymentType;
import kr.co.mcmp.softwarecatalog.application.model.ApplicationStatus;
import kr.co.mcmp.softwarecatalog.application.model.DeploymentHistory;
import kr.co.mcmp.softwarecatalog.application.model.LifecycleEvent;
import kr.co.mcmp.softwarecatalog.application.model.ResourceMetricsHistory;
import kr.co.mcmp.softwarecatalog.application.repository.ApplicationStatusRepository;
import kr.co.mcmp.softwarecatalog.application.repository.DeploymentHistoryRepository;
import kr.co.mcmp.softwarecatalog.application.repository.LifecycleEventRepository;
import kr.co.mcmp.softwarecatalog.application.repository.ResourceMetricsHistoryRepository;
import kr.co.mcmp.softwarecatalog.docker.model.ContainerHealthInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class DockerMonitoringService {

    private final ApplicationStatusRepository applicationStatusRepository;
    private final DeploymentHistoryRepository deploymentHistoryRepository;
    private final ResourceMetricsHistoryRepository metricsHistoryRepository;
    private final LifecycleEventRepository lifecycleEventRepository;
    private final DockerClientFactory dockerClientFactory;
    private final ContainerStatsCollector containerStatsCollector;
    private final DockerLogCollector dockerLogCollector;

    @Value("${docker.monitoring.interval:60000}")
    private long monitoringInterval;

    /** Last snapshot timestamp per deployment ID — controls the 10-minute interval */
    private final Map<Long, LocalDateTime> lastSnapshotTime = new ConcurrentHashMap<>();

    /** Last observed restart count per deployment ID — detects restart count increases */
    private final Map<Long, Integer> lastRestartCount = new ConcurrentHashMap<>();

    @Scheduled(fixedRateString = "${docker.monitoring.interval:60000}")
    @Transactional
    public void monitorContainerHealth() {
        log.info("Starting container health monitoring");
        List<DeploymentHistory> activeDeployments = getActiveDeployments();

        for (DeploymentHistory deployment : activeDeployments) {
            try {
                updateContainerHealth(deployment);
            } catch (Exception e) {
                log.error("Error monitoring container for deployment: {}", deployment.getId(), e);
            }
        }
        log.info("Container health monitoring completed");
    }

    private List<DeploymentHistory> getActiveDeployments() {
        return deploymentHistoryRepository.findAll().stream()
                .filter(d -> DeploymentType.VM.equals(d.getDeploymentType()))
                .collect(Collectors.groupingBy(d -> d.getVmId() != null ? d.getVmId() : d.getMciId()))
                .values().stream()
                .flatMap(deployments -> deployments.stream()
                        .collect(Collectors.groupingBy(DeploymentHistory::getCatalog))
                        .values().stream()
                        .map(catalogDeployments -> catalogDeployments.stream()
                                .max(Comparator.comparing(DeploymentHistory::getExecutedAt))
                                .orElse(null)))
                .filter(Objects::nonNull)
                .filter(d -> ("RUN".equalsIgnoreCase(d.getActionType().name()) || "INSTALL".equalsIgnoreCase(d.getActionType().name())) && "SUCCESS".equalsIgnoreCase(d.getStatus()))
                .collect(Collectors.toList());
    }

    private void updateContainerHealth(DeploymentHistory deployment) {
        log.info("Updating container health for deployment: {} (VM: {})", deployment.getId(), deployment.getVmId());

        ApplicationStatus status = applicationStatusRepository.findByCatalogIdAndVmId(
                deployment.getCatalog().getId(), deployment.getVmId()).orElse(new ApplicationStatus());

        try (var dockerClient = dockerClientFactory.getDockerClient(deployment.getPublicIp())) {
            log.info("Docker client connected successfully to: {}", deployment.getPublicIp());

            String catalogName = deployment.getCatalog().getName().toLowerCase().replaceAll("\\s+", "-");
            String containerId = containerStatsCollector.getContainerId(dockerClient, catalogName);

            if (containerId == null) {
                log.warn("Container not found for catalog: {} on VM: {}", catalogName, deployment.getVmId());
                status.setStatus("NOT_FOUND");
                status.setCheckedAt(LocalDateTime.now());
                applicationStatusRepository.save(status);
                return;
            }

            ContainerHealthInfo healthInfo = containerStatsCollector.collectContainerStats(dockerClient, containerId);
            log.info("Health info collected - Status: {}, CPU: {}%, Memory: {}%, OOM: {}, Restarts: {}",
                    healthInfo.getStatus(), healthInfo.getCpuUsage(), healthInfo.getMemoryUsage(),
                    healthInfo.getOomKilled(), healthInfo.getRestartCount());

            // Update ApplicationStatus with current values
            updateApplicationStatus(status, deployment, healthInfo);

            // Persist 10-minute snapshot if interval has elapsed
            saveMetricsSnapshotIfDue(deployment, healthInfo);

            // Detect and persist lifecycle events
            detectAndSaveLifecycleEvents(deployment, healthInfo);

            if (isThresholdExceeded(deployment.getCatalog(), healthInfo)) {
                log.warn("Resource thresholds exceeded for deployment: {}", deployment.getId());
                dockerLogCollector.collectAndSaveLogs(deployment.getId(), deployment.getVmId(), containerId);
            }

            applicationStatusRepository.save(status);
            log.info("ApplicationStatus updated successfully for deployment: {}", deployment.getId());
        } catch (Exception e) {
            log.error("Failed to update container health for deployment: {} (VM: {})", deployment.getId(), deployment.getVmId(), e);
            status.setStatus("ERROR");
            status.setCheckedAt(LocalDateTime.now());
            applicationStatusRepository.save(status);
        }
    }

    /**
     * Saves a snapshot to resource_metrics_history only if 10 minutes have elapsed since the last one.
     */
    private void saveMetricsSnapshotIfDue(DeploymentHistory deployment, ContainerHealthInfo healthInfo) {
        Long deploymentId = deployment.getId();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime last = lastSnapshotTime.get(deploymentId);

        if (last != null && now.isBefore(last.plusMinutes(10))) {
            return;
        }

        try {
            ResourceMetricsHistory snapshot = ResourceMetricsHistory.builder()
                    .deploymentId(deploymentId)
                    .recordedAt(now)
                    .cpuUsagePct(healthInfo.getCpuUsage())
                    .memoryUsagePct(healthInfo.getMemoryUsage())
                    .memoryLimitBytes(healthInfo.getMemoryLimitBytes())
                    .networkInBytes(healthInfo.getNetworkIn() != null ? healthInfo.getNetworkIn().longValue() : null)
                    .networkOutBytes(healthInfo.getNetworkOut() != null ? healthInfo.getNetworkOut().longValue() : null)
                    .restartCount(healthInfo.getRestartCount())
                    .oomKilled(Boolean.TRUE.equals(healthInfo.getOomKilled()))
                    .status(healthInfo.getStatus())
                    .resourceType(deployment.getResourceType())
                    .deploymentType("DOCKER")
                    .build();

            metricsHistoryRepository.save(snapshot);
            lastSnapshotTime.put(deploymentId, now);
            log.debug("Saved metrics snapshot for deployment: {}", deploymentId);
        } catch (Exception e) {
            log.error("Failed to save metrics snapshot for deployment: {}", deploymentId, e);
        }
    }

    /**
     * Detects and saves OOM Killed and restart surge lifecycle events.
     */
    private void detectAndSaveLifecycleEvents(DeploymentHistory deployment, ContainerHealthInfo healthInfo) {
        Long deploymentId = deployment.getId();
        LocalDateTime now = LocalDateTime.now();

        // OOM Killed detection
        if (Boolean.TRUE.equals(healthInfo.getOomKilled())) {
            try {
                lifecycleEventRepository.save(LifecycleEvent.builder()
                        .deploymentId(deploymentId)
                        .eventType("OOM_KILLED")
                        .severity("CRITICAL")
                        .occurredAt(now)
                        .detailMessage("Container OOM killed on VM: " + deployment.getVmId())
                        .createdAt(now)
                        .build());
                log.warn("OOM_KILLED event recorded for deployment: {}", deploymentId);
            } catch (Exception e) {
                log.error("Failed to save OOM_KILLED event for deployment: {}", deploymentId, e);
            }
        }

        // Restart surge detection (increase of +1 or more compared to previous count)
        Integer currentRestarts = healthInfo.getRestartCount();
        if (currentRestarts != null) {
            Integer prevRestarts = lastRestartCount.get(deploymentId);
            if (prevRestarts != null && currentRestarts > prevRestarts) {
                int increment = currentRestarts - prevRestarts;
                String severity = increment >= 3 ? "CRITICAL" : "WARNING";
                String eventType = increment >= 3 ? "CRASH_LOOP" : "RESTART";
                try {
                    lifecycleEventRepository.save(LifecycleEvent.builder()
                            .deploymentId(deploymentId)
                            .eventType(eventType)
                            .severity(severity)
                            .occurredAt(now)
                            .detailMessage(String.format("Restart count increased by %d (total: %d)", increment, currentRestarts))
                            .createdAt(now)
                            .build());
                    log.warn("{} event recorded for deployment: {}, increment={}", eventType, deploymentId, increment);
                } catch (Exception e) {
                    log.error("Failed to save {} event for deployment: {}", eventType, deploymentId, e);
                }
            }
            lastRestartCount.put(deploymentId, currentRestarts);
        }
    }

    private boolean isThresholdExceeded(SoftwareCatalog catalog, ContainerHealthInfo healthInfo) {
        if (healthInfo.getCpuUsage() != null && healthInfo.getMemoryUsage() != null) {
            boolean cpuExceeded = catalog.getCpuThreshold() != null && healthInfo.getCpuUsage() > catalog.getCpuThreshold();
            boolean memoryExceeded = catalog.getMemoryThreshold() != null && healthInfo.getMemoryUsage() > catalog.getMemoryThreshold();
            return cpuExceeded || memoryExceeded;
        }
        return false;
    }

    private void updateApplicationStatus(ApplicationStatus status, DeploymentHistory deployment, ContainerHealthInfo healthInfo) {
        status.setCatalog(deployment.getCatalog());
        status.setStatus(healthInfo.getStatus());
        status.setDeploymentType(deployment.getDeploymentType());
        status.setNamespace(deployment.getNamespace());
        status.setMciId(deployment.getMciId());
        status.setIsPortAccessible(healthInfo.getIsPortAccess());
        status.setIsHealthCheck(healthInfo.getIsHealthCheck());
        status.setVmId(deployment.getVmId());
        status.setClusterName(deployment.getClusterName());
        status.setCheckedAt(LocalDateTime.now());
        status.setServicePort(healthInfo.getServicePorts());
        status.setPublicIp(deployment.getPublicIp());
        status.setCpuUsage(healthInfo.getCpuUsage());
        status.setMemoryUsage(healthInfo.getMemoryUsage());
        status.setNetworkIn(healthInfo.getNetworkIn());
        status.setNetworkOut(healthInfo.getNetworkOut());
        status.setExecutedBy(deployment.getExecutedBy() != null ? deployment.getExecutedBy() : null);
    }
}
