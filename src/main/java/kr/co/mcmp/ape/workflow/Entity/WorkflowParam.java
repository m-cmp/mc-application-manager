package kr.co.mcmp.ape.workflow.Entity;

import lombok.*;
import org.hibernate.annotations.ColumnDefault;

import javax.persistence.*;

@Entity
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "workflow_param")
public class WorkflowParam {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "param_idx")
    private Long paramIdx;

    @ManyToOne
    @JoinColumn(name = "workflow_idx", nullable = false)
    private Workflow workflow;

    @Column(name = "param_key")
    private String paramKey;

    @Column(name = "param_value")
    private String paramValue;

    @Column(name = "event_listener_yn")
    private String eventListenerYn;

    @PrePersist
    public void prePersist() {
        if (this.eventListenerYn == null) {
            this.eventListenerYn = "N";
        }
    }
}