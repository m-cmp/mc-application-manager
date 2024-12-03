package kr.co.mcmp.softwarecatalog.kubernetes.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import javax.persistence.EntityNotFoundException;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import io.fabric8.kubernetes.api.model.Pod;
import kr.co.mcmp.softwarecatalog.CatalogRepository;
import kr.co.mcmp.softwarecatalog.SoftwareCatalog;
import kr.co.mcmp.softwarecatalog.application.constants.ActionType;
import kr.co.mcmp.softwarecatalog.application.constants.LogType;
import kr.co.mcmp.softwarecatalog.application.model.ApplicationStatus;
import kr.co.mcmp.softwarecatalog.application.model.DeploymentHistory;
import kr.co.mcmp.softwarecatalog.application.model.DeploymentLog;
import kr.co.mcmp.softwarecatalog.application.repository.ApplicationStatusRepository;
import kr.co.mcmp.softwarecatalog.application.repository.DeploymentHistoryRepository;
import kr.co.mcmp.softwarecatalog.application.repository.DeploymentLogRepository;
import kr.co.mcmp.softwarecatalog.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class KubernetesService {

    private final KubernetesDeployService deploymentService;
    private final DeploymentHistoryRepository historyRepository;
    private final ApplicationStatusRepository statusRepository;
    private final CatalogRepository catalogRepository;
    private final DeploymentLogRepository deploymentLogRepository;
    private final KubernetesOperationService operationService;
    private final UserRepository userRepository;

    public DeploymentHistory deployApplication(String namespace, String clusterName, Long catalogId, String username) {
        DeploymentHistory history = null;
        SoftwareCatalog catalog = null;
        try {
            catalog = findCatalogById(catalogId);
            history = deploymentService.deployApplication(namespace, clusterName, catalog, username);
            addDeploymentLog(history, LogType.INFO, "Deployment initiated successfully.");
            updateApplicationStatus(namespace, clusterName, catalog, ActionType.INSTALL.name());
            return historyRepository.save(history);
        } catch (Exception e) {
            log.error("애플리케이션 배포 중 오류 발생", e);
            
            if (history == null) {
                // 배포 시작 전에 오류가 발생한 경우
                history = createFailedDeploymentHistory(namespace, clusterName, catalog, username);
            }
            
            history.setStatus("FAILED");
            addDeploymentLog(history, LogType.ERROR, "Deployment failed: " + e.getMessage());
            
            if (catalog != null) {
                updateApplicationStatus(namespace, clusterName, catalog, "FAILED");
            }
            
            historyRepository.save(history);
            throw new RuntimeException("애플리케이션 배포 실패", e);
        }
    }
    
    private DeploymentHistory createFailedDeploymentHistory(String namespace, String clusterName, SoftwareCatalog catalog, String username) {
        return DeploymentHistory.builder()
                .namespace(namespace)
                .clusterName(clusterName)
                .catalog(catalog)
                .executedBy(userRepository.findByUsername(username).orElse(null))
                .status("FAILED")
                .actionType(ActionType.INSTALL)
                .executedAt(LocalDateTime.now())
                .build();
    }

    public void stopApplication(String namespace, String clusterName, Long catalogId, String username) {
        try {
            SoftwareCatalog catalog = findCatalogById(catalogId);
            operationService.stopApplication(namespace, clusterName, catalog, username);
            updateApplicationStatus(namespace, clusterName, catalog, ActionType.STOP.name() );
        } catch (Exception e) {
            log.error("애플리케이션 중지 중 오류 발생", e);
            throw new RuntimeException("애플리케이션 중지 실패", e);
        }
    }

    public void restartApplication(String namespace, String clusterName, Long catalogId, String username) {
        try {
            SoftwareCatalog catalog = findCatalogById(catalogId);
            operationService.restartApplication(namespace, clusterName, catalog, username);
            updateApplicationStatus(namespace, clusterName, catalog, ActionType.RESTART.name());
        } catch (Exception e) {
            log.error("애플리케이션 재시작 중 오류 발생", e);
            throw new RuntimeException("애플리케이션 재시작 실패", e);
        }
    }

    public void uninstallApplication(String namespace, String clusterName, Long catalogId, String username) {
        try {
            SoftwareCatalog catalog = findCatalogById(catalogId);
            operationService.uninstallApplication(namespace, clusterName, catalog, username);
            updateApplicationStatus(namespace, clusterName, catalog, ActionType.UNINSTALL.name());
        } catch (Exception e) {
            log.error("애플리케이션 제거 중 오류 발생", e);
            throw new RuntimeException("애플리케이션 제거 실패", e);
        }
    }
    
    
    private SoftwareCatalog findCatalogById(Long catalogId) {
        return catalogRepository.findById(catalogId)
                .orElseThrow(() -> new EntityNotFoundException("소프트웨어 카탈로그를 찾을 수 없습니다."));
    }

    private void addDeploymentLog(DeploymentHistory history, LogType logType, String message) {
        DeploymentLog log = DeploymentLog.builder()
                .deployment(history)
                .logType(logType)
                .logMessage(message)
                .loggedAt(LocalDateTime.now())
                .build();
        deploymentLogRepository.save(log);
    }

    private void updateApplicationStatus(String namespace, String clusterName, SoftwareCatalog catalog, String status) {

        // ApplicationStatus status = createApplicationStatus(namespace, clusterName, catalog);
        // statusRepository.save(status);
        Optional<ApplicationStatus> optApplicationStatus = statusRepository.findLatestByNamespaceAndClusterNameAndCatalogId(namespace, clusterName, catalog.getId());

        if(optApplicationStatus.isPresent()){
            optApplicationStatus.get().setStatus(status);
            optApplicationStatus.get().setCheckedAt(LocalDateTime.now());
            statusRepository.save(optApplicationStatus.get());
        }else{
            ApplicationStatus applicationStatus = createApplicationStatus(namespace, clusterName, catalog);
            applicationStatus.setStatus(status);
            statusRepository.save(applicationStatus);
        }

        
    }


    private ApplicationStatus createApplicationStatus(String namespace, String clusterName, SoftwareCatalog catalog) {
        return ApplicationStatus.builder()
                .namespace(namespace)
                .clusterName(clusterName)
                .catalog(catalog)
                .status("IN_PROGRESS")
                .checkedAt(LocalDateTime.now())
                .build();
    }

    private ApplicationStatus createAndSaveApplicationStatus(String namespace, String clusterName, List<Pod> pods,
            SoftwareCatalog catalog) {

        ApplicationStatus status = createApplicationStatus(namespace, clusterName, catalog);
        status.setPodStatus(getPodStatusSummary(pods));
        return statusRepository.save(status);
    }

    private String getPodStatusSummary(List<Pod> pods) {
        long runningPods = pods.stream()
                .filter(pod -> "Running".equals(pod.getStatus().getPhase()))
                .count();
        return runningPods + "/" + pods.size() + " running";
    }
}