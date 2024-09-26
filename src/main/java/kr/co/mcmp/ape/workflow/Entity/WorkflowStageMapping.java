package kr.co.mcmp.ape.workflow.Entity;

import lombok.*;

import javax.persistence.*;

@Entity
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "workflow_stage_mapping")
public class WorkflowStageMapping {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "mapping_idx")
    private Long mappingIdx;

//    @ManyToOne(cascade = CascadeType.ALL)
    @ManyToOne
    @JoinColumn(name = "workflow_idx", nullable = false)
    private Workflow workflow;

    @Column(name = "stage_order")
    private Integer stageOrder;

    @Column(name = "workflow_stage_idx")
    private Long workflowStageIdx;

    @Lob
    @Column(name = "stage", columnDefinition = "CLOB")
    private String stageContent;
}