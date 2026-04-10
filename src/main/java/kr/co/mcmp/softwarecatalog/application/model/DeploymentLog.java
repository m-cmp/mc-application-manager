package kr.co.mcmp.softwarecatalog.application.model;

import java.time.LocalDateTime;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import kr.co.mcmp.softwarecatalog.application.constants.LogType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "DEPLOYMENT_LOG")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeploymentLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // 배포 로그의 고유 식별자

    @ManyToOne(cascade = CascadeType.PERSIST)
    @JoinColumn(name = "deployment_id")
    private DeploymentHistory deployment; // 이 로그가 속한 배포 이력

    @Enumerated(EnumType.STRING)
    @Column(name = "log_type", nullable = false)
    private LogType logType; // 로그 유형 (예: INFO, WARNING, ERROR)

    @Column(name = "log_message", columnDefinition = "TEXT")
    private String logMessage; // 로그 메시지 내용

    @Column(name = "logged_at")
    private LocalDateTime loggedAt; // 로그 기록 시간

}