package kr.co.mcmp.softwarecatalog.application.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import kr.co.mcmp.softwarecatalog.application.model.DailyMetricsSummary;

@Repository
public interface DailyMetricsSummaryRepository extends JpaRepository<DailyMetricsSummary, Long> {

    boolean existsByDeploymentIdAndSummaryDate(Long deploymentId, LocalDate summaryDate);

    List<DailyMetricsSummary> findByDeploymentIdAndSummaryDateAfterOrderBySummaryDateAsc(
            Long deploymentId, LocalDate afterDate);

    List<DailyMetricsSummary> findByDeploymentIdAndSummaryDateBetweenOrderBySummaryDateAsc(
            Long deploymentId, LocalDate startDate, LocalDate endDate);

    @Query("SELECT d FROM DailyMetricsSummary d WHERE d.summaryDate >= :afterDate ORDER BY d.summaryDate ASC")
    List<DailyMetricsSummary> findAllBySummaryDateAfter(@Param("afterDate") LocalDate afterDate);

    @Modifying
    @Query("DELETE FROM DailyMetricsSummary d WHERE d.summaryDate < :cutoffDate")
    int deleteBySummaryDateBefore(@Param("cutoffDate") LocalDate cutoffDate);
}
