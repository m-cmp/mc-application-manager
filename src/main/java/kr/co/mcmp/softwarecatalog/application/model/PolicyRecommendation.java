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
@Table(name = "policy_recommendation",
        indexes = {
            @Index(name = "idx_policy_recommendation_deployment_created", columnList = "deployment_id, created_at DESC"),
            @Index(name = "idx_policy_recommendation_status", columnList = "status, created_at DESC")
        })
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PolicyRecommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "deployment_id", nullable = false)
    private Long deploymentId;

    @Column(name = "analysis_id")
    private Long analysisId;

    @Column(name = "selected_resource_type", length = 50)
    private String selectedResourceType;

    @Column(name = "recommended_resource_type", length = 50)
    private String recommendedResourceType;

    @Column(name = "mismatch")
    private Boolean mismatch;

    @Column(name = "actions", columnDefinition = "TEXT")
    private String actions;

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "decided_by", length = 100)
    private String decidedBy;

    @Column(name = "decision_reason", columnDefinition = "TEXT")
    private String decisionReason;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
