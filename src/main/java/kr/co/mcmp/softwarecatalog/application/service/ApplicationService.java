package kr.co.mcmp.softwarecatalog.application.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.co.mcmp.softwarecatalog.SoftwareCatalogDTO;
import kr.co.mcmp.softwarecatalog.CatalogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 애플리케이션 등록 및 수정을 담당하는 서비스
 * 배포 및 운영 작업은 ApplicationOrchestrationService를 사용하세요
 */
@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class ApplicationService {

    private final CatalogService catalogService;
    private final kr.co.mcmp.softwarecatalog.application.service.NexusIntegrationService nexusIntegrationService;

    /**
     * 소프트웨어 카탈로그를 등록합니다.
     * 
     * @param catalog 등록할 소프트웨어 카탈로그 정보
     * @param username 사용자명
     * @return 등록된 소프트웨어 카탈로그 DTO
     */
    public SoftwareCatalogDTO registerSoftwareCatalog(SoftwareCatalogDTO catalog, String username) {
        log.info("Registering software catalog: {}", catalog.getTitle());
        
        // 1. 내부 DB에 카탈로그 등록
        SoftwareCatalogDTO result = catalogService.createCatalog(catalog, username);
        
        // 2. 넥서스에 애플리케이션 등록
        try {
            nexusIntegrationService.registerToNexus(result);
            log.info("Application registered to Nexus successfully: {}", result.getTitle());
        } catch (Exception e) {
            log.warn("Failed to register application to Nexus: {}", result.getTitle(), e);
            // 넥서스 등록 실패해도 DB 등록은 유지
        }
        
        log.info("Software catalog registered successfully with ID: {}", result.getId());
        return result;
    }
    
    /**
     * 소프트웨어 카탈로그를 수정합니다.
     * 
     * @param catalogId 수정할 카탈로그 ID
     * @param catalog 수정할 소프트웨어 카탈로그 정보
     * @param username 사용자명
     * @return 수정된 소프트웨어 카탈로그 DTO
     */
    public SoftwareCatalogDTO updateSoftwareCatalog(Long catalogId, SoftwareCatalogDTO catalog, String username) {
        log.info("Updating software catalog with ID: {}", catalogId);
        
        SoftwareCatalogDTO result = catalogService.updateCatalog(catalogId, catalog, username);
        
        log.info("Software catalog updated successfully with ID: {}", result.getId());
        return result;
    }
    
    /**
     * 소프트웨어 카탈로그를 삭제합니다.
     * 
     * @param catalogId 삭제할 카탈로그 ID
     */
    public void deleteSoftwareCatalog(Long catalogId) {
        log.info("Deleting software catalog with ID: {}", catalogId);
        
        // 1. 카탈로그 정보 조회 (넥서스 삭제를 위해)
        SoftwareCatalogDTO catalog = catalogService.getCatalog(catalogId);
        
        // 2. 내부 DB에서 카탈로그 삭제
        catalogService.deleteCatalog(catalogId);
        
        // 3. 넥서스에서 애플리케이션 삭제
        try {
            nexusIntegrationService.deleteFromNexus(catalog.getTitle());
            log.info("Application deleted from Nexus successfully: {}", catalog.getTitle());
        } catch (Exception e) {
            log.warn("Failed to delete application from Nexus: {}", catalog.getTitle(), e);
            // 넥서스 삭제 실패해도 DB 삭제는 유지
        }
        
        log.info("Software catalog deleted successfully with ID: {}", catalogId);
    }
    
    /**
     * 소프트웨어 카탈로그를 조회합니다.
     * 
     * @param catalogId 조회할 카탈로그 ID
     * @return 소프트웨어 카탈로그 DTO
     */
    public SoftwareCatalogDTO getSoftwareCatalog(Long catalogId) {
        log.debug("Retrieving software catalog with ID: {}", catalogId);
        
        return catalogService.getCatalog(catalogId);
    }
    
    /**
     * 모든 소프트웨어 카탈로그를 조회합니다.
     * 
     * @return 소프트웨어 카탈로그 DTO 목록
     */
    public List<SoftwareCatalogDTO> getAllSoftwareCatalogs() {
        log.debug("Retrieving all software catalogs");
        
        return catalogService.getAllCatalogs();
    }
    
    // ===== 넥서스 연동 관련 메서드 =====
    
    /**
     * 넥서스에서 애플리케이션을 조회합니다.
     * 
     * @param applicationName 애플리케이션 이름
     * @return 넥서스 애플리케이션 정보
     */
    public Object getApplicationFromNexus(String applicationName) {
        log.debug("Getting application from Nexus: {}", applicationName);
        
        return nexusIntegrationService.getFromNexus(applicationName);
    }
    
    /**
     * 넥서스에서 모든 애플리케이션을 조회합니다.
     * 
     * @return 넥서스 애플리케이션 목록
     */
    public List<Object> getAllApplicationsFromNexus() {
        log.debug("Getting all applications from Nexus");
        
        @SuppressWarnings("unchecked")
        List<Object> result = (List<Object>) (List<?>) nexusIntegrationService.getAllFromNexus();
        return result;
    }
    
    /**
     * 넥서스에서 이미지 태그 목록을 조회합니다.
     * 
     * @param imageName 이미지 이름
     * @return 태그 목록
     */
    public List<String> getImageTagsFromNexus(String imageName) {
        log.debug("Getting image tags from Nexus: {}", imageName);
        
        return nexusIntegrationService.getImageTagsFromNexus(imageName);
    }
    
    /**
     * 넥서스에서 이미지를 풀합니다.
     * 
     * @param imageName 이미지 이름
     * @param tag 태그
     * @return 풀 결과
     */
    public Object pullImageFromNexus(String imageName, String tag) {
        log.info("Pulling image from Nexus: {}:{}", imageName, tag);
        
        return nexusIntegrationService.pullImageFromNexus(imageName, tag);
    }
    
    /**
     * 카탈로그 ID로 이미지를 풀합니다.
     * 
     * @param catalogId 카탈로그 ID
     * @return 풀 결과
     */
    public Object pullImageByCatalogId(Long catalogId) {
        log.info("Pulling image by catalog ID: {}", catalogId);
        
        // 카탈로그 정보 조회애차차
        SoftwareCatalogDTO catalog = catalogService.getCatalog(catalogId);
        
        if (catalog.getPackageInfo() == null) {
            throw new IllegalArgumentException("PackageInfo is not available for catalog ID: " + catalogId);
        }
        
        String imageName = catalog.getPackageInfo().getPackageName();
        String tag = catalog.getPackageInfo().getPackageVersion();
        
        log.info("Pulling image for catalog '{}': {}:{}", catalog.getTitle(), imageName, tag);
        
        return nexusIntegrationService.pullImageFromNexus(imageName, tag);
    }
    
    /**
     * 넥서스에 이미지를 푸시합니다.
     * 
     * @param imageName 이미지 이름
     * @param tag 태그
     * @param imageData 이미지 데이터
     * @return 푸시 결과
     */
    public Object pushImageToNexus(String imageName, String tag, byte[] imageData) {
        log.info("Pushing image to Nexus: {}:{}", imageName, tag);
        
        return nexusIntegrationService.pushImageToNexus(imageName, tag, imageData);
    }

}