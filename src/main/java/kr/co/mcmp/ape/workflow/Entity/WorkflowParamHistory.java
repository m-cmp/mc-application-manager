package kr.co.mcmp.ape.workflow.Entity;

import lombok.*;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "workflow_param_history")
public class WorkflowParamHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "workflow_param_history_idx")
    private Long workflowParamHistoryIdx;

    @ManyToOne
    @JoinColumn(name = "workflow_idx", nullable = false)
    private Workflow workflow;

    @Column(name = "run_user_id", nullable = false)
    private String runUserId;

    @Column(name = "run_date")
    private LocalDateTime runDate;
}