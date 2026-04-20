package kr.co.mcmp.softwarecatalog.application.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import kr.co.mcmp.softwarecatalog.application.model.ResourceMetricsHistory;

@Repository
public interface ResourceMetricsHistoryRepository extends JpaRepository<ResourceMetricsHistory, Long> {

    List<ResourceMetricsHistory> findByDeploymentIdAndRecordedAtBetweenOrderByRecordedAtAsc(
            Long deploymentId, LocalDateTime start, LocalDateTime end);

    boolean existsByDeploymentIdAndRecordedAtBetween(
            Long deploymentId, LocalDateTime start, LocalDateTime end);

    @Query("SELECT MAX(r.recordedAt) FROM ResourceMetricsHistory r WHERE r.deploymentId = :deploymentId")
    LocalDateTime findLastRecordedAtByDeploymentId(@Param("deploymentId") Long deploymentId);

    @Modifying
    @Query("DELETE FROM ResourceMetricsHistory r WHERE r.recordedAt < :cutoff")
    int deleteByRecordedAtBefore(@Param("cutoff") LocalDateTime cutoff);

    @Query(value = """
            SELECT
                :deploymentId                                                       AS deployment_id,
                AVG(cpu_usage_pct)                                                  AS avg_cpu_pct,
                MAX(cpu_usage_pct)                                                  AS max_cpu_pct,
                PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY cpu_usage_pct)        AS p95_cpu_pct,
                STDDEV(cpu_usage_pct)                                               AS stddev_cpu,
                AVG(memory_usage_pct)                                               AS avg_memory_pct,
                MAX(memory_usage_pct)                                               AS max_memory_pct,
                PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY memory_usage_pct)     AS p95_memory_pct,
                STDDEV(memory_usage_pct)                                            AS stddev_memory,
                COUNT(*)                                                            AS sample_count,
                SUM(CASE WHEN oom_killed = true THEN 1 ELSE 0 END)                 AS oom_count,
                SUM(CASE WHEN status = 'RUNNING' THEN 1 ELSE 0 END) * 10           AS running_minutes,
                COUNT(*) * 10                                                       AS total_minutes,
                MIN(resource_type)                                                  AS resource_type
            FROM resource_metrics_history
            WHERE deployment_id = :deploymentId
              AND recorded_at BETWEEN :start AND :end
            """, nativeQuery = true)
    DailyAggregationProjection aggregateByDeploymentAndDate(
            @Param("deploymentId") Long deploymentId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    interface DailyAggregationProjection {
        Long getDeploymentId();
        Double getAvgCpuPct();
        Double getMaxCpuPct();
        Double getP95CpuPct();
        Double getStddevCpu();
        Double getAvgMemoryPct();
        Double getMaxMemoryPct();
        Double getP95MemoryPct();
        Double getStddevMemory();
        Integer getSampleCount();
        Integer getOomCount();
        Integer getRunningMinutes();
        Integer getTotalMinutes();
        String getResourceType();
    }
}
