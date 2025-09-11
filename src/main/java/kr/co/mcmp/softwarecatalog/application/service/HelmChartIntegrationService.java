package kr.co.mcmp.softwarecatalog.application.service;

import java.util.List;
import java.util.Map;

import kr.co.mcmp.softwarecatalog.application.dto.HelmChartRegistrationRequest;

/**
 * Helm Chart 통합 서비스 인터페이스
 */
public interface HelmChartIntegrationService {
    
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
    
    /**
     * Helm Chart를 등록하고 Nexus에 푸시합니다.
     * 
     * @param request Helm Chart 등록 요청
     * @param username 사용자명
     * @return 등록 결과
     */
    Map<String, Object> registerHelmChart(HelmChartRegistrationRequest request, String username);
    
    /**
     * Helm Chart를 Nexus로 푸시합니다.
     * 
     * @param request Helm Chart 등록 요청
     * @return 푸시 결과
     */
    Map<String, Object> pushHelmChartToNexus(HelmChartRegistrationRequest request);
}
