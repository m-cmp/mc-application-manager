package kr.co.mcmp.softwarecatalog.application.dto;

import kr.co.mcmp.softwarecatalog.application.constants.DeploymentType;
import kr.co.mcmp.softwarecatalog.application.constants.VmDeploymentMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

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
    private List<String> vmIds;     // VM 배포시 (단일/다중 VM 모두 지원)
    private String clusterName;     // K8s 배포시
    private Long catalogId;
    private Integer servicePort;    // VM 배포시
    private String username;
    private DeploymentType deploymentType;
    private VmDeploymentMode vmDeploymentMode;  // VM 배포 방식 (Standalone/Clustering)
    
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
                .vmIds(this.vmIds)
                .clusterName(this.clusterName)
                .catalogId(this.catalogId)
                .servicePort(this.servicePort)
                .username(this.username)
                .deploymentType(this.deploymentType)
                .vmDeploymentMode(this.vmDeploymentMode)
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
