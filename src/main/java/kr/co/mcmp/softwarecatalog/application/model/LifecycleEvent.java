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
 * Structured lifecycle event table.
 * Stores events worth analyzing: OOM Killed, CrashLoopBackOff, abnormal restarts, etc.
 * Retained for 6 months, then purged.
 */
@Entity
@Table(name = "lifecycle_event",
        indexes = {
            @Index(name = "idx_lifecycle_deployment_time", columnList = "deployment_id, occurred_at DESC"),
            @Index(name = "idx_lifecycle_event_type", columnList = "event_type, occurred_at DESC")
        })
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class LifecycleEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "deployment_id", nullable = false)
    private Long deploymentId;

    /**
     * Event type: OOM_KILLED, CRASH_LOOP, RESTART, IMAGE_PULL_ERROR, SCALE_OUT, SCALE_IN
     */
    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    /**
     * Severity: CRITICAL, WARNING, INFO
     */
    @Column(name = "severity", nullable = false, length = 20)
    private String severity;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "pod_name", length = 255)
    private String podName;

    @Column(name = "container_name", length = 255)
    private String containerName;

    @Column(name = "exit_code")
    private Integer exitCode;

    @Column(name = "detail_message", columnDefinition = "TEXT")
    private String detailMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
