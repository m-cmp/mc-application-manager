package kr.co.mcmp.softwarecatalog.application.service;

import java.util.List;
import java.util.Map;

import kr.co.mcmp.softwarecatalog.application.constants.ActionType;
import kr.co.mcmp.softwarecatalog.application.dto.ApplicationStatusDto;
import kr.co.mcmp.softwarecatalog.application.dto.DeploymentRequest;
import kr.co.mcmp.softwarecatalog.application.model.DeploymentHistory;
import kr.co.mcmp.softwarecatalog.application.model.DeploymentLog;
import kr.co.mcmp.ape.cbtumblebug.dto.K8sSpec;
import kr.co.mcmp.ape.cbtumblebug.dto.Spec;

/**
 * 애플리케이션 오케스트레이션을 담당하는 메인 서비스 인터페이스
 * 다양한 배포 타입과 운영을 통합 관리
 */
public interface ApplicationOrchestrationService {
    
    /**
     * 애플리케이션을 배포합니다.
     * 
     * @param request 배포 요청 정보
     * @return 배포 이력
     */
    DeploymentHistory deployApplication(DeploymentRequest request);
    
    /**
     * 애플리케이션 운영을 수행합니다.
     * 
     * @param operation 수행할 작업
     * @param applicationStatusId 애플리케이션 상태 ID
     * @param reason 작업 사유
     * @param username 사용자명
     * @return 작업 결과
     */
    Map<String, Object> performOperation(ActionType operation, Long applicationStatusId, String reason, String username);
    
    /**
     * 애플리케이션 운영을 수행합니다. (상세 사유 포함)
     * 
     * @param operation 수행할 작업
     * @param applicationStatusId 애플리케이션 상태 ID
     * @param reason 작업 사유
     * @param detailReason 상세 사유
     * @param username 사용자명
     * @return 작업 결과
     */
    Map<String, Object> performOperation(ActionType operation, Long applicationStatusId, String reason, String detailReason, String username);
    
    /**
     * 애플리케이션 그룹 목록을 조회합니다.
     * 
     * @return 애플리케이션 상태 목록
     */
    List<ApplicationStatusDto> getApplicationGroups();
    
    /**
     * 배포 이력을 조회합니다.
     * 
     * @param catalogId 카탈로그 ID
     * @param username 사용자명
     * @return 배포 이력 목록
     */
    List<DeploymentHistory> getDeploymentHistories(Long catalogId, String username);
    
    /**
     * 배포 로그를 조회합니다.
     * 
     * @param deploymentId 배포 ID
     * @param username 사용자명
     * @return 배포 로그 목록
     */
    List<DeploymentLog> getDeploymentLogs(Long deploymentId, String username);
    
    /**
     * DeploymentHistoryId를 기반으로 애플리케이션을 삭제합니다.
     * 
     * @param deploymentHistoryId 배포 히스토리 ID
     * @param reason 삭제 사유
     * @param username 사용자명
     * @return 삭제 결과
     */
    Map<String, Object> deleteApplicationByDeploymentHistoryId(Long deploymentHistoryId, String reason, String username);
    
    /**
     * 최신 애플리케이션 상태를 조회합니다.
     * 
     * @param username 사용자명
     * @return 애플리케이션 상태
     */
    ApplicationStatusDto getLatestApplicationStatus(String username);
    
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

