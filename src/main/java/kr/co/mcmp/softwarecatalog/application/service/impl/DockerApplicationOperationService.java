package kr.co.mcmp.softwarecatalog.application.service.impl;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.github.dockerjava.api.DockerClient;

import kr.co.mcmp.softwarecatalog.application.constants.ActionType;
import kr.co.mcmp.softwarecatalog.application.constants.DeploymentType;
import kr.co.mcmp.softwarecatalog.application.dto.OperationRequest;
import kr.co.mcmp.softwarecatalog.application.dto.OperationResult;
import kr.co.mcmp.softwarecatalog.application.model.ApplicationStatus;
import kr.co.mcmp.softwarecatalog.application.repository.ApplicationStatusRepository;
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
    
    private final ApplicationStatusRepository applicationStatusRepository;
    private final DockerClientFactory dockerClientFactory;
    private final DockerOperationService dockerOperationService;
    private final ContainerStatsCollector containerStatsCollector;
    private final ApplicationHistoryService applicationHistoryService;
    
    @Override
    public Map<String, Object> performOperation(ActionType operation, Long applicationStatusId, String reason, String username) {
        ApplicationStatus applicationStatus = applicationStatusRepository.findById(applicationStatusId)
            .orElseThrow(() -> new IllegalArgumentException("ApplicationStatus not found with id: " + applicationStatusId));
    
        String host = applicationStatus.getPublicIp();
        DockerClient dockerClient = dockerClientFactory.getDockerClient(host);
        String containerName = applicationStatus.getCatalog().getName().toLowerCase().replaceAll("\\s+", "-");
        String containerId = containerStatsCollector.getContainerId(dockerClient, containerName);

        if (containerId == null) {
            throw new IllegalStateException("Container ID is not available for application status: " + applicationStatusId);
        }
    
        Map<String, Object> result = new HashMap<>();
        result.put("operation", operation);
        result.put("applicationStatusId", applicationStatusId);
    
        try {
            switch (operation.toString().toLowerCase()) {
                case "status":
                    String status = dockerOperationService.getDockerContainerStatus(host, containerId);
                    result.put("status", status);
                    break;
                case "stop":
                    String stopResult = dockerOperationService.stopDockerContainer(host, containerId);
                    result.put("result", stopResult);
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
    
            applicationHistoryService.insertOperationHistory(applicationStatus, username, reason, "Docker operation: " + operation.name(), operation);
            updateApplicationStatus(applicationStatus, operation, result, username);
    
        } catch (Exception e) {
            log.error("Error performing Docker operation: {} on application status: {}", operation, applicationStatusId, e);
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
            case "uninstall":
                applicationStatus.setStatus(ActionType.UNINSTALL.name());
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
}


