package kr.co.mcmp.ape.workflowStage.Entity;

import lombok.*;

import javax.persistence.*;

@Entity
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "workflow_stage")
public class WorkflowStage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "workflow_stage_idx")
    private Long workflowStageIdx;

    @ManyToOne
    @JoinColumn(name = "workflow_stage_type_idx", nullable = false)
    private WorkflowStageType workflowStageType;

    @Column(name = "workflow_stage_order")
    private Integer workflowStageOrder;

    @Column(name = "workflow_stage_name", nullable = false)
    private String workflowStageName;

    @Column(name = "workflow_stage_desc")
    private String workflowStageDesc;

    @Lob
    @Column(name = "workflow_stage_content", nullable = false, columnDefinition = "CLOB")
    private String workflowStageContent;
}