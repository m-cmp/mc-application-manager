package kr.co.mcmp.softwarecatalog.application.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import kr.co.mcmp.softwarecatalog.application.model.UnifiedLog;

@Repository
public interface UnifiedLogRepository extends JpaRepository<UnifiedLog, Long> {
    
    /**
     * 배포 ID로 로그 조회 (최신순)
     */
    List<UnifiedLog> findByDeploymentIdOrderByLoggedAtDesc(Long deploymentId);
    
    /**
     * 배포 ID와 모듈로 로그 조회 (최신순)
     */
    List<UnifiedLog> findByDeploymentIdAndModuleOrderByLoggedAtDesc(Long deploymentId, UnifiedLog.LogSourceType module);
    
    /**
     * 배포 ID와 심각도로 로그 조회 (최신순)
     */
    List<UnifiedLog> findByDeploymentIdAndSeverityOrderByLoggedAtDesc(Long deploymentId, UnifiedLog.LogSeverity severity);
    
    /**
     * 네임스페이스와 파드 이름으로 로그 조회 (최신순)
     */
    List<UnifiedLog> findByNamespaceAndPodNameOrderByLoggedAtDesc(String namespace, String podName);
    
    /**
     * 클러스터 이름과 네임스페이스로 로그 조회 (최신순)
     */
    List<UnifiedLog> findByClusterNameAndNamespaceOrderByLoggedAtDesc(String clusterName, String namespace);
    
    /**
     * VM ID로 로그 조회 (최신순)
     */
    List<UnifiedLog> findByVmIdOrderByLoggedAtDesc(String vmId);
    
    /**
     * 시간 범위로 로그 조회 (최신순)
     */
    List<UnifiedLog> findByLoggedAtBetweenOrderByLoggedAtDesc(LocalDateTime startTime, LocalDateTime endTime);
    
    /**
     * 배포 ID와 시간 범위로 로그 조회 (최신순)
     */
    List<UnifiedLog> findByDeploymentIdAndLoggedAtBetweenOrderByLoggedAtDesc(
            Long deploymentId, LocalDateTime startTime, LocalDateTime endTime);
    
    /**
     * 모듈별 로그 개수 조회
     */
    @Query("SELECT u.module, COUNT(u) FROM UnifiedLog u WHERE u.deployment.id = :deploymentId GROUP BY u.module")
    List<Object[]> countByModuleAndDeploymentId(@Param("deploymentId") Long deploymentId);
    
    /**
     * 심각도별 로그 개수 조회
     */
    @Query("SELECT u.severity, COUNT(u) FROM UnifiedLog u WHERE u.deployment.id = :deploymentId GROUP BY u.severity")
    List<Object[]> countBySeverityAndDeploymentId(@Param("deploymentId") Long deploymentId);
    
    /**
     * 배포 ID로 로그 삭제
     */
    @Modifying
    @Query("DELETE FROM UnifiedLog u WHERE u.deployment.id = :deploymentId")
    void deleteByDeploymentId(@Param("deploymentId") Long deploymentId);
    
    /**
     * 카탈로그 ID로 로그 삭제
     */
    @Modifying
    @Query("DELETE FROM UnifiedLog u WHERE u.deployment.catalog.id = :catalogId")
    void deleteByCatalogId(@Param("catalogId") Long catalogId);
    
    /**
     * 애플리케이션 상태 ID로 로그 조회 (최신순)
     */
    List<UnifiedLog> findByApplicationStatusIdOrderByLoggedAtDesc(Long applicationStatusId);
    
    /**
     * 애플리케이션 상태 ID와 모듈로 로그 조회 (최신순)
     */
    List<UnifiedLog> findByApplicationStatusIdAndModuleOrderByLoggedAtDesc(Long applicationStatusId, UnifiedLog.LogSourceType module);
    
    /**
     * 애플리케이션 상태 ID와 심각도로 로그 조회 (최신순)
     */
    List<UnifiedLog> findByApplicationStatusIdAndSeverityOrderByLoggedAtDesc(Long applicationStatusId, UnifiedLog.LogSeverity severity);
    
    /**
     * 애플리케이션 상태 ID로 로그 삭제
     */
    @Modifying
    @Query("DELETE FROM UnifiedLog u WHERE u.applicationStatus.id = :applicationStatusId")
    void deleteByApplicationStatusId(@Param("applicationStatusId") Long applicationStatusId);
    
    /**
     * 오래된 로그 삭제 (지정된 날짜 이전)
     */
    @Modifying
    @Query("DELETE FROM UnifiedLog u WHERE u.loggedAt < :cutoffDate")
    int deleteByLoggedAtBefore(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    /**
     * 페이지네이션으로 로그 조회
     */
    Page<UnifiedLog> findByDeploymentIdOrderByLoggedAtDesc(Long deploymentId, Pageable pageable);
    
    /**
     * 모듈과 심각도로 필터링된 로그 조회
     */
    List<UnifiedLog> findByDeploymentIdAndModuleAndSeverityOrderByLoggedAtDesc(
            Long deploymentId, UnifiedLog.LogSourceType module, UnifiedLog.LogSeverity severity);
    
    /**
     * 애플리케이션 상태 ID와 심각도로 로그 조회
     */
    List<UnifiedLog> findByApplicationStatusIdAndSeverityOrderByLoggedAtDesc(Long applicationStatusId, String severity);
    
    /**
     * 애플리케이션 상태 ID와 모듈로 로그 조회
     */
    List<UnifiedLog> findByApplicationStatusIdAndModuleOrderByLoggedAtDesc(Long applicationStatusId, String module);
}
