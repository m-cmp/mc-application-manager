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
 * Infrastructure spec snapshot at deployment time.
 * One row per deployment; serves as the baseline for over/under-provisioning analysis.
 * Retained permanently (small data volume, no purge needed).
 */
@Entity
@Table(name = "infra_spec_snapshot",
        indexes = {
            @Index(name = "idx_infra_spec_deployment", columnList = "deployment_id")
        })
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class InfraSpecSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "deployment_id", nullable = false, unique = true)
    private Long deploymentId;

    @Column(name = "captured_at", nullable = false)
    private LocalDateTime capturedAt;

    /**
     * Resource type selected at deploy time: CPU_INTENSIVE, MEMORY_INTENSIVE, GENERAL_PURPOSE
     */
    @Column(name = "resource_type", length = 50)
    private String resourceType;

    @Column(name = "deployment_type", length = 10)
    private String deploymentType;

    // Docker (VM-based) spec
    @Column(name = "vm_instance_type", length = 100)
    private String vmInstanceType;

    @Column(name = "vm_cpu_cores")
    private Integer vmCpuCores;

    @Column(name = "vm_memory_gb")
    private Double vmMemoryGb;

    // Kubernetes (Pod-based) spec
    @Column(name = "pod_cpu_request", length = 20)
    private String podCpuRequest;

    @Column(name = "pod_cpu_limit", length = 20)
    private String podCpuLimit;

    @Column(name = "pod_memory_request", length = 20)
    private String podMemoryRequest;

    @Column(name = "pod_memory_limit", length = 20)
    private String podMemoryLimit;

    // Catalog recommended spec (denormalized — comparable without JOIN)
    @Column(name = "catalog_min_cpu")
    private Double catalogMinCpu;

    @Column(name = "catalog_rec_cpu")
    private Double catalogRecCpu;

    @Column(name = "catalog_min_memory_mb")
    private Integer catalogMinMemoryMb;
}
