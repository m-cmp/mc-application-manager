package kr.co.mcmp.softwarecatalog.application.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.co.mcmp.softwarecatalog.application.service.ApplicationService;
import kr.co.mcmp.softwarecatalog.application.service.NexusIntegrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 애플리케이션 등록 및 수정을 담당하는 서비스 구현체
 * 배포 및 운영 작업은 ApplicationOrchestrationService를 사용하세요
 */
@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class ApplicationServiceImpl implements ApplicationService {

    private final NexusIntegrationService nexusIntegrationService;

    // ===== 넥서스 연동 관련 메서드 (애플리케이션 배포/운영용) =====
    
    /**
     * 넥서스에서 애플리케이션을 조회합니다.
     * 
     * @param applicationName 애플리케이션 이름
     * @return 넥서스 애플리케이션 정보
     */
    @Override
    public Object getApplicationFromNexus(String applicationName) {
        log.info("Getting application from Nexus: {}", applicationName);
        
        return nexusIntegrationService.getFromNexus(applicationName);
    }
    
    /**
     * 넥서스에서 모든 애플리케이션을 조회합니다.
     * 
     * @return 넥서스 애플리케이션 목록
     */
    @Override
    public List<Object> getAllApplicationsFromNexus() {
        log.info("Getting all applications from Nexus");
        
        return (List<Object>) (List<?>) nexusIntegrationService.getAllFromNexus();
    }
    
    /**
     * 넥서스에서 이미지 태그 목록을 조회합니다.
     * 
     * @param imageName 이미지 이름
     * @return 태그 목록
     */
    @Override
    public List<String> getImageTagsFromNexus(String imageName) {
        log.info("Getting image tags from Nexus: {}", imageName);
        
        return nexusIntegrationService.getImageTagsFromNexus(imageName);
    }
    
    /**
     * 넥서스에서 이미지를 풀합니다.
     * 
     * @param imageName 이미지 이름
     * @param tag 이미지 태그
     * @return 풀 결과
     */
    @Override
    public Object pullImageFromNexus(String imageName, String tag) {
        log.info("Pulling image from Nexus: {}:{}", imageName, tag);
        
        return nexusIntegrationService.pullImageFromNexus(imageName, tag);
    }
}
