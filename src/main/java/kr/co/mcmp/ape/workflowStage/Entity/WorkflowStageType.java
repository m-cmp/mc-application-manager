package kr.co.mcmp.ape.workflowStage.Entity;

import lombok.*;

import javax.persistence.*;

@Entity
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "workflow_stage_type")
public class WorkflowStageType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "workflow_stage_type_idx")
    private Long workflowStageTypeIdx;

    @Column(name = "workflow_stage_type_name", nullable = false)
    private String workflowStageTypeName;

    @Column(name = "workflow_stage_type_desc")
    private String workflowStageTypeDesc;
}
