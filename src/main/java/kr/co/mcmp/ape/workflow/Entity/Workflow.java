package kr.co.mcmp.ape.workflow.Entity;

import kr.co.mcmp.oss.entity.Oss;
import lombok.*;

import javax.persistence.*;

@Entity
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "workflow")
public class Workflow {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "workflow_idx")
    private Long workflowIdx;

    @Column(name = "workflow_name", nullable = false)
    private String workflowName;

    @Column(name = "workflow_purpose", nullable = false)
    private String workflowPurpose;

    @ManyToOne
    @JoinColumn(name = "oss_idx", nullable = false)
    private Oss oss;

    @Lob
    @Column(name = "script", columnDefinition = "CLOB")
    private String script;
}