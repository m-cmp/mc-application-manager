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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "operation_profile_analysis",
        indexes = {
            @Index(name = "idx_operation_profile_deployment_created", columnList = "deployment_id, created_at DESC"),
            @Index(name = "idx_operation_profile_period", columnList = "analysis_start_date, analysis_end_date")
        })
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class OperationProfileAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "deployment_id", nullable = false)
    private Long deploymentId;

    @Column(name = "analysis_start_date", nullable = false)
    private LocalDate analysisStartDate;

    @Column(name = "analysis_end_date", nullable = false)
    private LocalDate analysisEndDate;

    @Column(name = "selected_resource_type", length = 50)
    private String selectedResourceType;

    @Column(name = "recommended_resource_type", length = 50)
    private String recommendedResourceType;

    @Column(name = "cpu_sizing_status", length = 50)
    private String cpuSizingStatus;

    @Column(name = "memory_sizing_status", length = 50)
    private String memorySizingStatus;

    @Column(name = "data_status", length = 50)
    private String dataStatus;

    @Column(name = "valid_days")
    private Integer validDays;

    @Column(name = "missing_days")
    private Integer missingDays;

    @Column(name = "sample_count")
    private Integer sampleCount;

    @Column(name = "cpu_pressure_p95")
    private Double cpuPressureP95;

    @Column(name = "memory_pressure_p95")
    private Double memoryPressureP95;

    @Column(name = "max_cpu_pressure")
    private Double maxCpuPressure;

    @Column(name = "max_memory_pressure")
    private Double maxMemoryPressure;

    @Column(name = "oom_count")
    private Integer oomCount;

    @Column(name = "restart_count")
    private Integer restartCount;

    @Column(name = "crash_loop_count")
    private Integer crashLoopCount;

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "reasons", columnDefinition = "TEXT")
    private String reasons;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
