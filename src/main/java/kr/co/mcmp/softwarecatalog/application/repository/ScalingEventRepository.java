package kr.co.mcmp.softwarecatalog.application.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import kr.co.mcmp.softwarecatalog.application.model.ScalingEvent;
import kr.co.mcmp.softwarecatalog.application.model.ScalingEvent.ScalingStatus;

@Repository
public interface ScalingEventRepository extends JpaRepository<ScalingEvent, Long> {
    
    /**
     * 특정 배포의 진행 중인 스케일링 이벤트 조회
     */
    @Query("SELECT s FROM ScalingEvent s WHERE s.deploymentId = :deploymentId AND s.status IN ('PENDING', 'IN_PROGRESS') ORDER BY s.triggeredAt, s.id")
    List<ScalingEvent> findPendingScalingEvents(@Param("deploymentId") Long deploymentId);

    @Modifying
    @Transactional
    @Query("""
            UPDATE ScalingEvent s SET s.status = :status, s.completedAt = :completedAt, s.errorMessage = :errorMessage
            WHERE s.id = :eventId AND s.status IN ('PENDING', 'IN_PROGRESS')
            """)
    int finishPendingEvent(@Param("eventId") Long eventId, @Param("status") ScalingStatus status,
            @Param("completedAt") LocalDateTime completedAt, @Param("errorMessage") String errorMessage);
    
    /**
     * 특정 클러스터의 진행 중인 스케일링 이벤트 조회
     */
    @Query("SELECT s FROM ScalingEvent s WHERE s.namespace = :namespace AND s.clusterName = :clusterName AND s.status IN ('PENDING', 'IN_PROGRESS')")
    List<ScalingEvent> findPendingScalingEventsByCluster(@Param("namespace") String namespace, @Param("clusterName") String clusterName);
    
    /**
     * 가장 최근의 스케일링 이벤트 조회
     */
    Optional<ScalingEvent> findTopByDeploymentIdOrderByTriggeredAtDesc(Long deploymentId);
    
    /**
     * 배포의 모든 스케일링 이벤트 조회
     */
    List<ScalingEvent> findAllByDeploymentIdOrderByTriggeredAtDesc(Long deploymentId);
}
