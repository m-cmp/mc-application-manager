package kr.co.mcmp.softwarecatalog.application.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "SCALING_EVENT")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScalingEvent {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "deployment_id")
    private Long deploymentId;
    
    @Column(name = "namespace")
    private String namespace;
    
    @Column(name = "cluster_name")
    private String clusterName;
    
    @Column(name = "node_group_name")
    private String nodeGroupName;
    
    @Column(name = "old_node_count")
    private Integer oldNodeCount;
    
    @Column(name = "new_node_count")
    private Integer newNodeCount;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "scaling_type")
    private ScalingType scalingType;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private ScalingStatus status;
    
    @Column(name = "triggered_at")
    private LocalDateTime triggeredAt;
    
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    
    @Column(name = "error_message")
    private String errorMessage;
    
    public enum ScalingType {
        SCALE_OUT, SCALE_IN
    }
    
    public enum ScalingStatus {
        PENDING, IN_PROGRESS, COMPLETED, FAILED
    }
}
