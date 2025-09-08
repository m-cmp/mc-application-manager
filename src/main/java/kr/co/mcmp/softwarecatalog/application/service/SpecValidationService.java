package kr.co.mcmp.softwarecatalog.application.service;

import kr.co.mcmp.ape.cbtumblebug.dto.K8sSpec;
import kr.co.mcmp.ape.cbtumblebug.dto.Spec;

/**
 * 스펙 검증을 담당하는 서비스 인터페이스
 */
public interface SpecValidationService {
    
    /**
     * VM 스펙을 검증합니다.
     * 
     * @param namespace 네임스페이스
     * @param mciId MCI ID
     * @param vmId VM ID
     * @param catalogId 카탈로그 ID
     * @return 스펙 충족 여부
     */
    boolean checkSpecForVm(String namespace, String mciId, String vmId, Long catalogId);
    
    /**
     * Kubernetes 스펙을 검증합니다.
     * 
     * @param namespace 네임스페이스
     * @param clusterName 클러스터명
     * @param catalogId 카탈로그 ID
     * @return 스펙 충족 여부
     */
    boolean checkSpecForK8s(String namespace, String clusterName, Long catalogId);
    
    /**
     * VM 스펙 정보를 조회합니다.
     * 
     * @param namespace 네임스페이스
     * @param mciId MCI ID
     * @param vmId VM ID
     * @return VM 스펙 정보
     */
    Spec getSpecForVm(String namespace, String mciId, String vmId);
    
    /**
     * Kubernetes 스펙 정보를 조회합니다.
     * 
     * @param namespace 네임스페이스
     * @param clusterName 클러스터명
     * @return Kubernetes 스펙 정보
     */
    K8sSpec getSpecForK8s(String namespace, String clusterName);
}


