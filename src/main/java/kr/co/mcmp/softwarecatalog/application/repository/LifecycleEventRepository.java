package kr.co.mcmp.softwarecatalog.application.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import kr.co.mcmp.softwarecatalog.application.model.LifecycleEvent;

@Repository
public interface LifecycleEventRepository extends JpaRepository<LifecycleEvent, Long> {

    List<LifecycleEvent> findByDeploymentIdAndOccurredAtBetweenOrderByOccurredAtDesc(
            Long deploymentId, LocalDateTime start, LocalDateTime end);

    long countByDeploymentIdAndEventTypeAndOccurredAtBetween(
            Long deploymentId, String eventType, LocalDateTime start, LocalDateTime end);

    List<LifecycleEvent> findByDeploymentIdAndEventTypeOrderByOccurredAtDesc(
            Long deploymentId, String eventType);

    @Modifying
    @Query("DELETE FROM LifecycleEvent e WHERE e.occurredAt < :cutoff")
    int deleteByOccurredAtBefore(@Param("cutoff") LocalDateTime cutoff);
}
