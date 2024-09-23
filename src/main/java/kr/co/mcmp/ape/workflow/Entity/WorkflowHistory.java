package kr.co.mcmp.ape.workflow.Entity;

import lombok.*;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "workflow_history")
public class WorkflowHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "workflow_history_idx")
    private Long workflowHistoryIdx;

    @ManyToOne
    @JoinColumn(name = "workflow_idx", nullable = false)
    private Workflow workflow;

    @Column(name = "run_script")
    private String runScript;

    @Column(name = "run_user_id", nullable = false)
    private String runUserId;

    @Column(name = "run_date")
    private LocalDateTime runDate;
}