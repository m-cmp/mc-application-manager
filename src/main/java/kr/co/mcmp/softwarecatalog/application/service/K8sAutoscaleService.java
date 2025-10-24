package kr.co.mcmp.softwarecatalog.application.service;

import kr.co.mcmp.ape.cbtumblebug.dto.K8sNodeGroupAutoscaleRequest;

/**
 * K8S 노드 오토스케일링 서비스 인터페이스
 */
public interface K8sAutoscaleService {
    
    /**
     * K8S 노드 그룹의 오토스케일 크기를 변경합니다.
     * 
     * @param nsId 네임스페이스 ID
     * @param k8sClusterId K8S 클러스터 ID
     * @param k8sNodeGroupName K8S 노드 그룹 이름
     * @param request 오토스케일 요청 정보
     * @return 변경 결과
     */
    boolean changeNodeGroupAutoscaleSize(String nsId, String k8sClusterId, String k8sNodeGroupName, 
                                       K8sNodeGroupAutoscaleRequest request);
    
    /**
     * CPU/메모리 사용률이 임계값을 초과한 경우 노드를 스케일 아웃합니다.
     * 
     * @param namespace 네임스페이스
     * @param clusterName 클러스터 이름
     * @param nodeGroupName 노드 그룹 이름
     * @param currentSize 현재 노드 수
     * @param maxSize 최대 노드 수
     * @return 스케일 아웃 결과
     */
    boolean scaleOutNodeGroup(String namespace, String clusterName, String nodeGroupName, 
                             int currentSize, int maxSize);
    
    // 스케일 인 기능은 제거됨 (요청사항에 따라)
}
