package kr.co.mcmp.softwarecatalog.application.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.co.mcmp.softwarecatalog.application.dto.UnifiedLogDTO;
import kr.co.mcmp.softwarecatalog.application.model.DeploymentHistory;
import kr.co.mcmp.softwarecatalog.application.model.UnifiedLog;
import kr.co.mcmp.softwarecatalog.application.repository.DeploymentHistoryRepository;
import kr.co.mcmp.softwarecatalog.application.repository.UnifiedLogRepository;
import kr.co.mcmp.softwarecatalog.application.service.UnifiedLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 통합 로그 서비스 구현체
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UnifiedLogServiceImpl implements UnifiedLogService {
    
    private final UnifiedLogRepository unifiedLogRepository;
    private final DeploymentHistoryRepository deploymentHistoryRepository;
    
    @Override
    @Transactional
    public UnifiedLogDTO saveLog(UnifiedLogDTO logDTO) {
        try {
            // DeploymentHistory 조회
            DeploymentHistory deployment = deploymentHistoryRepository.findById(logDTO.getDeploymentId())
                    .orElseThrow(() -> new IllegalArgumentException("Deployment not found: " + logDTO.getDeploymentId()));
            
            // DTO를 엔티티로 변환
            UnifiedLog log = UnifiedLog.fromDTO(logDTO);
            log.setDeployment(deployment);
            
            // 저장
            UnifiedLog savedLog = unifiedLogRepository.save(log);
            
            // log.info("Saved unified log: ID={}, Module={}, Severity={}", savedLog.getId(), savedLog.getModule().getValue(), savedLog.getSeverity().getValue());
            
            return savedLog.toDTO();
        } catch (Exception e) {
            log.error("Failed to save unified log", e);
            throw new RuntimeException("Failed to save unified log: " + e.getMessage(), e);
        }
    }
    
    @Override
    public List<UnifiedLogDTO> getLogsByDeploymentId(Long deploymentId) {
        return unifiedLogRepository.findByDeploymentIdOrderByLoggedAtDesc(deploymentId)
                .stream()
                .map(UnifiedLog::toDTO)
                .collect(Collectors.toList());
    }
    
    @Override
    public List<UnifiedLogDTO> getLogsByDeploymentIdAndModule(Long deploymentId, String module) {
        UnifiedLog.LogSourceType moduleType = UnifiedLog.LogSourceType.valueOf(module);
        return unifiedLogRepository.findByDeploymentIdAndModuleOrderByLoggedAtDesc(deploymentId, moduleType)
                .stream()
                .map(UnifiedLog::toDTO)
                .collect(Collectors.toList());
    }
    
    @Override
    public List<UnifiedLogDTO> getLogsByDeploymentIdAndSeverity(Long deploymentId, String severity) {
        UnifiedLog.LogSeverity severityType = UnifiedLog.LogSeverity.valueOf(severity);
        return unifiedLogRepository.findByDeploymentIdAndSeverityOrderByLoggedAtDesc(deploymentId, severityType)
                .stream()
                .map(UnifiedLog::toDTO)
                .collect(Collectors.toList());
    }
    
    @Override
    public List<UnifiedLogDTO> getLogsByNamespaceAndPodName(String namespace, String podName) {
        return unifiedLogRepository.findByNamespaceAndPodNameOrderByLoggedAtDesc(namespace, podName)
                .stream()
                .map(UnifiedLog::toDTO)
                .collect(Collectors.toList());
    }
    
    @Override
    public List<UnifiedLogDTO> getLogsByClusterAndNamespace(String clusterName, String namespace) {
        return unifiedLogRepository.findByClusterNameAndNamespaceOrderByLoggedAtDesc(clusterName, namespace)
                .stream()
                .map(UnifiedLog::toDTO)
                .collect(Collectors.toList());
    }
    
    @Override
    public List<UnifiedLogDTO> getLogsByVmId(String vmId) {
        return unifiedLogRepository.findByVmIdOrderByLoggedAtDesc(vmId)
                .stream()
                .map(UnifiedLog::toDTO)
                .collect(Collectors.toList());
    }
    
    @Override
    public List<UnifiedLogDTO> getLogsByTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        return unifiedLogRepository.findByLoggedAtBetweenOrderByLoggedAtDesc(startTime, endTime)
                .stream()
                .map(UnifiedLog::toDTO)
                .collect(Collectors.toList());
    }
    
    @Override
    public List<UnifiedLogDTO> getLogsByDeploymentIdAndTimeRange(Long deploymentId, LocalDateTime startTime, LocalDateTime endTime) {
        return unifiedLogRepository.findByDeploymentIdAndLoggedAtBetweenOrderByLoggedAtDesc(deploymentId, startTime, endTime)
                .stream()
                .map(UnifiedLog::toDTO)
                .collect(Collectors.toList());
    }
    
    @Override
    public Page<UnifiedLogDTO> getLogsByDeploymentIdWithPagination(Long deploymentId, Pageable pageable) {
        return unifiedLogRepository.findByDeploymentIdOrderByLoggedAtDesc(deploymentId, pageable)
                .map(UnifiedLog::toDTO);
    }
    
    @Override
    public List<Object[]> getLogStatisticsByModule(Long deploymentId) {
        return unifiedLogRepository.countByModuleAndDeploymentId(deploymentId);
    }
    
    @Override
    public List<Object[]> getLogStatisticsBySeverity(Long deploymentId) {
        return unifiedLogRepository.countBySeverityAndDeploymentId(deploymentId);
    }
    
    
    
    
    @Override
    @Transactional
    public void deleteLogsByDeploymentId(Long deploymentId) {
        try {
            unifiedLogRepository.deleteByDeploymentId(deploymentId);
            log.info("Deleted all logs for deployment: {}", deploymentId);
        } catch (Exception e) {
            log.error("Failed to delete logs for deployment: {}", deploymentId, e);
        }
    }
    
    @Override
    @Transactional
    public void deleteLogsByCatalogId(Long catalogId) {
        try {
            unifiedLogRepository.deleteByCatalogId(catalogId);
            log.info("Deleted all logs for catalog: {}", catalogId);
        } catch (Exception e) {
            log.error("Failed to delete logs for catalog: {}", catalogId, e);
        }
    }
    
    @Override
    public List<UnifiedLogDTO> getLogsByApplicationStatusId(Long applicationStatusId) {
        return unifiedLogRepository.findByApplicationStatusIdOrderByLoggedAtDesc(applicationStatusId)
                .stream()
                .map(UnifiedLog::toDTO)
                .collect(Collectors.toList());
    }
    
    @Override
    public List<UnifiedLogDTO> getLogsByApplicationStatusIdAndModule(Long applicationStatusId, String module) {
        UnifiedLog.LogSourceType moduleType = UnifiedLog.LogSourceType.valueOf(module);
        return unifiedLogRepository.findByApplicationStatusIdAndModuleOrderByLoggedAtDesc(applicationStatusId, moduleType)
                .stream()
                .map(UnifiedLog::toDTO)
                .collect(Collectors.toList());
    }
    
    @Override
    public List<UnifiedLogDTO> getLogsByApplicationStatusIdAndSeverity(Long applicationStatusId, String severity) {
        UnifiedLog.LogSeverity severityType = UnifiedLog.LogSeverity.valueOf(severity);
        return unifiedLogRepository.findByApplicationStatusIdAndSeverityOrderByLoggedAtDesc(applicationStatusId, severityType)
                .stream()
                .map(UnifiedLog::toDTO)
                .collect(Collectors.toList());
    }
    
    @Override
    @Transactional
    public void deleteLogsByApplicationStatusId(Long applicationStatusId) {
        try {
            unifiedLogRepository.deleteByApplicationStatusId(applicationStatusId);
            log.info("Deleted all logs for application status: {}", applicationStatusId);
        } catch (Exception e) {
            log.error("Failed to delete logs for application status: {}", applicationStatusId, e);
        }
    }
    
    @Override
    @Transactional
    public void cleanupOldLogs(LocalDateTime cutoffDate) {
        try {
            int deletedCount = unifiedLogRepository.deleteByLoggedAtBefore(cutoffDate);
            log.info("Cleaned up {} old logs before {}", deletedCount, cutoffDate);
        } catch (Exception e) {
            log.error("Failed to cleanup old logs before {}", cutoffDate, e);
        }
    }
    
}
