package kr.co.mcmp.softwarecatalog.application.model;

import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import kr.co.mcmp.softwarecatalog.application.dto.UnifiedLogDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 통합 로그 엔티티 (쿠버네티스, 도커, 애플리케이션 로그 통합)
 */
@Entity
@Table(name = "UNIFIED_LOG")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnifiedLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * 로그 발생 시간
     */
    @Column(name = "LOGGED_AT", nullable = false)
    private LocalDateTime loggedAt;
    
    /**
     * 에러 코드
     */
    @Column(name = "ERROR_CODE", length = 50)
    private String errorCode;
    
    /**
     * 심각도 (ERROR, WARN, INFO, DEBUG)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "SEVERITY", length = 20, nullable = false)
    private LogSeverity severity;
    
    /**
     * 모듈 (KUBERNETES, DOCKER, APPLICATION, SYSTEM)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "MODULE", length = 20, nullable = false)
    private LogSourceType module;
    
    /**
     * 로그 메시지
     */
    @Column(name = "LOG_MESSAGE", length = 2000)
    private String logMessage;
    
    /**
     * 배포 이력 (외래키)
     */
    @ManyToOne
    @JoinColumn(name = "DEPLOYMENT_ID")
    private DeploymentHistory deployment;
    
    /**
     * 애플리케이션 상태 (외래키)
     */
    @ManyToOne
    @JoinColumn(name = "APPLICATION_STATUS_ID")
    private ApplicationStatus applicationStatus;
    
    /**
     * 네임스페이스 (쿠버네티스용)
     */
    @Column(name = "NAMESPACE", length = 100)
    private String namespace;
    
    /**
     * 파드 이름 (쿠버네티스용)
     */
    @Column(name = "POD_NAME", length = 200)
    private String podName;
    
    /**
     * 컨테이너 이름 (도커/쿠버네티스용)
     */
    @Column(name = "CONTAINER_NAME", length = 200)
    private String containerName;
    
    /**
     * 클러스터 이름 (쿠버네티스용)
     */
    @Column(name = "CLUSTER_NAME", length = 100)
    private String clusterName;
    
    /**
     * VM ID (도커용)
     */
    @Column(name = "VM_ID", length = 100)
    private String vmId;
    
    /**
     * 추가 메타데이터 (JSON 형태)
     */
    @Column(name = "METADATA", length = 1000)
    private String metadata;
    
    /**
     * DTO로 변환
     */
    public UnifiedLogDTO toDTO() {
        return UnifiedLogDTO.builder()
                .id(this.id)
                .loggedAt(this.loggedAt)
                .errorCode(this.errorCode)
                .severity(this.severity != null ? this.severity.getValue() : null)
                .module(this.module != null ? this.module.getValue() : null)
                .logMessage(this.logMessage)
                .deploymentId(this.deployment != null ? this.deployment.getId() : null)
                .applicationStatusId(this.applicationStatus != null ? this.applicationStatus.getId() : null)
                .namespace(this.namespace)
                .podName(this.podName)
                .containerName(this.containerName)
                .clusterName(this.clusterName)
                .vmId(this.vmId)
                .metadata(this.metadata)
                .build();
    }
    
    /**
     * DTO에서 엔티티로 변환
     */
    public static UnifiedLog fromDTO(UnifiedLogDTO dto) {
        return UnifiedLog.builder()
                .id(dto.getId())
                .loggedAt(dto.getLoggedAt())
                .errorCode(dto.getErrorCode())
                .severity(dto.getSeverity() != null ? 
                    LogSeverity.valueOf(dto.getSeverity()) : null)
                .module(dto.getModule() != null ? 
                    LogSourceType.valueOf(dto.getModule()) : null)
                .logMessage(dto.getLogMessage())
                .namespace(dto.getNamespace())
                .podName(dto.getPodName())
                .containerName(dto.getContainerName())
                .clusterName(dto.getClusterName())
                .vmId(dto.getVmId())
                .metadata(dto.getMetadata())
                .build();
    }
    
    /**
     * 로그 심각도 열거형
     */
    public enum LogSeverity {
        ERROR("ERROR"),
        WARN("WARN"),
        INFO("INFO"),
        DEBUG("DEBUG");
        
        private final String value;
        
        LogSeverity(String value) {
            this.value = value;
        }
        
        public String getValue() {
            return value;
        }
    }
    
    /**
     * 로그 소스 타입 열거형
     */
    public enum LogSourceType {
        KUBERNETES("KUBERNETES"),
        DOCKER("DOCKER"),
        APPLICATION("APPLICATION"),
        SYSTEM("SYSTEM");
        
        private final String value;
        
        LogSourceType(String value) {
            this.value = value;
        }
        
        public String getValue() {
            return value;
        }
    }
}
