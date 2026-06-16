package kr.co.mcmp.softwarecatalog.docker.service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.co.mcmp.softwarecatalog.SoftwareCatalog;
import kr.co.mcmp.softwarecatalog.application.constants.ActionType;
import kr.co.mcmp.softwarecatalog.application.constants.DeploymentType;
import kr.co.mcmp.softwarecatalog.application.constants.ApplicationStatusValues;
import kr.co.mcmp.softwarecatalog.application.model.ApplicationStatus;
import kr.co.mcmp.softwarecatalog.application.model.DeploymentHistory;
import kr.co.mcmp.softwarecatalog.application.model.AbnormalEvent;
import kr.co.mcmp.softwarecatalog.application.model.OperationHistory;
import kr.co.mcmp.softwarecatalog.application.model.ResourceMetricsHistory;
import kr.co.mcmp.softwarecatalog.application.repository.ApplicationStatusRepository;
import kr.co.mcmp.softwarecatalog.application.repository.DeploymentHistoryRepository;
import kr.co.mcmp.softwarecatalog.application.repository.AbnormalEventRepository;
import kr.co.mcmp.softwarecatalog.application.repository.OperationHistoryRepository;
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
    private final AbnormalEventRepository abnormalEventRepository;
    private final OperationHistoryRepository operationHistoryRepository;
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
                .collect(Collectors.groupingBy(this::vmDeploymentKey))
                .values().stream()
                .map(deployments -> deployments.stream()
                        .max(Comparator.comparing(DeploymentHistory::getExecutedAt))
                        .orElse(null))
                .filter(Objects::nonNull)
                .filter(d -> ("RUN".equalsIgnoreCase(d.getActionType().name()) || "INSTALL".equalsIgnoreCase(d.getActionType().name())) && "SUCCESS".equalsIgnoreCase(d.getStatus()))
                .collect(Collectors.toList());
    }

    private String vmDeploymentKey(DeploymentHistory deployment) {
        Long catalogId = deployment.getCatalog() != null ? deployment.getCatalog().getId() : null;
        return String.join("|",
                String.valueOf(catalogId),
                keyPart(deployment.getNamespace()),
                keyPart(deployment.getMciId()),
                keyPart(deployment.getVmId()));
    }

    private String keyPart(String value) {
        return value == null ? "" : value;
    }

    private void updateContainerHealth(DeploymentHistory deployment) {
        log.info("Updating container health for deployment: {} (VM: {})", deployment.getId(), deployment.getVmId());

        ApplicationStatus status = applicationStatusRepository
                .findByCatalogIdAndNamespaceAndMciIdAndVmId(
                        deployment.getCatalog().getId(),
                        deployment.getNamespace(),
                        deployment.getMciId(),
                        deployment.getVmId())
                .orElse(new ApplicationStatus());
        applyDeploymentIdentity(status, deployment);

        if (isEffectivelyUninstalled(status, deployment)) {
            markUninstalled(status, deployment);
            log.debug("Skipping Docker monitoring for uninstalled application status: {}", status.getId());
            return;
        }

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
            detectAndSaveAbnormalEvents(deployment, healthInfo);

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
                    .memoryBytes(healthInfo.getMemoryBytes())
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
    private void detectAndSaveAbnormalEvents(DeploymentHistory deployment, ContainerHealthInfo healthInfo) {
        Long deploymentId = deployment.getId();
        LocalDateTime now = LocalDateTime.now();

        // OOM Killed detection
        if (Boolean.TRUE.equals(healthInfo.getOomKilled())) {
            try {
                abnormalEventRepository.save(AbnormalEvent.builder()
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
                    abnormalEventRepository.save(AbnormalEvent.builder()
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
        applyDeploymentIdentity(status, deployment);
        status.setStatus(healthInfo.getStatus());
        status.setIsPortAccessible(healthInfo.getIsPortAccess());
        status.setIsHealthCheck(healthInfo.getIsHealthCheck());
        status.setCheckedAt(LocalDateTime.now());
        status.setServicePort(healthInfo.getServicePorts());
        status.setCpuUsage(healthInfo.getCpuUsage());
        status.setMemoryUsage(healthInfo.getMemoryUsage());
        status.setNetworkIn(healthInfo.getNetworkIn());
        status.setNetworkOut(healthInfo.getNetworkOut());
    }

    private void applyDeploymentIdentity(ApplicationStatus status, DeploymentHistory deployment) {
        status.setCatalog(deployment.getCatalog());
        status.setDeploymentType(deployment.getDeploymentType());
        status.setNamespace(deployment.getNamespace());
        status.setMciId(deployment.getMciId());
        status.setVmId(deployment.getVmId());
        status.setClusterName(deployment.getClusterName());
        status.setDeploymentHistoryId(deployment.getId());
        status.setPublicIp(deployment.getPublicIp());
        status.setExecutedBy(deployment.getExecutedBy() != null ? deployment.getExecutedBy() : null);
    }

    private boolean isEffectivelyUninstalled(ApplicationStatus status, DeploymentHistory deployment) {
        if (status == null) {
            return false;
        }
        if (ApplicationStatusValues.UNINSTALLED.equalsIgnoreCase(status.getStatus())) {
            return true;
        }
        if (status.getId() == null) {
            return false;
        }
        if (deployment == null || deployment.getId() == null) {
            return false;
        }

        Optional<OperationHistory> latestOperation = operationHistoryRepository
                .findTopByDeploymentHistoryIdOrderByCreatedAtDesc(deployment.getId());
        return latestOperation
                .map(operation -> ActionType.UNINSTALL.name().equalsIgnoreCase(operation.getOperationType()))
                .orElse(false);
    }

    private void markUninstalled(ApplicationStatus status, DeploymentHistory deployment) {
        if (!ApplicationStatusValues.UNINSTALLED.equalsIgnoreCase(status.getStatus())) {
            status.setStatus(ApplicationStatusValues.UNINSTALLED);
            status.setCheckedAt(LocalDateTime.now());
            applicationStatusRepository.save(status);
        }
        if (deployment != null && !ApplicationStatusValues.UNINSTALLED.equalsIgnoreCase(deployment.getStatus())) {
            deployment.setStatus(ApplicationStatusValues.UNINSTALLED);
            deployment.setUpdatedAt(LocalDateTime.now());
            deploymentHistoryRepository.save(deployment);
        }
    }
}
