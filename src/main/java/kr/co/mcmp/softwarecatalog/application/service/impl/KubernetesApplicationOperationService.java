package kr.co.mcmp.softwarecatalog.application.service.impl;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import kr.co.mcmp.softwarecatalog.application.constants.ActionType;
import kr.co.mcmp.softwarecatalog.application.constants.DeploymentType;
import kr.co.mcmp.softwarecatalog.application.model.ApplicationStatus;
import kr.co.mcmp.softwarecatalog.application.repository.ApplicationStatusRepository;
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
    
    private final ApplicationStatusRepository applicationStatusRepository;
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
            result.put("result", "SUCCESS");
            applicationHistoryService.insertOperationHistory(applicationStatus, username, reason, "Kubernetes operation: " + operation.name(), operation);
            updateApplicationStatus(applicationStatus, operation, result, username);
    
        } catch (Exception e) {
            log.error("Error performing Kubernetes operation: {} on application status: {}", operation, applicationStatusId, e);
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
                applicationStatus.setStatus(ActionType.UNINSTALL.name());
                break;
            case "restart":
                applicationStatus.setStatus(ActionType.RESTART.name());
                break;
        }
        applicationStatus.setCheckedAt(java.time.LocalDateTime.now());
        applicationStatusRepository.save(applicationStatus);
    }
}


