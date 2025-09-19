package kr.co.mcmp.softwarecatalog.application.dto;

import kr.co.mcmp.softwarecatalog.application.constants.DeploymentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 배포 요청을 받는 DTO (API 요청용)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeploymentRequestDTO {
    
    // 기본 배포 정보
    private String namespace;
    private String mciId;           // VM 배포시
    private String vmId;            // VM 배포시
    private String clusterName;     // K8s 배포시
    private Long catalogId;
    private Integer servicePort;    // VM 배포시
    private String username;
    private DeploymentType deploymentType;
    
    // HPA 설정
    private Boolean hpaEnabled;
    private Integer minReplicas;
    private Integer maxReplicas;
    private Double cpuThreshold;
    private Double memoryThreshold;
    
    // Ingress 설정
    private Boolean ingressEnabled;
    private String ingressHost;
    private String ingressPath;
    private String ingressClass;
    private Boolean ingressTlsEnabled;
    private String ingressTlsSecret;
    
    /**
     * DeploymentRequest로 변환합니다.
     */
    public DeploymentRequest toDeploymentRequest() {
        return DeploymentRequest.builder()
                .namespace(this.namespace)
                .mciId(this.mciId)
                .vmId(this.vmId)
                .clusterName(this.clusterName)
                .catalogId(this.catalogId)
                .servicePort(this.servicePort)
                .username(this.username)
                .deploymentType(this.deploymentType)
                .hpaEnabled(this.hpaEnabled)
                .minReplicas(this.minReplicas)
                .maxReplicas(this.maxReplicas)
                .cpuThreshold(this.cpuThreshold)
                .memoryThreshold(this.memoryThreshold)
                .ingressEnabled(this.ingressEnabled)
                .ingressHost(this.ingressHost)
                .ingressPath(this.ingressPath)
                .ingressClass(this.ingressClass)
                .ingressTlsEnabled(this.ingressTlsEnabled)
                .ingressTlsSecret(this.ingressTlsSecret)
                .build();
    }
}
