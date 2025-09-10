package kr.co.mcmp.softwarecatalog.application.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;

import kr.co.mcmp.softwarecatalog.application.service.ArtifactHubIntegrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * ArtifactHub 연동 서비스 구현체
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ArtifactHubIntegrationServiceImpl implements ArtifactHubIntegrationService {
    
    @Autowired
    private RestTemplate restTemplate;
    
    private static final String ARTIFACT_HUB_API_BASE = "https://artifacthub.io/api/v1";
    
    /**
     * ArtifactHub에서 Helm Chart를 검색합니다.
     */
    @Override
    public Map<String, Object> searchHelmCharts(String query, int page, int pageSize) {
        log.info("Searching ArtifactHub Helm charts: query={}, page={}, pageSize={}", query, page, pageSize);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            String url = String.format("%s/packages/search?kind=0&facets=false&page=%d&limit=%d&sort=relevance&ts_query_web=%s", 
                    ARTIFACT_HUB_API_BASE, page, pageSize, query);
            
            HttpEntity<String> entity = createHttpEntity();
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                result.put("success", true);
                result.put("data", response.getBody());
                result.put("message", "ArtifactHub Helm chart search completed successfully");
                log.info("Successfully searched ArtifactHub Helm charts: {} results", pageSize);
            } else {
                result.put("success", false);
                result.put("message", "Failed to search ArtifactHub: " + response.getStatusCode());
                log.error("ArtifactHub search failed: {}", response.getStatusCode());
            }
            
        } catch (Exception e) {
            log.error("Error searching ArtifactHub Helm charts: {}", e.getMessage());
            result.put("success", false);
            result.put("message", "Error searching ArtifactHub Helm charts: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * ArtifactHub에서 Helm Chart 상세 정보를 조회합니다.
     * 검색 결과에서 해당 패키지를 찾아 반환합니다.
     */
    @Override
    public Map<String, Object> getHelmChartDetails(String packageId) {
        log.info("Getting ArtifactHub Helm chart details: packageId={}", packageId);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 검색 API를 사용하여 해당 패키지를 찾습니다
            String[] searchTerms = {"nginx", "helm", "chart", "*"};
            
            for (String searchTerm : searchTerms) {
                log.info("Searching with term: {}", searchTerm);
                for (int page = 1; page <= 3; page++) {
                    String url = String.format("%s/packages/search?kind=0&facets=false&page=%d&limit=60&sort=relevance&ts_query_web=%s", 
                            ARTIFACT_HUB_API_BASE, page, searchTerm);
                    HttpEntity<String> entity = createHttpEntity();
                    ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
                    
                    if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                        try {
                            ObjectMapper mapper = new ObjectMapper();
                            Map<String, Object> responseBody = mapper.readValue(response.getBody(), Map.class);
                            @SuppressWarnings("unchecked")
                            List<Map<String, Object>> packages = (List<Map<String, Object>>) responseBody.get("packages");
                            
                            if (packages != null) {
                                for (Map<String, Object> packageInfo : packages) {
                                    if (packageId.equals(packageInfo.get("package_id"))) {
                                        result.put("success", true);
                                        result.put("data", packageInfo);
                                        result.put("message", "ArtifactHub Helm chart details retrieved successfully");
                                        log.info("Successfully retrieved ArtifactHub Helm chart details for: {}", packageId);
                                        return result;
                                    }
                                }
                            }
                        } catch (Exception jsonException) {
                            log.error("Failed to parse search response for term '{}' page {}: {}", searchTerm, page, jsonException.getMessage());
                        }
                    }
                }
            }
            
            result.put("success", false);
            result.put("message", "Package not found: " + packageId);
            log.warn("Package not found in search results: {}", packageId);
            
        } catch (Exception e) {
            log.error("Error getting ArtifactHub Helm chart details: {}", e.getMessage());
            result.put("success", false);
            result.put("message", "Error getting ArtifactHub Helm chart details: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * ArtifactHub에서 Helm Chart 버전 목록을 조회합니다.
     */
    @Override
    public List<String> getHelmChartVersions(String packageId) {
        log.info("Getting ArtifactHub Helm chart versions: packageId={}", packageId);
        
        List<String> versions = new ArrayList<>();
        
        try {
            String url = String.format("%s/packages/helm/%s/versions", ARTIFACT_HUB_API_BASE, packageId);
            HttpEntity<String> entity = createHttpEntity();
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                Map<String, Object> responseBody = (Map<String, Object>) response.getBody();
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> versionList = (List<Map<String, Object>>) responseBody.get("versions");
                
                if (versionList != null) {
                    for (Map<String, Object> versionInfo : versionList) {
                        String version = (String) versionInfo.get("version");
                        if (version != null) {
                            versions.add(version);
                        }
                    }
                }
                log.info("Successfully retrieved {} versions for Helm chart: {}", versions.size(), packageId);
            }
            
        } catch (Exception e) {
            log.error("Error getting ArtifactHub Helm chart versions: {}", e.getMessage());
        }
        
        return versions;
    }
    
    /**
     * HTTP 엔티티 생성
     */
    private HttpEntity<String> createHttpEntity() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        headers.set("Accept", "application/json");
        return new HttpEntity<>(headers);
    }
}
