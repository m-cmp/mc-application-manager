package kr.co.mcmp.softwarecatalog.application.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import kr.co.mcmp.ape.cbtumblebug.api.CbtumblebugRestApi;
import kr.co.mcmp.ape.cbtumblebug.dto.VmAccessInfo;
import kr.co.mcmp.softwarecatalog.CatalogService;
import kr.co.mcmp.softwarecatalog.SoftwareCatalogDTO;
import kr.co.mcmp.softwarecatalog.application.constants.ActionType;
import kr.co.mcmp.softwarecatalog.application.constants.DeploymentType;
import kr.co.mcmp.softwarecatalog.application.constants.LogType;
import kr.co.mcmp.softwarecatalog.application.dto.DeploymentRequest;
import kr.co.mcmp.softwarecatalog.application.model.ApplicationStatus;
import kr.co.mcmp.softwarecatalog.application.model.DeploymentHistory;
import kr.co.mcmp.softwarecatalog.application.model.DeploymentLog;
import kr.co.mcmp.softwarecatalog.application.model.OperationHistory;
import kr.co.mcmp.softwarecatalog.application.repository.ApplicationStatusRepository;
import kr.co.mcmp.softwarecatalog.application.repository.DeploymentHistoryRepository;
import kr.co.mcmp.softwarecatalog.application.repository.DeploymentLogRepository;
import kr.co.mcmp.softwarecatalog.application.repository.OperationHistoryRepository;
import kr.co.mcmp.softwarecatalog.application.service.ApplicationHistoryService;
import kr.co.mcmp.softwarecatalog.users.Entity.User;
import kr.co.mcmp.softwarecatalog.users.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 애플리케이션 이력 관리를 담당하는 서비스 구현체
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApplicationHistoryServiceImpl implements ApplicationHistoryService {
    
    private final CbtumblebugRestApi cbtumblebugRestApi;
    private final CatalogService catalogService;
    private final UserService userService;
    private final ApplicationStatusRepository applicationStatusRepository;
    private final DeploymentHistoryRepository deploymentHistoryRepository;
    private final DeploymentLogRepository deploymentLogRepository;
    private final OperationHistoryRepository operationHistoryRepository;
    
    @Override
    public DeploymentHistory createDeploymentHistory(DeploymentRequest request, User user) {
        SoftwareCatalogDTO catalog = catalogService.getCatalog(request.getCatalogId());
        
        if (request.getDeploymentType() == DeploymentType.VM) {
            VmAccessInfo vmInfo = cbtumblebugRestApi.getVmInfo(request.getNamespace(), request.getMciId(), request.getVmId());
            String[] parts = vmInfo.getConnectionName().split("-");
            
            return DeploymentHistory.builder()
                    .catalog(catalog.toEntity())
                    .deploymentType(DeploymentType.VM)
                    .cloudProvider(parts.length > 0 ? parts[0] : "")
                    .cloudRegion(vmInfo.getRegion().getRegion())
                    .namespace(request.getNamespace())
                    .mciId(request.getMciId())
                    .vmId(request.getVmId())
                    .publicIp(vmInfo.getPublicIP())
                    .actionType(ActionType.INSTALL)
                    .status("IN_PROGRESS")
                    .servicePort(request.getServicePort())
                    .executedAt(LocalDateTime.now())
                    .executedBy(user)
                    .build();
        } else if (request.getDeploymentType() == DeploymentType.K8S) {
            return DeploymentHistory.builder()
                    .catalog(catalog.toEntity())
                    .deploymentType(DeploymentType.K8S)
                    .namespace(request.getNamespace())
                    .clusterName(request.getClusterName())
                    .actionType(ActionType.INSTALL)
                    .status("IN_PROGRESS")
                    .executedAt(LocalDateTime.now())
                    .executedBy(user)
                    .build();
        }
        
        throw new IllegalArgumentException("Unsupported deployment type: " + request.getDeploymentType());
    }
    
    @Override
    public void updateApplicationStatus(DeploymentHistory history, String status, User user) {
        ApplicationStatus appStatus = applicationStatusRepository.findByCatalogId(history.getCatalog().getId())
                .orElse(new ApplicationStatus());
        
        appStatus.setCatalog(history.getCatalog());
        appStatus.setStatus(status);
        appStatus.setDeploymentType(history.getDeploymentType());
        appStatus.setCheckedAt(LocalDateTime.now());
        
        if (history.getDeploymentType() == DeploymentType.VM) {
            appStatus.setNamespace(history.getNamespace());
            appStatus.setMciId(history.getMciId());
            appStatus.setVmId(history.getVmId());
            appStatus.setPublicIp(history.getPublicIp());
        } else if (history.getDeploymentType() == DeploymentType.K8S) {
            appStatus.setNamespace(history.getNamespace());
            appStatus.setClusterName(history.getClusterName());
        }

        applicationStatusRepository.save(appStatus);
    }
    
    @Override
    public void addDeploymentLog(DeploymentHistory history, LogType logType, String message) {
        DeploymentLog log = DeploymentLog.builder()
                .deployment(history)
                .logType(logType)
                .logMessage(message)
                .loggedAt(LocalDateTime.now())
                .build();
        deploymentLogRepository.save(log);
    }
    
    @Override
    public void insertOperationHistory(ApplicationStatus applicationStatus, String username, String reason, ActionType actionType) {
        User user = getUserOrNull(username);
        OperationHistory operationHistory = OperationHistory.builder()
                    .applicationStatus(applicationStatus)
                    .reason(reason)
                    .operationType(actionType.name())
                    .executedBy(user)
                    .createdAt(LocalDateTime.now()).build();

        operationHistoryRepository.save(operationHistory);
    }
    
    @Override
    public List<DeploymentHistory> getDeploymentHistories(Long catalogId, String username) {
        if (StringUtils.isBlank(username)) {
            return deploymentHistoryRepository.findByCatalogIdOrderByExecutedAtDesc(catalogId);
        } else {
            User user = getUserOrNull(username);
            return user != null ? 
                deploymentHistoryRepository.findByCatalogIdAndExecutedByOrderByExecutedAtDesc(catalogId, user) :
                List.of();
        }
    }
    
    @Override
    public List<DeploymentLog> getDeploymentLogs(Long deploymentId, String username) {
        if (StringUtils.isBlank(username)) {
            return deploymentLogRepository.findByDeploymentIdOrderByLoggedAtDesc(deploymentId);
        } else {
            User user = getUserOrNull(username);
            return user != null ? 
                deploymentLogRepository.findByDeploymentIdAndDeployment_ExecutedByOrderByLoggedAtDesc(deploymentId, user) :
                List.of();
        }
    }
    
    private User getUserOrNull(String username) {
        return StringUtils.isNotBlank(username) ? userService.findUserByUsername(username).orElse(null) : null;
    }
}


