package kr.co.mcmp.softwarecatalog.application.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import kr.co.mcmp.softwarecatalog.application.model.AutoscaleCheckSample;

@Repository
public interface AutoscaleCheckSampleRepository extends JpaRepository<AutoscaleCheckSample, Long> {

    List<AutoscaleCheckSample> findByDeploymentIdAndCheckedAtBetweenOrderByCheckedAtAsc(
            Long deploymentId, LocalDateTime start, LocalDateTime end);

    @Modifying
    @Query("DELETE FROM AutoscaleCheckSample s WHERE s.checkedAt < :cutoff")
    int deleteByCheckedAtBefore(@Param("cutoff") LocalDateTime cutoff);
}
