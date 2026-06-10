package kr.co.mcmp.softwarecatalog.application.service.impl;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import kr.co.mcmp.softwarecatalog.application.constants.ActionType;
import kr.co.mcmp.softwarecatalog.application.constants.DeploymentType;
import kr.co.mcmp.softwarecatalog.application.constants.ApplicationStatusValues;
import kr.co.mcmp.softwarecatalog.application.model.ApplicationStatus;
import kr.co.mcmp.softwarecatalog.application.model.DeploymentHistory;
import kr.co.mcmp.softwarecatalog.application.repository.ApplicationStatusRepository;
import kr.co.mcmp.softwarecatalog.application.repository.DeploymentHistoryRepository;
import kr.co.mcmp.softwarecatalog.application.service.ApplicationHistoryService;
import kr.co.mcmp.softwarecatalog.application.service.ApplicationOperationService;
import kr.co.mcmp.softwarecatalog.kubernetes.service.KubernetesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Kubernetes 애플리케이션 운영을 담당하는 서비스
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KubernetesApplicationOperationService implements ApplicationOperationService {
    
    private static final List<ActionType> ACTIVE_DEPLOYMENT_ACTIONS = Arrays.asList(ActionType.INSTALL, ActionType.RUN);
    private static final List<String> ACTIVE_DEPLOYMENT_STATUSES = Arrays.asList("SUCCESS", "RUNNING");

    private final ApplicationStatusRepository applicationStatusRepository;
    private final DeploymentHistoryRepository deploymentHistoryRepository;
    private final KubernetesService kubernetesService;
    private final ApplicationHistoryService applicationHistoryService;
    
    @Override
    public Map<String, Object> performOperation(ActionType operation, Long applicationStatusId, String reason, String username) {
        ApplicationStatus applicationStatus = applicationStatusRepository.findById(applicationStatusId)
            .orElseThrow(() -> new IllegalArgumentException("ApplicationStatus not found with id: " + applicationStatusId));

        Long catalogId = applicationStatus.getCatalog().getId();
        String namespace = applicationStatus.getNamespace();
        String clusterName = applicationStatus.getClusterName();

        Map<String, Object> result = new HashMap<>();
        result.put("operation", operation);
        result.put("applicationStatusId", applicationStatusId);
        result.put("success", false);
    
        try {
            switch (operation.toString().toLowerCase()) {
                case "stop":
                    kubernetesService.stopApplication(namespace, clusterName, catalogId, username);
                    break;
                case "uninstall":
                    kubernetesService.uninstallApplication(namespace, clusterName, catalogId, username);
                    break;
                case "restart":
                    kubernetesService.restartApplication(namespace, clusterName, catalogId, username);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown operation: " + operation);
            }
            result.put("success", true);
            result.put("result", "SUCCESS");
            applicationHistoryService.insertOperationHistory(applicationStatus, username, reason, "Kubernetes operation: " + operation.name(), operation);
            updateApplicationStatus(applicationStatus, operation, result, username);
    
        } catch (Exception e) {
            log.error("Error performing Kubernetes operation: {} on application status: {}", operation, applicationStatusId, e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }
    
        return result;
    }
    
    @Override
    public DeploymentType getSupportedDeploymentType() {
        return DeploymentType.K8S;
    }
    
    private void updateApplicationStatus(ApplicationStatus applicationStatus, ActionType operation, Map<String, Object> result, String username) {
        switch (operation.toString().toLowerCase()) {
            case "stop":
                applicationStatus.setStatus(ActionType.STOP.name());
                break;
            case "uninstall":
                applicationStatus.setStatus(ApplicationStatusValues.UNINSTALLED);
                markDeploymentHistoryAsUninstalled(applicationStatus);
                break;
            case "restart":
                applicationStatus.setStatus(ActionType.RESTART.name());
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

        if (deploymentHistory == null && applicationStatus.getCatalog() != null
                && applicationStatus.getClusterName() != null && applicationStatus.getNamespace() != null) {
            deploymentHistory = deploymentHistoryRepository
                    .findTopByCatalogIdAndClusterNameAndNamespaceAndActionTypeInAndStatusInOrderByExecutedAtDesc(
                            applicationStatus.getCatalog().getId(),
                            applicationStatus.getClusterName(),
                            applicationStatus.getNamespace(),
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


