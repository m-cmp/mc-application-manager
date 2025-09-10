package kr.co.mcmp.softwarecatalog.application.service;

import java.util.List;
import java.util.Map;

import kr.co.mcmp.dto.oss.repository.CommonRepository;
import kr.co.mcmp.oss.dto.OssDto;
import kr.co.mcmp.softwarecatalog.SoftwareCatalogDTO;

/**
 * 내부 넥서스와 연동하는 서비스 인터페이스
 */
public interface NexusIntegrationService {
    
    /**
     * 넥서스에 애플리케이션을 등록합니다.
     * 
     * @param catalog 소프트웨어 카탈로그 정보
     * @return 넥서스 등록 결과
     */
    Map<String, Object> registerToNexus(SoftwareCatalogDTO catalog);
    
    /**
     * 넥서스에서 애플리케이션을 조회합니다.
     * 
     * @param applicationName 애플리케이션 이름
     * @return 넥서스 애플리케이션 정보
     */
    CommonRepository.RepositoryDto getFromNexus(String applicationName);
    
    /**
     * 넥서스에서 모든 애플리케이션을 조회합니다.
     * 
     * @return 넥서스 애플리케이션 목록
     */
    List<CommonRepository.RepositoryDto> getAllFromNexus();
    
    /**
     * 넥서스에서 애플리케이션을 삭제합니다.
     * 
     * @param applicationName 애플리케이션 이름
     * @return 삭제 결과
     */
    Map<String, Object> deleteFromNexus(String applicationName);
    
    /**
     * 넥서스에서 이미지를 풀합니다.
     * 
     * @param imageName 이미지 이름
     * @param tag 태그
     * @return 풀 결과
     */
    Map<String, Object> pullImageFromNexus(String imageName, String tag);
    
    /**
     * 넥서스에 이미지를 푸시합니다.
     * 
     * @param imageName 이미지 이름
     * @param tag 태그
     * @param imageData 이미지 데이터
     * @return 푸시 결과
     */
    Map<String, Object> pushImageToNexus(String imageName, String tag, byte[] imageData);
    
    /**
     * 넥서스에서 이미지 태그 목록을 조회합니다.
     * 
     * @param imageName 이미지 이름
     * @return 태그 목록
     */
    List<String> getImageTagsFromNexus(String imageName);
    
    /**
     * 넥서스에 이미지가 존재하는지 확인합니다.
     * 
     * @param imageName 이미지 이름
     * @param tag 이미지 태그
     * @return 이미지 존재 여부
     */
    boolean checkImageExistsInNexus(String imageName, String tag);
    
    /**
     * 넥서스에 이미지를 푸시하고 카탈로그에 등록합니다.
     * 
     * @param catalog 소프트웨어 카탈로그 정보
     * @return 등록 결과
     */
    Map<String, Object> pushImageAndRegisterCatalog(SoftwareCatalogDTO catalog);
    
    /**
     * 넥서스에 Docker Repository가 있는지 확인하고, 없으면 생성합니다.
     * 
     * @param repositoryName Repository 이름
     * @return Repository 생성/확인 결과
     */
    Map<String, Object> ensureDockerRepositoryExists(String repositoryName);
    
    /**
     * DB에서 Nexus OSS 정보를 가져옵니다.
     * 
     * @return Nexus OSS 정보
     */
    OssDto getNexusInfoFromDB();
    
    /**
     * Nexus에서 Docker Repository 이름을 동적으로 가져옵니다.
     * 
     * @return Docker Repository 이름
     */
    String getDockerRepositoryName();
}
