package kr.co.mcmp.softwarecatalog.application.dto;

import kr.co.mcmp.softwarecatalog.application.constants.DeploymentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 배포 요청 정보를 담는 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeploymentRequest {
    
    /**
     * 네임스페이스
     */
    private String namespace;
    
    /**
     * MCI ID (VM 배포시)
     */
    private String mciId;
    
    /**
     * VM ID (VM 배포시)
     */
    private String vmId;
    
    /**
     * 클러스터명 (Kubernetes 배포시)
     */
    private String clusterName;
    
    /**
     * 카탈로그 ID
     */
    private Long catalogId;
    
    /**
     * 서비스 포트
     */
    private Integer servicePort;
    
    /**
     * 사용자명
     */
    private String username;
    
    /**
     * 배포 타입
     */
    private DeploymentType deploymentType;
    
    /**
     * 추가 설정 정보
     */
    private java.util.Map<String, Object> additionalConfig;
    
    /**
     * VM 배포 요청을 생성합니다.
     */
    public static DeploymentRequest forVm(String namespace, String mciId, String vmId, 
                                        Long catalogId, Integer servicePort, String username) {
        return DeploymentRequest.builder()
                .namespace(namespace)
                .mciId(mciId)
                .vmId(vmId)
                .catalogId(catalogId)
                .servicePort(servicePort)
                .username(username)
                .deploymentType(DeploymentType.VM)
                .build();
    }
    
    /**
     * Kubernetes 배포 요청을 생성합니다.
     */
    public static DeploymentRequest forKubernetes(String namespace, String clusterName, 
                                                Long catalogId, String username) {
        return DeploymentRequest.builder()
                .namespace(namespace)
                .clusterName(clusterName)
                .catalogId(catalogId)
                .username(username)
                .deploymentType(DeploymentType.K8S)
                .build();
    }
}


