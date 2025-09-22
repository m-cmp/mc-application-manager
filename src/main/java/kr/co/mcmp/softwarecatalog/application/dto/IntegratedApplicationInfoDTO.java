package kr.co.mcmp.softwarecatalog.application.dto;

import java.time.LocalDateTime;
import java.util.List;

import kr.co.mcmp.softwarecatalog.application.constants.ActionType;
import kr.co.mcmp.softwarecatalog.application.constants.DeploymentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 통합 애플리케이션 정보 DTO
 * catalog 정보 중복을 제거하고 필요한 정보만 포함
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntegratedApplicationInfoDTO {
    
    // 기본 배포 정보
    private Long deploymentId;
    private Long catalogId;
    private String catalogName;
    private String catalogDescription;
    private String catalogCategory;
    private Integer defaultPort;
    private String logoUrlLarge;
    private String logoUrlSmall;
    
    // 배포 상세 정보
    private DeploymentType deploymentType;
    private String namespace;
    private String clusterName;
    private String mciId;
    private String vmId;
    private String publicIp;
    private ActionType actionType;
    private String status;
    private LocalDateTime executedAt;
    private String executedBy;
    private String cloudProvider;
    private String cloudRegion;
    private Integer servicePort;
    private String podStatus;
    private String releaseName;
    
    // 애플리케이션 상태 정보
    private String applicationStatus;
    private Double cpuUsage;
    private Double memoryUsage;
    private Double networkIn;
    private Double networkOut;
    private Boolean healthCheck;
    private Boolean portAccessible;
    private LocalDateTime lastCheckedAt;
    
    // Ingress 정보
    private Boolean ingressEnabled;
    private String ingressHost;
    private String ingressPath;
    private String ingressClass;
    private Boolean ingressTlsEnabled;
    private String ingressTlsSecret;
    
    // 로그 정보
    private List<DeploymentLogSummaryDTO> deploymentLogs;
    private List<OperationHistorySummaryDTO> operationHistories;
    private ErrorLogsDTO errorLogs;
    private PodLogsDTO podLogs;
    // private List<String> infoLogs;
    // private List<String> debugLogs;
    
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeploymentLogSummaryDTO {
        private Long id;
        private String logType;
        private String logMessage;
        private LocalDateTime loggedAt;
    }
    
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OperationHistorySummaryDTO {
        private Long id;
        private String operationType;
        private String reason;
        private String detailReason;
        private String status;
        private LocalDateTime executedAt;
        private String executedBy;
    }
    
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorLogsDTO {
        private List<ErrorLogItemDTO> logs;
        private Integer totalCount;
        private String lastErrorTime;
        private String severity;
    }
    
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorLogItemDTO {
        private Long id;
        private String logMessage;
        private LocalDateTime loggedAt;
        private String errorCode;
        private String severity;
        private String module;
        private String podName;
        private String containerName;
    }
    
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PodLogsDTO {
        private List<PodLogItemDTO> logs;
        private Integer totalCount;
        private String lastLogTime;
        private String podStatus;
        private String podName;
    }
    
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PodLogItemDTO {
        private Long id;
        private String logMessage;
        private LocalDateTime loggedAt;
        private String severity;
        private String module;
        private String podName;
        private String containerName;
        private String namespace;
    }
}
