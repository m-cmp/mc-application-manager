package kr.co.mcmp.softwarecatalog.application.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * L2 daily aggregation table.
 * Aggregates resource_metrics_history (L1) every day at 02:00.
 * L1 raw records are deleted after successful aggregation. Retained for 6 months.
 */
@Entity
@Table(name = "daily_metrics_summary",
        uniqueConstraints = @UniqueConstraint(columnNames = {"deployment_id", "summary_date"}),
        indexes = {
            @Index(name = "idx_daily_summary_deployment", columnList = "deployment_id"),
            @Index(name = "idx_daily_summary_date", columnList = "summary_date")
        })
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class DailyMetricsSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "deployment_id", nullable = false)
    private Long deploymentId;

    @Column(name = "summary_date", nullable = false)
    private LocalDate summaryDate;

    // CPU aggregates
    @Column(name = "avg_cpu_pct")
    private Double avgCpuPct;

    @Column(name = "max_cpu_pct")
    private Double maxCpuPct;

    @Column(name = "p95_cpu_pct")
    private Double p95CpuPct;

    @Column(name = "stddev_cpu")
    private Double stddevCpu;

    // Memory aggregates
    @Column(name = "avg_memory_pct")
    private Double avgMemoryPct;

    @Column(name = "max_memory_pct")
    private Double maxMemoryPct;

    @Column(name = "p95_memory_pct")
    private Double p95MemoryPct;

    @Column(name = "stddev_memory")
    private Double stddevMemory;

    // Event aggregates
    @Column(name = "oom_count")
    private Integer oomCount;

    @Column(name = "restart_count")
    private Integer restartCount;

    @Column(name = "crash_loop_count")
    private Integer crashLoopCount;

    // Availability
    @Column(name = "running_minutes")
    private Integer runningMinutes;

    @Column(name = "total_minutes")
    private Integer totalMinutes;

    @Column(name = "sample_count")
    private Integer sampleCount;

    @Column(name = "resource_type", length = 50)
    private String resourceType;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
