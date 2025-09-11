package kr.co.mcmp.softwarecatalog.application.service;

import java.util.List;

import kr.co.mcmp.softwarecatalog.category.dto.KeyValueDTO;
import kr.co.mcmp.softwarecatalog.category.dto.SoftwareCatalogRequestDTO;

/**
 * 애플리케이션 등록 및 수정을 담당하는 서비스 인터페이스
 * 배포 및 운영 작업은 ApplicationOrchestrationService를 사용하세요
 */
public interface ApplicationService {

    // ===== 넥서스 연동 관련 메서드 (애플리케이션 배포/운영용) =====

    /**
     * 넥서스에서 애플리케이션을 조회합니다.
     *
     * @param applicationName 애플리케이션 이름
     * @return 넥서스 애플리케이션 정보
     */
    Object getApplicationFromNexus(String applicationName);

    /**
     * 넥서스에서 모든 애플리케이션을 조회합니다.
     *
     * @return 넥서스 애플리케이션 목록
     */
    List<Object> getAllApplicationsFromNexus();

    /**
     * 넥서스에서 이미지 태그 목록을 조회합니다.
     *
     * @param imageName 이미지 이름
     * @return 태그 목록
     */
    List<String> getImageTagsFromNexus(String imageName);

    /**
     * 넥서스에서 이미지를 풀합니다.
     *
     * @param imageName 이미지 이름
     * @param tag 이미지 태그
     * @return 풀 결과
     */
    Object pullImageFromNexus(String imageName, String tag);

    List<KeyValueDTO> getCategoriesFromDB(SoftwareCatalogRequestDTO.SearchCatalogListDTO request);

    List<KeyValueDTO> getPackageInfoFromDB(SoftwareCatalogRequestDTO.SearchPackageListDTO request);

    List<KeyValueDTO> getPackageVersionFromDB(SoftwareCatalogRequestDTO.SearchPackageVersionListDTO request);
}