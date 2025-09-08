package kr.co.mcmp.softwarecatalog.application.service;

import java.util.Map;

import kr.co.mcmp.softwarecatalog.application.constants.ActionType;

/**
 * 애플리케이션 운영 작업을 담당하는 서비스 인터페이스
 */
public interface ApplicationOperationService {
    
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
     * 지원하는 배포 타입을 반환합니다.
     * 
     * @return 지원하는 배포 타입
     */
    kr.co.mcmp.softwarecatalog.application.constants.DeploymentType getSupportedDeploymentType();
}


