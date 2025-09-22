package kr.co.mcmp.softwarecatalog.application.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import kr.co.mcmp.softwarecatalog.application.dto.UnifiedLogDTO;
import kr.co.mcmp.softwarecatalog.application.model.UnifiedLog;

/**
 * 통합 로그 서비스 인터페이스
 */
public interface UnifiedLogService {
    
    /**
     * 로그 저장
     */
    UnifiedLogDTO saveLog(UnifiedLogDTO logDTO);
    
    /**
     * 배포 ID로 로그 조회
     */
    List<UnifiedLogDTO> getLogsByDeploymentId(Long deploymentId);
    
    /**
     * 배포 ID와 모듈로 로그 조회
     */
    List<UnifiedLogDTO> getLogsByDeploymentIdAndModule(Long deploymentId, String module);
    
    /**
     * 배포 ID와 심각도로 로그 조회
     */
    List<UnifiedLogDTO> getLogsByDeploymentIdAndSeverity(Long deploymentId, String severity);
    
    /**
     * 네임스페이스와 파드 이름으로 로그 조회
     */
    List<UnifiedLogDTO> getLogsByNamespaceAndPodName(String namespace, String podName);
    
    /**
     * 클러스터와 네임스페이스로 로그 조회
     */
    List<UnifiedLogDTO> getLogsByClusterAndNamespace(String clusterName, String namespace);
    
    /**
     * VM ID로 로그 조회
     */
    List<UnifiedLogDTO> getLogsByVmId(String vmId);
    
    /**
     * 시간 범위로 로그 조회
     */
    List<UnifiedLogDTO> getLogsByTimeRange(LocalDateTime startTime, LocalDateTime endTime);
    
    /**
     * 배포 ID와 시간 범위로 로그 조회
     */
    List<UnifiedLogDTO> getLogsByDeploymentIdAndTimeRange(Long deploymentId, LocalDateTime startTime, LocalDateTime endTime);
    
    /**
     * 페이지네이션으로 로그 조회
     */
    Page<UnifiedLogDTO> getLogsByDeploymentIdWithPagination(Long deploymentId, Pageable pageable);
    
    /**
     * 모듈별 로그 통계
     */
    List<Object[]> getLogStatisticsByModule(Long deploymentId);
    
    /**
     * 심각도별 로그 통계
     */
    List<Object[]> getLogStatisticsBySeverity(Long deploymentId);
    
    
    /**
     * 애플리케이션 상태 ID와 심각도로 로그 조회
     */
    List<UnifiedLogDTO> getLogsByApplicationStatusIdAndSeverity(Long applicationStatusId, String severity);
    
    /**
     * 애플리케이션 상태 ID와 모듈로 로그 조회
     */
    List<UnifiedLogDTO> getLogsByApplicationStatusIdAndModule(Long applicationStatusId, String module);
    
    /**
     * 배포 ID로 로그 삭제
     */
    void deleteLogsByDeploymentId(Long deploymentId);
    
    /**
     * 카탈로그 ID로 로그 삭제
     */
    void deleteLogsByCatalogId(Long catalogId);
    
    /**
     * 애플리케이션 상태 ID로 로그 조회
     */
    List<UnifiedLogDTO> getLogsByApplicationStatusId(Long applicationStatusId);
    
    /**
     * 애플리케이션 상태 ID로 로그 삭제
     */
    void deleteLogsByApplicationStatusId(Long applicationStatusId);
    
    /**
     * 오래된 로그 정리
     */
    void cleanupOldLogs(LocalDateTime cutoffDate);
}
