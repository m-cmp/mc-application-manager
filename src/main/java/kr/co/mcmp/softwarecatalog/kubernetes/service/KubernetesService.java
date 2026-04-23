package kr.co.mcmp.softwarecatalog.kubernetes.service;

import java.time.LocalDateTime;
import java.util.Optional;

import jakarta.persistence.EntityNotFoundException;

import org.springframework.stereotype.Service;
import kr.co.mcmp.softwarecatalog.CatalogRepository;
import kr.co.mcmp.softwarecatalog.SoftwareCatalog;
import kr.co.mcmp.softwarecatalog.application.constants.ActionType;
import kr.co.mcmp.softwarecatalog.application.constants.DeploymentType;
import kr.co.mcmp.softwarecatalog.application.constants.LogType;
import kr.co.mcmp.softwarecatalog.application.dto.DeploymentRequest;
import kr.co.mcmp.softwarecatalog.application.model.ApplicationStatus;
import kr.co.mcmp.softwarecatalog.application.model.DeploymentHistory;
import kr.co.mcmp.softwarecatalog.application.model.DeploymentLog;
import kr.co.mcmp.softwarecatalog.application.model.InfraSpecSnapshot;
import kr.co.mcmp.softwarecatalog.application.repository.ApplicationStatusRepository;
import kr.co.mcmp.softwarecatalog.application.repository.DeploymentHistoryRepository;
import kr.co.mcmp.softwarecatalog.application.repository.DeploymentLogRepository;
import kr.co.mcmp.softwarecatalog.application.repository.InfraSpecSnapshotRepository;
import kr.co.mcmp.softwarecatalog.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class KubernetesService {

    private final KubernetesDeployService deploymentService;
    private final KubernetesOperationService operationService;
    private final DeploymentHistoryRepository historyRepository;
    private final ApplicationStatusRepository statusRepository;
    private final CatalogRepository catalogRepository;
    private final DeploymentLogRepository deploymentLogRepository;
    private final InfraSpecSnapshotRepository infraSpecSnapshotRepository;
    private final UserRepository userRepository;

    /**
     * DTO 기반 애플리케이션 배포 (새로운 방식)
     */
    public DeploymentHistory deployApplication(DeploymentRequest request) {
        DeploymentHistory history = null;
        SoftwareCatalog catalog = null;
        try {
            catalog = findCatalogById(request.getCatalogId());
            // DTO를 포함하여 호출
            history = deploymentService.deployApplication(
                request.getNamespace(), 
                request.getClusterName(), 
                catalog, 
                request.getUsername(), 
                request
            );
            addDeploymentLog(history, LogType.INFO, "Deployment initiated successfully with DTO configuration.");
            updateApplicationStatus(request.getNamespace(), request.getClusterName(), catalog, ActionType.INSTALL.name());
            history.setResourceType(request.getResourceType());
            DeploymentHistory saved = historyRepository.save(history);
            saveInfraSpecSnapshot(saved, request, catalog);
            return saved;
        } catch (Exception e) {
            log.error("애플리케이션 배포 중 오류 발생", e);
            
            if (history == null) {
                // 배포 시작 전에 오류가 발생한 경우 - 기본 실패 이력 생성
                history = DeploymentHistory.builder()
                        .namespace(request.getNamespace())
                        .clusterName(request.getClusterName())
                        .catalog(catalog)
                        .executedBy(userRepository.findByUsername(request.getUsername()).orElse(null))
                        .deploymentType(DeploymentType.K8S)
                        .status("FAILED")
                        .actionType(ActionType.INSTALL)
                        .executedAt(LocalDateTime.now())
                        .build();
                addDeploymentLog(history, LogType.ERROR, "Deployment failed: " + e.getMessage());
                historyRepository.save(history);
            } else {
                // 배포 중에 오류가 발생한 경우 (이미 생성된 history가 있음)
                history.setStatus("FAILED");
                addDeploymentLog(history, LogType.ERROR, "Deployment failed: " + e.getMessage());
                historyRepository.save(history);
            }
            
            if (catalog != null) {
                updateApplicationStatus(request.getNamespace(), request.getClusterName(), catalog, "FAILED");
            }
            
            throw new RuntimeException("애플리케이션 배포 실패", e);
        }
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

    private void saveInfraSpecSnapshot(DeploymentHistory history, DeploymentRequest request, SoftwareCatalog catalog) {
        try {
            if (infraSpecSnapshotRepository.existsByDeploymentId(history.getId())) return;

            InfraSpecSnapshot snapshot = InfraSpecSnapshot.builder()
                    .deploymentId(history.getId())
                    .capturedAt(LocalDateTime.now())
                    .resourceType(request.getResourceType())
                    .deploymentType("K8S")
                    .podCpuRequest(formatCpuValue(catalog.getMinCpu()))
                    .podCpuLimit(formatCpuValue(catalog.getRecommendedCpu()))
                    .podMemoryRequest(formatMemoryMi(catalog.getMinMemory()))
                    .podMemoryLimit(formatMemoryMi(catalog.getRecommendedMemory()))
                    .catalogMinCpu(catalog.getMinCpu() != null ? catalog.getMinCpu().doubleValue() : null)
                    .catalogRecCpu(catalog.getRecommendedCpu() != null ? catalog.getRecommendedCpu().doubleValue() : null)
                    .catalogMinMemoryMb(catalog.getMinMemory() != null ? catalog.getMinMemory().intValue() : null)
                    .build();

            infraSpecSnapshotRepository.save(snapshot);
            log.info("InfraSpecSnapshot saved for K8s deployment: {}", history.getId());
        } catch (Exception e) {
            log.warn("Failed to save InfraSpecSnapshot for K8s deployment: {}", history.getId(), e);
        }
    }

    private String formatCpuValue(Double cpuValue) {
        return cpuValue != null ? cpuValue.toString() : null;
    }

    private String formatMemoryMi(Long memoryGi) {
        return memoryGi != null ? (memoryGi * 1024) + "Mi" : null;
    }
}