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

    // Spring Data JPA 3.2 native projections require exact Java property aliases.
    // Quote camelCase names so PostgreSQL does not fold them to lowercase.
    @Query(value = """
            SELECT
                :deploymentId AS "deploymentId",
                AVG(cpu_usage_pct) AS "avgCpuPct",
                MAX(cpu_usage_pct) AS "maxCpuPct",
                PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY cpu_usage_pct) AS "p95CpuPct",
                STDDEV(cpu_usage_pct) AS "stddevCpu",
                AVG(memory_usage_pct) AS "avgMemoryPct",
                MAX(memory_usage_pct) AS "maxMemoryPct",
                PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY memory_usage_pct) AS "p95MemoryPct",
                STDDEV(memory_usage_pct) AS "stddevMemory",
                AVG(network_in_bytes) AS "avgNetworkInBytes",
                MAX(network_in_bytes) AS "maxNetworkInBytes",
                AVG(network_out_bytes) AS "avgNetworkOutBytes",
                MAX(network_out_bytes) AS "maxNetworkOutBytes",
                COUNT(*) AS "sampleCount",
                SUM(CASE WHEN oom_killed = true THEN 1 ELSE 0 END) AS "oomCount",
                SUM(CASE WHEN status = 'RUNNING' THEN 1 ELSE 0 END) * 10 AS "runningMinutes",
                COUNT(*) * 10 AS "totalMinutes",
                MIN(resource_type) AS "resourceType"
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
        Double getAvgNetworkInBytes();
        Long getMaxNetworkInBytes();
        Double getAvgNetworkOutBytes();
        Long getMaxNetworkOutBytes();
        Integer getSampleCount();
        Integer getOomCount();
        Integer getRunningMinutes();
        Integer getTotalMinutes();
        String getResourceType();
    }
}
