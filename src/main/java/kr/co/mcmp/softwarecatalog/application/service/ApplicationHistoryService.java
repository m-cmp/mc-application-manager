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
     * @param actionType 작업 타입
     */
    void insertOperationHistory(ApplicationStatus applicationStatus, String username, String reason, ActionType actionType);
    
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


