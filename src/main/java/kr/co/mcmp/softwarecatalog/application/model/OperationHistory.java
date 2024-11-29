package kr.co.mcmp.softwarecatalog.application.model;

import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import kr.co.mcmp.softwarecatalog.application.constants.ActionType;
import kr.co.mcmp.softwarecatalog.users.Entity.User;
import lombok.Builder;
import lombok.Data;

@Entity
@Data
@Builder
@Table(name ="OPERATION_HISTORY")
public class OperationHistory {

    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "application_status_id")
    private ApplicationStatus applicationStatus; // ApplicationStatus와의 외래키 관계

    @ManyToOne
    @JoinColumn(name = "executed_by")
    private User executedBy;

    @Column(name = "reason")
    private String reason; // 작업 요청 이유

    @Column(name = "operation_type")
    private String operationType; // 작업 유형: 중지, 재시작, 삭제 등

    @Column(name = "created_at")
    private LocalDateTime createdAt; // 작업 요청 시간




}
