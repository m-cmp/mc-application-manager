package kr.co.mcmp.softwarecatalog.application.service;

import java.util.List;
import java.util.Map;

/**
 * Docker Hub 연동 서비스 인터페이스
 */
public interface DockerHubIntegrationService {
    
    /**
     * Docker Hub에서 이미지를 검색합니다.
     * 
     * @param query 검색 쿼리
     * @param page 페이지 번호
     * @param pageSize 페이지 크기
     * @return 검색 결과
     */
    Map<String, Object> searchImages(String query, int page, int pageSize);
    
    /**
     * Docker Hub에서 특정 이미지의 상세 정보를 조회합니다.
     * 
     * @param imageName 이미지 이름
     * @param tag 이미지 태그
     * @return 이미지 상세 정보
     */
    Map<String, Object> getImageDetails(String imageName, String tag);
    
    /**
     * Docker Hub에서 이미지의 태그 목록을 조회합니다.
     * 
     * @param imageName 이미지 이름
     * @return 태그 목록
     */
    List<String> getImageTags(String imageName);
    
    /**
     * Docker Hub 이미지를 넥서스에 푸시합니다.
     * 
     * @param imageName 이미지 이름
     * @param tag 이미지 태그
     * @return 푸시 결과
     */
    Map<String, Object> pushImageToNexus(String imageName, String tag);
}
