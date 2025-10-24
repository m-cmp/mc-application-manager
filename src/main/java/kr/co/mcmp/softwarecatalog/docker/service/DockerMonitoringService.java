package kr.co.mcmp.softwarecatalog.docker.service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.co.mcmp.softwarecatalog.SoftwareCatalog;
import kr.co.mcmp.softwarecatalog.application.constants.DeploymentType;
import kr.co.mcmp.softwarecatalog.application.model.ApplicationStatus;
import kr.co.mcmp.softwarecatalog.application.model.DeploymentHistory;
import kr.co.mcmp.softwarecatalog.application.repository.ApplicationStatusRepository;
import kr.co.mcmp.softwarecatalog.application.repository.DeploymentHistoryRepository;
import kr.co.mcmp.softwarecatalog.docker.model.ContainerHealthInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class DockerMonitoringService {

    private final ApplicationStatusRepository applicationStatusRepository;
    private final DeploymentHistoryRepository deploymentHistoryRepository;
    private final DockerClientFactory dockerClientFactory;
    private final ContainerStatsCollector containerStatsCollector;
    private final DockerLogCollector dockerLogCollector;

    @Value("${docker.monitoring.interval:60000}")
    private long monitoringInterval;

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
        
        // VM별 ApplicationStatus를 찾기 위해 catalogId와 vmId로 검색
        ApplicationStatus status = applicationStatusRepository.findByCatalogIdAndVmId(
            deployment.getCatalog().getId(), deployment.getVmId()).orElse(new ApplicationStatus());

        try (var dockerClient = dockerClientFactory.getDockerClient(deployment.getPublicIp())) {
            log.info("Docker client connected successfully to: {}", deployment.getPublicIp());
            
            String catalogName = deployment.getCatalog().getName().toLowerCase().replaceAll("\\s+", "-");
            log.info("Looking for container with name pattern: {}", catalogName);
            
            String containerId = containerStatsCollector.getContainerId(dockerClient, catalogName);
            log.info("Found containerId: {}", containerId);

            if (containerId == null) {
                log.warn("Container not found for catalog: {} on VM: {}", catalogName, deployment.getVmId());
                status.setStatus("NOT_FOUND");
                status.setCheckedAt(LocalDateTime.now());
                applicationStatusRepository.save(status);
                return;
            }

            ContainerHealthInfo healthInfo = containerStatsCollector.collectContainerStats(dockerClient, containerId);
            log.info("Health info collected - Status: {}, CPU: {}%, Memory: {}%", 
                    healthInfo.getStatus(), healthInfo.getCpuUsage(), healthInfo.getMemoryUsage());
            
            updateApplicationStatus(status, deployment, healthInfo);
            
            if (isThresholdExceeded(deployment.getCatalog(), healthInfo)) {
                log.warn("Resource thresholds exceeded for deployment: {}", deployment.getId());
                // UnifiedLog를 사용하여 에러 로그 수집
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