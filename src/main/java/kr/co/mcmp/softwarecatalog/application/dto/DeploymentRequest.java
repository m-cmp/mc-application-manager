package kr.co.mcmp.softwarecatalog.application.dto;

import kr.co.mcmp.softwarecatalog.application.constants.DeploymentType;
import kr.co.mcmp.softwarecatalog.application.constants.VmDeploymentMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

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
     * VM IDs (VM 배포시 - 단일/다중 VM 모두 지원)
     */
    private List<String> vmIds;
    
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
     * VM 배포 방식 (Standalone/Clustering)
     */
    private VmDeploymentMode vmDeploymentMode;
    
    /**
     * 추가 설정 정보
     */
    private java.util.Map<String, Object> additionalConfig;
    
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
     * VM 배포 요청을 생성합니다. (단일 VM)
     */
    public static DeploymentRequest forVm(String namespace, String mciId, String vmId, 
                                        Long catalogId, Integer servicePort, String username) {
        return DeploymentRequest.builder()
                .namespace(namespace)
                .mciId(mciId)
                .vmIds(List.of(vmId))
                .catalogId(catalogId)
                .servicePort(servicePort)
                .username(username)
                .deploymentType(DeploymentType.VM)
                .vmDeploymentMode(VmDeploymentMode.STANDALONE)
                .build();
    }
    
    /**
     * VM 배포 요청을 생성합니다. (다중 VM)
     */
    public static DeploymentRequest forVmMultiple(String namespace, String mciId, List<String> vmIds, 
                                                Long catalogId, Integer servicePort, String username, 
                                                VmDeploymentMode deploymentMode) {
        return DeploymentRequest.builder()
                .namespace(namespace)
                .mciId(mciId)
                .vmIds(vmIds)
                .catalogId(catalogId)
                .servicePort(servicePort)
                .username(username)
                .deploymentType(DeploymentType.VM)
                .vmDeploymentMode(deploymentMode)
                .build();
    }
    
    /**
     * VM 배포 요청을 생성합니다. (단일/다중 VM 자동 판단)
     */
    public static DeploymentRequest forVm(String namespace, String mciId, List<String> vmIds, 
                                        Long catalogId, Integer servicePort, String username, 
                                        VmDeploymentMode deploymentMode) {
        return DeploymentRequest.builder()
                .namespace(namespace)
                .mciId(mciId)
                .vmIds(vmIds)
                .catalogId(catalogId)
                .servicePort(servicePort)
                .username(username)
                .deploymentType(DeploymentType.VM)
                .vmDeploymentMode(deploymentMode)
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
    
    /**
     * VM 개수가 1개인지 확인합니다.
     */
    public boolean isSingleVm() {
        return vmIds != null && vmIds.size() == 1;
    }
    
    /**
     * VM 개수가 여러개인지 확인합니다.
     */
    public boolean isMultipleVm() {
        return vmIds != null && vmIds.size() > 1;
    }
    
    /**
     * 첫 번째 VM ID를 반환합니다. (단일 VM인 경우)
     */
    public String getFirstVmId() {
        return (vmIds != null && !vmIds.isEmpty()) ? vmIds.get(0) : null;
    }
    
    /**
     * VM 개수를 반환합니다.
     */
    public int getVmCount() {
        return vmIds != null ? vmIds.size() : 0;
    }
}


