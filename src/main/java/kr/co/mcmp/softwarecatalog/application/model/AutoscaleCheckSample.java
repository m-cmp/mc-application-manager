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

@Entity
@Table(name = "autoscale_check_sample",
        indexes = {
            @Index(name = "idx_acs_deployment_time", columnList = "deployment_id, checked_at DESC"),
            @Index(name = "idx_acs_checked_at", columnList = "checked_at")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutoscaleCheckSample {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "deployment_id", nullable = false)
    private Long deploymentId;

    @Column(name = "checked_at", nullable = false)
    private LocalDateTime checkedAt;

    @Column(name = "cpu_usage_pct")
    private Double cpuUsagePct;

    @Column(name = "memory_usage_pct")
    private Double memoryUsagePct;

    @Column(name = "cpu_threshold")
    private Double cpuThreshold;

    @Column(name = "memory_threshold")
    private Double memoryThreshold;

    @Column(name = "cpu_exceeded")
    private Boolean cpuExceeded;

    @Column(name = "memory_exceeded")
    private Boolean memoryExceeded;

    @Column(name = "threshold_exceeded", nullable = false)
    private Boolean thresholdExceeded;

    @Column(name = "deployment_type", length = 10)
    private String deploymentType;
}
