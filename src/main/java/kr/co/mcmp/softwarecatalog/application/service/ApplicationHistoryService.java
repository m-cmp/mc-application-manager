package kr.co.mcmp.softwarecatalog.application.service;

import java.util.List;

import kr.co.mcmp.softwarecatalog.application.constants.ActionType;
import kr.co.mcmp.softwarecatalog.application.constants.LogType;
import kr.co.mcmp.softwarecatalog.application.dto.DeploymentRequest;
import kr.co.mcmp.softwarecatalog.application.model.ApplicationStatus;
import kr.co.mcmp.softwarecatalog.application.model.DeploymentHistory;
import kr.co.mcmp.softwarecatalog.application.model.DeploymentLog;
import kr.co.mcmp.softwarecatalog.users.Entity.User;

/**
 * 애플리케이션 이력 관리를 담당하는 서비스 인터페이스
 */
public interface ApplicationHistoryService {
    
    /**
     * 배포 이력을 생성합니다.
     * 
     * @param request 배포 요청 정보
     * @param user 사용자
     * @return 배포 이력
     */
    DeploymentHistory createDeploymentHistory(DeploymentRequest request, User user);
    
    /**
     * 애플리케이션 상태를 업데이트합니다.
     * 
     * @param history 배포 이력
     * @param status 상태
     * @param user 사용자
     */
    void updateApplicationStatus(DeploymentHistory history, String status, User user);
    
    /**
     * VM별 ApplicationStatus를 생성합니다. (다중 VM 배포용)
     * 
     * @param history 배포 이력
     * @param vmId VM ID
     * @param publicIp 공인 IP
     * @param servicePort 서비스 포트
     * @param status 상태
     * @param user 사용자
     */
    void createApplicationStatusForVm(DeploymentHistory history, String vmId, String publicIp, 
                                    Integer servicePort, String status, User user);
    
    /**
     * VM별 DeploymentHistory를 생성합니다. (다중 VM 배포용)
     * 
     * @param request 배포 요청 정보
     * @param vmId VM ID
     * @param user 사용자
     * @return VM별 배포 이력
     */
    DeploymentHistory createDeploymentHistoryForVm(DeploymentRequest request, String vmId, User user);
    
    /**
     * 특정 VM에 기존 설치가 있는지 확인합니다.
     * 
     * @param namespace 네임스페이스
     * @param mciId MCI ID
     * @param vmId VM ID
     * @param catalogId 카탈로그 ID
     * @return 기존 설치 여부
     */
    boolean hasExistingInstallation(String namespace, String mciId, String vmId, Long catalogId);
    
    /**
     * 배포 로그를 추가합니다.
     * 
     * @param history 배포 이력
     * @param logType 로그 타입
     * @param message 로그 메시지
     */
    void addDeploymentLog(DeploymentHistory history, LogType logType, String message);
    
    /**
     * 운영 이력을 추가합니다.
     * 
     * @param applicationStatus 애플리케이션 상태
     * @param username 사용자명
     * @param reason 작업 사유
     * @param detailReason 상세 작업 사유
     * @param actionType 작업 타입
     */
    void insertOperationHistory(ApplicationStatus applicationStatus, String username, String reason, String detailReason, ActionType actionType);
    
    /**
     * 배포 이력 목록을 조회합니다.
     * 
     * @param catalogId 카탈로그 ID
     * @param username 사용자명
     * @return 배포 이력 목록
     */
    List<DeploymentHistory> getDeploymentHistories(Long catalogId, String username);
    
    /**
     * 배포 로그 목록을 조회합니다.
     * 
     * @param deploymentId 배포 ID
     * @param username 사용자명
     * @return 배포 로그 목록
     */
    List<DeploymentLog> getDeploymentLogs(Long deploymentId, String username);
}


