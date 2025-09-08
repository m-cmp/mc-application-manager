package kr.co.mcmp.softwarecatalog.application.service;

import kr.co.mcmp.softwarecatalog.application.constants.DeploymentType;
import kr.co.mcmp.softwarecatalog.application.dto.DeploymentRequest;
import kr.co.mcmp.softwarecatalog.application.model.DeploymentHistory;

/**
 * 배포 관련 작업을 담당하는 서비스 인터페이스
 */
public interface DeploymentService {
    
    /**
     * 애플리케이션을 배포합니다.
     * 
     * @param request 배포 요청 정보
     * @return 배포 이력
     */
    DeploymentHistory deployApplication(DeploymentRequest request);
    
    /**
     * 배포 타입에 따른 적절한 배포 서비스를 반환합니다.
     * 
     * @param deploymentType 배포 타입
     * @return 배포 서비스
     */
    DeploymentService getDeploymentService(DeploymentType deploymentType);
}


