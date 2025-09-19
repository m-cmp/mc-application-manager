package kr.co.mcmp.softwarecatalog.application.service.impl;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.persistence.EntityNotFoundException;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.co.mcmp.softwarecatalog.application.constants.ActionType;
import kr.co.mcmp.softwarecatalog.application.constants.DeploymentType;
import kr.co.mcmp.softwarecatalog.application.dto.ApplicationStatusDto;
import kr.co.mcmp.softwarecatalog.application.dto.DeploymentRequest;
import kr.co.mcmp.softwarecatalog.application.model.ApplicationStatus;
import kr.co.mcmp.softwarecatalog.application.model.DeploymentHistory;
import kr.co.mcmp.softwarecatalog.application.model.DeploymentLog;
import kr.co.mcmp.ape.cbtumblebug.dto.K8sSpec;
import kr.co.mcmp.ape.cbtumblebug.dto.Spec;
import kr.co.mcmp.softwarecatalog.application.repository.ApplicationStatusRepository;
import kr.co.mcmp.softwarecatalog.application.repository.DeploymentHistoryRepository;
import kr.co.mcmp.softwarecatalog.application.service.ApplicationHistoryService;
import kr.co.mcmp.softwarecatalog.application.service.ApplicationOperationService;
import kr.co.mcmp.softwarecatalog.application.service.ApplicationOrchestrationService;
import kr.co.mcmp.softwarecatalog.application.service.DeploymentService;
import kr.co.mcmp.softwarecatalog.application.service.SpecValidationService;
import kr.co.mcmp.softwarecatalog.users.Entity.User;
import kr.co.mcmp.softwarecatalog.users.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 애플리케이션 오케스트레이션을 담당하는 메인 서비스 구현체
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class ApplicationOrchestrationServiceImpl implements ApplicationOrchestrationService {
    
    private final ApplicationStatusRepository applicationStatusRepository;
    private final UserService userService;
    private final ApplicationHistoryService applicationHistoryService;
    private final SpecValidationService specValidationService;
    private final List<DeploymentService> deploymentServices;
    private final List<ApplicationOperationService> operationServices;
    private final DeploymentHistoryRepository deploymentHistoryRepository;
    
    @Override
    public DeploymentHistory deployApplication(DeploymentRequest request) {
        log.info("Starting deployment for catalogId: {}, type: {}", request.getCatalogId(), request.getDeploymentType());
        
        // 스펙 검증
        if (!validateSpec(request)) {
            log.warn("⚠️ Spec validation failed for deployment request - catalogId: {}, type: {}", 
                    request.getCatalogId(), request.getDeploymentType());
            log.warn("⚠️ Continuing deployment despite insufficient resources...");
        } else {
            log.info("✅ Spec validation passed for deployment request - catalogId: {}, type: {}", 
                    request.getCatalogId(), request.getDeploymentType());
        }
        
        // 배포 타입에 따른 적절한 배포 서비스 선택
        DeploymentService deploymentService = getDeploymentService(request.getDeploymentType());
        
        // 배포 실행
        return deploymentService.deployApplication(request);
    }
    
    @Override
    public Map<String, Object> performOperation(ActionType operation, Long applicationStatusId, String reason, String username) {
        log.info("Performing operation: {} on applicationStatusId: {}", operation, applicationStatusId);
        
        ApplicationStatus applicationStatus = applicationStatusRepository.findById(applicationStatusId)
            .orElseThrow(() -> new EntityNotFoundException("ApplicationStatus not found with id: " + applicationStatusId));
        
        // 배포 타입에 따른 적절한 운영 서비스 선택
        ApplicationOperationService operationService = getOperationService(applicationStatus.getDeploymentType());
        
        // 운영 실행
        return operationService.performOperation(operation, applicationStatusId, reason, username);
    }
    
    @Override
    public List<ApplicationStatusDto> getApplicationGroups() {
        List<Object[]> vmGroups = applicationStatusRepository.findDistinctVmGroups();
        
        return vmGroups.stream()
            .flatMap(group -> {
                String namespace = (String) group[0];
                String mciId = (String) group[1];
                String vmId = (String) group[2];
                List<ApplicationStatus> applications = applicationStatusRepository.findByNamespaceAndMciIdAndVmId(
                    namespace, mciId, vmId);
                
                return applications.stream()
                    .map(ApplicationStatusDto::fromEntity);
            })
            .collect(Collectors.toList());
    }
    
    @Override
    public List<DeploymentHistory> getDeploymentHistories(Long catalogId, String username) {
        return applicationHistoryService.getDeploymentHistories(catalogId, username);
    }
    
    @Override
    public List<DeploymentLog> getDeploymentLogs(Long deploymentId, String username) {
        return applicationHistoryService.getDeploymentLogs(deploymentId, username);
    }
    
    @Override
    public ApplicationStatusDto getLatestApplicationStatus(String username) {
        if (StringUtils.isBlank(username)) {
            throw new IllegalArgumentException("Username cannot be blank");
        }
    
        User user = userService.findUserByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("User not found with username: " + username));
    
        ApplicationStatus status = applicationStatusRepository.findTopByExecutedByOrderByCheckedAtDesc(user)
                .orElseThrow(() -> new EntityNotFoundException("Application status not found for user: " + username));
    
        return ApplicationStatusDto.fromEntity(status);
    }
    
    @Override
    public boolean checkSpecForVm(String namespace, String mciId, String vmId, Long catalogId) {
        return specValidationService.checkSpecForVm(namespace, mciId, vmId, catalogId);
    }
    
    @Override
    public boolean checkSpecForK8s(String namespace, String clusterName, Long catalogId) {
        return specValidationService.checkSpecForK8s(namespace, clusterName, catalogId);
    }
    
    @Override
    public Spec getSpecForVm(String namespace, String mciId, String vmId) {
        return specValidationService.getSpecForVm(namespace, mciId, vmId);
    }
    
    @Override
    public K8sSpec getSpecForK8s(String namespace, String clusterName) {
        return specValidationService.getSpecForK8s(namespace, clusterName);
    }
    
    /**
     * 배포 요청의 스펙을 검증합니다.
     */
    private boolean validateSpec(DeploymentRequest request) {
        try {
            boolean isValid = false;
            if (request.getDeploymentType() == DeploymentType.VM) {
                isValid = checkSpecForVm(request.getNamespace(), request.getMciId(), request.getVmId(), request.getCatalogId());
                if (!isValid) {
                    log.warn("❌ VM spec validation failed - insufficient resources for VM deployment");
                }
            } else if (request.getDeploymentType() == DeploymentType.K8S) {
                isValid = checkSpecForK8s(request.getNamespace(), request.getClusterName(), request.getCatalogId());
                if (!isValid) {
                    log.warn("❌ K8s spec validation failed - insufficient resources for K8s deployment");
                }
            } else {
                log.info("ℹ️ Spec validation skipped for deployment type: {}", request.getDeploymentType());
                return true; // 기타 타입은 검증 생략
            }
            return isValid;
        } catch (Exception e) {
            log.warn("❌ Spec validation error: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 배포 타입에 따른 적절한 배포 서비스를 반환합니다.
     */
    private DeploymentService getDeploymentService(DeploymentType deploymentType) {
        return deploymentServices.stream()
                .filter(service -> service.getDeploymentService(deploymentType) != null)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No deployment service found for type: " + deploymentType));
    }
    
    /**
     * 배포 타입에 따른 적절한 운영 서비스를 반환합니다.
     */
    private ApplicationOperationService getOperationService(DeploymentType deploymentType) {
        return operationServices.stream()
                .filter(service -> service.getSupportedDeploymentType() == deploymentType)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No operation service found for type: " + deploymentType));
    }
    
    @Override
    public Map<String, Object> deleteApplicationByDeploymentHistoryId(Long deploymentHistoryId, String reason, String username) {
        log.info("Deleting application by deployment history ID: {}", deploymentHistoryId);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 1. DeploymentHistory 조회
            DeploymentHistory deploymentHistory = deploymentHistoryRepository.findById(deploymentHistoryId)
                    .orElseThrow(() -> new EntityNotFoundException("Deployment history not found with ID: " + deploymentHistoryId));
            
            // 2. ApplicationStatus 조회 (deploymentHistoryId로)
            Optional<ApplicationStatus> applicationStatusOpt = applicationStatusRepository
                    .findByDeploymentHistoryId(deploymentHistoryId);
            
            if (applicationStatusOpt.isPresent()) {
                ApplicationStatus applicationStatus = applicationStatusOpt.get();
                
                // 3. 배포 타입에 따른 삭제 처리
                if (deploymentHistory.getDeploymentType() == DeploymentType.K8S) {
                    // K8s 배포 삭제
                    deleteK8sApplication(deploymentHistory, applicationStatus, reason, username);
                } else if (deploymentHistory.getDeploymentType() == DeploymentType.VM) {
                    // VM 배포 삭제
                    deleteVmApplication(deploymentHistory, applicationStatus, reason, username);
                }
                
                // 4. ApplicationStatus 삭제
                applicationStatusRepository.delete(applicationStatus);
                log.info("ApplicationStatus deleted for deployment history ID: {}", deploymentHistoryId);
            }
            
            // 5. DeploymentHistory 상태 업데이트
            deploymentHistory.setStatus("DELETED");
            deploymentHistory.setUpdatedAt(LocalDateTime.now());
            deploymentHistoryRepository.save(deploymentHistory);
            
            result.put("success", true);
            result.put("message", "Application deleted successfully");
            result.put("deploymentHistoryId", deploymentHistoryId);
            result.put("deploymentType", deploymentHistory.getDeploymentType());
            
            log.info("Successfully deleted application for deployment history ID: {}", deploymentHistoryId);
            
        } catch (Exception e) {
            log.error("Failed to delete application for deployment history ID: {}", deploymentHistoryId, e);
            result.put("success", false);
            result.put("message", "Failed to delete application: " + e.getMessage());
            result.put("deploymentHistoryId", deploymentHistoryId);
        }
        
        return result;
    }
    
    private void deleteK8sApplication(DeploymentHistory deploymentHistory, ApplicationStatus applicationStatus, String reason, String username) {
        try {
            // K8s 배포 삭제 로직
            log.info("Deleting K8s application - Namespace: {}, Cluster: {}", 
                    applicationStatus.getNamespace(), applicationStatus.getClusterName());
            
            // TODO: 실제 K8s 리소스 삭제 로직 구현
            // - Helm release uninstall
            // - 관련 리소스 정리
            
        } catch (Exception e) {
            log.error("Failed to delete K8s application", e);
            throw new RuntimeException("K8s application deletion failed", e);
        }
    }
    
    private void deleteVmApplication(DeploymentHistory deploymentHistory, ApplicationStatus applicationStatus, String reason, String username) {
        try {
            // VM 배포 삭제 로직
            log.info("Deleting VM application - MCI: {}, VM: {}", 
                    applicationStatus.getMciId(), applicationStatus.getVmId());
            
            // TODO: 실제 VM 컨테이너 삭제 로직 구현
            // - Docker container stop/remove
            // - 관련 리소스 정리
            
        } catch (Exception e) {
            log.error("Failed to delete VM application", e);
            throw new RuntimeException("VM application deletion failed", e);
        }
    }

    @Override
    public List<DeploymentHistory> getAllDeploymentHistory() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getAllDeploymentHistory'");
    }
}

