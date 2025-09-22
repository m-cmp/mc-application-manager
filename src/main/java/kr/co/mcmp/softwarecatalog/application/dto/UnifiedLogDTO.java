package kr.co.mcmp.softwarecatalog.application.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 통합 로그 DTO (쿠버네티스, 도커, 애플리케이션 로그 통합)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnifiedLogDTO {
    
    /**
     * 로그 ID
     */
    private Long id;
    
    /**
     * 로그 발생 시간
     */
    private LocalDateTime loggedAt;
    
    /**
     * 에러 코드
     */
    private String errorCode;
    
    /**
     * 심각도 (ERROR, WARN, INFO, DEBUG)
     */
    private String severity;
    
    /**
     * 모듈 (KUBERNETES, DOCKER, APPLICATION, SYSTEM)
     */
    private String module;
    
    /**
     * 로그 메시지
     */
    private String logMessage;
    
    /**
     * 배포 ID (외래키)
     */
    private Long deploymentId;
    
    /**
     * 애플리케이션 상태 ID (외래키)
     */
    private Long applicationStatusId;
    
    /**
     * 네임스페이스 (쿠버네티스용)
     */
    private String namespace;
    
    /**
     * 파드 이름 (쿠버네티스용)
     */
    private String podName;
    
    /**
     * 컨테이너 이름 (도커/쿠버네티스용)
     */
    private String containerName;
    
    /**
     * 클러스터 이름 (쿠버네티스용)
     */
    private String clusterName;
    
    /**
     * VM ID (도커용)
     */
    private String vmId;
    
    /**
     * 추가 메타데이터 (JSON 형태)
     */
    private String metadata;
    
    /**
     * 로그 소스 타입
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
    
    /**
     * 로그 심각도 레벨
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
}
