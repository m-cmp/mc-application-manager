package kr.co.mcmp.softwarecatalog.application.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * L1 raw metrics time-series table.
 * Snapshots are stored every 10 minutes and rolled up into daily_metrics_summary after 1 month.
 */
@Entity
@Table(name = "resource_metrics_history",
        indexes = {
            @Index(name = "idx_rmh_deployment_time", columnList = "deployment_id, recorded_at DESC"),
            @Index(name = "idx_rmh_recorded_at", columnList = "recorded_at")
        })
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ResourceMetricsHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "deployment_id", nullable = false)
    private Long deploymentId;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    @Column(name = "cpu_usage_pct")
    private Double cpuUsagePct;

    @Column(name = "memory_usage_pct")
    private Double memoryUsagePct;

    @Column(name = "memory_bytes")
    private Long memoryBytes;

    @Column(name = "memory_limit_bytes")
    private Long memoryLimitBytes;

    @Column(name = "network_in_bytes")
    private Long networkInBytes;

    @Column(name = "network_out_bytes")
    private Long networkOutBytes;

    @Column(name = "restart_count")
    private Integer restartCount;

    @Column(name = "oom_killed")
    private Boolean oomKilled;

    @Column(name = "status", length = 50)
    private String status;

    @Column(name = "resource_type", length = 50)
    private String resourceType;

    @Column(name = "deployment_type", length = 10)
    private String deploymentType;
}
