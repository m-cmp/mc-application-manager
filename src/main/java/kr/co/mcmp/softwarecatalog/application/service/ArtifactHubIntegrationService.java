package kr.co.mcmp.softwarecatalog.application.service;

import java.util.List;
import java.util.Map;

/**
 * ArtifactHub 연동 서비스 인터페이스
 */
public interface ArtifactHubIntegrationService {
    
    /**
     * ArtifactHub에서 Helm Chart를 검색합니다.
     * 
     * @param query 검색 쿼리
     * @param page 페이지 번호
     * @param pageSize 페이지 크기
     * @return 검색 결과
     */
    Map<String, Object> searchHelmCharts(String query, int page, int pageSize);
    
    
    /**
     * ArtifactHub에서 Helm Chart 버전 목록을 조회합니다.
     * 
     * @param packageId 패키지 ID
     * @return 버전 목록
     */
    List<String> getHelmChartVersions(String packageId);
}
