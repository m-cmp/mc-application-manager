package kr.co.mcmp.softwarecatalog.application.service.impl;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.github.dockerjava.api.DockerClient;

import kr.co.mcmp.softwarecatalog.application.constants.ActionType;
import kr.co.mcmp.softwarecatalog.application.constants.DeploymentType;
import kr.co.mcmp.softwarecatalog.application.constants.ApplicationStatusValues;
import kr.co.mcmp.softwarecatalog.application.model.ApplicationStatus;
import kr.co.mcmp.softwarecatalog.application.model.DeploymentHistory;
import kr.co.mcmp.softwarecatalog.application.repository.ApplicationStatusRepository;
import kr.co.mcmp.softwarecatalog.application.repository.DeploymentHistoryRepository;
import kr.co.mcmp.softwarecatalog.application.service.ApplicationHistoryService;
import kr.co.mcmp.softwarecatalog.application.service.ApplicationOperationService;
import kr.co.mcmp.softwarecatalog.docker.service.ContainerStatsCollector;
import kr.co.mcmp.softwarecatalog.docker.service.DockerClientFactory;
import kr.co.mcmp.softwarecatalog.docker.service.DockerOperationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Docker 애플리케이션 운영을 담당하는 서비스
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DockerApplicationOperationService implements ApplicationOperationService {
    
    private static final List<ActionType> ACTIVE_DEPLOYMENT_ACTIONS = Arrays.asList(ActionType.INSTALL, ActionType.RUN);
    private static final List<String> ACTIVE_DEPLOYMENT_STATUSES = Arrays.asList("SUCCESS", "RUNNING");

    private final ApplicationStatusRepository applicationStatusRepository;
    private final DeploymentHistoryRepository deploymentHistoryRepository;
    private final DockerClientFactory dockerClientFactory;
    private final DockerOperationService dockerOperationService;
    private final ContainerStatsCollector containerStatsCollector;
    private final ApplicationHistoryService applicationHistoryService;
    
    @Override
    public Map<String, Object> performOperation(ActionType operation, Long applicationStatusId, String reason, String username) {
        ApplicationStatus applicationStatus = applicationStatusRepository.findById(applicationStatusId)
            .orElseThrow(() -> new IllegalArgumentException("ApplicationStatus not found with id: " + applicationStatusId));
    
        Map<String, Object> result = new HashMap<>();
        result.put("operation", operation);
        result.put("applicationStatusId", applicationStatusId);
        result.put("success", false);
    
        try {
            String host = applicationStatus.getPublicIp();
            String containerName = applicationStatus.getCatalog().getName().toLowerCase().replaceAll("\\s+", "-");
            String containerId;

            try (DockerClient dockerClient = dockerClientFactory.getDockerClient(host)) {
                containerId = containerStatsCollector.getContainerId(dockerClient, containerName);
            }

            if (containerId == null) {
                if (ActionType.UNINSTALL.equals(operation)) {
                    result.put("result", "Container already removed");
                    result.put("success", true);
                    applicationHistoryService.insertOperationHistory(applicationStatus, username, reason, "Docker operation: " + operation.name(), operation);
                    updateApplicationStatus(applicationStatus, operation, result, username);
                    return result;
                }
                throw new IllegalStateException("Container ID is not available for application status: " + applicationStatusId);
            }

            switch (operation.toString().toLowerCase()) {
                case "status":
                    String status = dockerOperationService.getDockerContainerStatus(host, containerId);
                    result.put("status", status);
                    break;
                case "stop":
                    String stopResult = dockerOperationService.stopDockerContainer(host, containerId);
                    result.put("result", stopResult);
                    break;
                case "start":
                    String startResult = dockerOperationService.startDockerContainer(host, containerId);
                    result.put("result", startResult);
                    break;
                case "uninstall":
                    String removeResult = dockerOperationService.removeDockerContainer(host, containerId);
                    result.put("result", removeResult);
                    break;
                case "restart":
                    String restartResult = dockerOperationService.restartDockerContainer(host, containerId);
                    result.put("result", restartResult);
                    break;
                case "isrunning":
                    boolean isRunning = dockerOperationService.isContainerRunning(host, containerId);
                    result.put("isRunning", isRunning);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown operation: " + operation);
            }

            result.put("success", true);
            applicationHistoryService.insertOperationHistory(applicationStatus, username, reason, "Docker operation: " + operation.name(), operation);
            updateApplicationStatus(applicationStatus, operation, result, username);
    
        } catch (Exception e) {
            log.error("Error performing Docker operation: {} on application status: {}", operation, applicationStatusId, e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }
    
        return result;
    }
    
    @Override
    public DeploymentType getSupportedDeploymentType() {
        return DeploymentType.VM;
    }
    
    private void updateApplicationStatus(ApplicationStatus applicationStatus, ActionType operation, Map<String, Object> result, String username) {
        switch (operation.toString().toLowerCase()) {
            case "status":
                applicationStatus.setStatus((String) result.get("status"));
                break;
            case "stop":
                applicationStatus.setStatus(ActionType.STOP.name());
                break;
            case "start":
                applicationStatus.setStatus(ActionType.START.name());
                break;
            case "uninstall":
                applicationStatus.setStatus(ApplicationStatusValues.UNINSTALLED);
                markDeploymentHistoryAsUninstalled(applicationStatus);
                break;
            case "restart":
                applicationStatus.setStatus(ActionType.RESTART.name());
                break;
            case "isrunning":
                applicationStatus.setStatus((Boolean) result.get("isRunning") ? ActionType.RUN.name() : ActionType.STOP.name());
                break;
        }
        applicationStatus.setCheckedAt(java.time.LocalDateTime.now());
        applicationStatusRepository.save(applicationStatus);
    }

    private void markDeploymentHistoryAsUninstalled(ApplicationStatus applicationStatus) {
        DeploymentHistory deploymentHistory = null;

        if (applicationStatus.getDeploymentHistoryId() != null) {
            deploymentHistory = deploymentHistoryRepository.findById(applicationStatus.getDeploymentHistoryId()).orElse(null);
        }

        if (deploymentHistory == null && applicationStatus.getCatalog() != null && applicationStatus.getVmId() != null) {
            deploymentHistory = deploymentHistoryRepository
                    .findTopByCatalogIdAndVmIdAndActionTypeInAndStatusInOrderByExecutedAtDesc(
                            applicationStatus.getCatalog().getId(),
                            applicationStatus.getVmId(),
                            ACTIVE_DEPLOYMENT_ACTIONS,
                            ACTIVE_DEPLOYMENT_STATUSES)
                    .orElse(null);
        }

        if (deploymentHistory == null) {
            log.warn("No active deployment history found to mark uninstalled for applicationStatusId={}", applicationStatus.getId());
            return;
        }

        deploymentHistory.setStatus(ApplicationStatusValues.UNINSTALLED);
        deploymentHistory.setUpdatedAt(java.time.LocalDateTime.now());
        deploymentHistoryRepository.save(deploymentHistory);
    }
}


