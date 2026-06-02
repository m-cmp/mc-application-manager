package kr.co.mcmp.softwarecatalog.application.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import kr.co.mcmp.softwarecatalog.application.model.AbnormalEvent;

@Repository
public interface AbnormalEventRepository extends JpaRepository<AbnormalEvent, Long> {

    List<AbnormalEvent> findByDeploymentIdAndOccurredAtBetweenOrderByOccurredAtDesc(
            Long deploymentId, LocalDateTime start, LocalDateTime end);

    long countByDeploymentIdAndEventTypeAndOccurredAtBetween(
            Long deploymentId, String eventType, LocalDateTime start, LocalDateTime end);

    List<AbnormalEvent> findByDeploymentIdAndEventTypeOrderByOccurredAtDesc(
            Long deploymentId, String eventType);

    @Modifying
    @Query("DELETE FROM AbnormalEvent e WHERE e.occurredAt < :cutoff")
    int deleteByOccurredAtBefore(@Param("cutoff") LocalDateTime cutoff);
}
