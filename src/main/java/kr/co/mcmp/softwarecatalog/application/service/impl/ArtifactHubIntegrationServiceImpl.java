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
     * ArtifactHub에서 Helm Chart를 검색합니다. (공식 저장소만 필터링)
     */
    @Override
    public Map<String, Object> searchHelmCharts(String query, int page, int pageSize) {
        log.info("Searching ArtifactHub Helm charts (Official repositories only): query={}, page={}, pageSize={}", query, page, pageSize);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            String url = String.format("%s/packages/search?kind=0&facets=false&page=%d&limit=%d&sort=relevance&ts_query_web=%s", 
                    ARTIFACT_HUB_API_BASE, page, pageSize, query);
            
            HttpEntity<String> entity = createHttpEntity();
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                Map<String, Object> responseBody = response.getBody();
                if (responseBody != null) {
                    // 공식 저장소만 필터링
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> allPackages = (List<Map<String, Object>>) responseBody.get("packages");
                    if (allPackages != null) {
                        List<Map<String, Object>> officialPackages = new ArrayList<>();
                        for (Map<String, Object> packageInfo : allPackages) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> repository = (Map<String, Object>) packageInfo.get("repository");
                            if (repository != null) {
                                String repositoryUrl = (String) repository.get("url");
                                if (isOfficialRepository(repositoryUrl)) {
                                    officialPackages.add(packageInfo);
                                    log.debug("Found official package: {} from {}", packageInfo.get("name"), repositoryUrl);
                                }
                            }
                        }
                        
                        // 필터링된 결과로 교체
                        responseBody.put("packages", officialPackages);
                        log.info("Filtered to {} official packages from {} total packages", officialPackages.size(), allPackages.size());
                    }
                }
                
                result.put("success", true);
                result.put("data", responseBody);
                result.put("message", "ArtifactHub Helm chart search completed successfully (Official repositories only)");
                log.info("Successfully searched ArtifactHub Helm charts (Official repositories only): {} results", pageSize);
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
     * 공식 저장소인지 검증합니다.
     */
    private boolean isOfficialRepository(String repositoryUrl) {
        if (repositoryUrl == null) {
            return false;
        }
        
        // 허용된 공식 저장소 목록 (확장)
        String[] officialRepositories = {
            "charts.bitnami.com",
            "kubernetes-charts.storage.googleapis.com",
            "https://kubernetes-charts.storage.googleapis.com",
            "https://charts.bitnami.com/bitnami",
            "cowboysysop.github.io",
            "https://cowboysysop.github.io/charts/",
            "charts.jfrog.io",
            "https://charts.jfrog.io",
            "marketplace.azurecr.io",
            "https://marketplace.azurecr.io/helm/v1/repo",
            "helm.nginx.com",
            "https://helm.nginx.com/stable",
            // 더 많은 공식 저장소 추가
            "charts.helm.sh",
            "https://charts.helm.sh/stable",
            "kubernetes-sigs.github.io",
            "https://kubernetes-sigs.github.io",
            "prometheus-community.github.io",
            "https://prometheus-community.github.io/helm-charts",
            "grafana.github.io",
            "https://grafana.github.io/helm-charts",
            "elastic.github.io",
            "https://elastic.github.io/helm-charts",
            "jetstack.github.io",
            "https://jetstack.github.io/charts",
            "ingress-nginx.github.io",
            "https://ingress-nginx.github.io/charts",
            "traefik.github.io",
            "https://traefik.github.io/charts"
        };
        
        for (String officialRepo : officialRepositories) {
            if (repositoryUrl.contains(officialRepo)) {
                log.debug("Repository verified as official: {}", repositoryUrl);
                return true;
            }
        }
        
        log.debug("Repository not in official list: {}", repositoryUrl);
        return false;
    }
    
    /**
     * 여러 페이지에서 패키지를 검색하는 헬퍼 메서드
     */
    private boolean searchPackageInPages(String packageId, String searchTerm, Map<String, Object> result) {
        try {
            // API 제한을 고려하여 페이지 수를 최소화
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
                            log.info("Found {} packages in search results for term '{}'", packages.size(), searchTerm);
                            for (Map<String, Object> packageInfo : packages) {
                                String foundPackageId = (String) packageInfo.get("package_id");
                                String packageName = (String) packageInfo.get("name");
                                log.debug("Checking package: id={}, name={}", foundPackageId, packageName);
                                
                                if (packageId.equals(foundPackageId)) {
                                    result.put("success", true);
                                    result.put("data", packageInfo);
                                    result.put("message", "ArtifactHub Helm chart details retrieved successfully");
                                    log.info("Successfully retrieved ArtifactHub Helm chart details for: {}", packageId);
                                    return true;
                                }
                            }
                            log.warn("PackageId {} not found in {} packages for term '{}'", packageId, packages.size(), searchTerm);
                        }
                    } catch (Exception jsonException) {
                        log.error("Failed to parse search response for term '{}' page {}: {}", searchTerm, page, jsonException.getMessage());
                    }
                } else if (response.getStatusCode().value() == 429) {
                    log.warn("API rate limit reached for term '{}', skipping remaining pages", searchTerm);
                    break;
                }
                
                // API 제한을 피하기 위해 페이지 간 대기
                if (page < 3) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error searching for package with term '{}': {}", searchTerm, e.getMessage());
        }
        
        return false;
    }
    
    /**
     * ArtifactHub에서 Helm Chart 버전 목록을 조회합니다.
     * ArtifactHub API는 직접적인 버전 조회 엔드포인트를 제공하지 않으므로,
     * 검색 API를 통해 해당 패키지의 모든 버전을 찾아 반환합니다.
     */
    @Override
    public List<String> getHelmChartVersions(String packageId) {
        log.info("Getting ArtifactHub Helm chart versions: packageId={}", packageId);
        
        List<String> versions = new ArrayList<>();
        
        try {
            // 먼저 패키지 상세 정보를 조회하여 repository 정보를 얻습니다
            // Map<String, Object> chartDetails = getHelmChartDetails(packageId);
            Map<String, Object> chartDetails = new HashMap<>();
            if (!(Boolean) chartDetails.get("success")) {
                log.warn("Failed to get chart details for packageId: {}", packageId);
                return versions;
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> chartData = (Map<String, Object>) chartDetails.get("data");
            if (chartData == null) {
                log.warn("No chart data found for packageId: {}", packageId);
                return versions;
            }
            
            // Repository 정보에서 repository name을 추출
            @SuppressWarnings("unchecked")
            Map<String, Object> repository = (Map<String, Object>) chartData.get("repository");
            if (repository == null) {
                log.warn("No repository information found for packageId: {}", packageId);
                return versions;
            }
            
            String repositoryName = (String) repository.get("name");
            String chartName = (String) chartData.get("name");
            
            if (repositoryName == null || chartName == null) {
                log.warn("Missing repository name or chart name for packageId: {}", packageId);
                return versions;
            }
            
            // 여러 페이지를 검색하여 모든 버전을 수집
            for (int page = 1; page <= 5; page++) {
                String searchQuery = String.format("%s %s", repositoryName, chartName);
                String url = String.format("%s/packages/search?kind=0&facets=false&page=%d&limit=60&sort=relevance&ts_query_web=%s", 
                        ARTIFACT_HUB_API_BASE, page, searchQuery);
                
                HttpEntity<String> entity = createHttpEntity();
                ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
                
                if (response.getStatusCode().is2xxSuccessful()) {
                    Map<String, Object> responseBody = (Map<String, Object>) response.getBody();
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> packages = (List<Map<String, Object>>) responseBody.get("packages");
                    
                    if (packages != null && !packages.isEmpty()) {
                        boolean foundMatchingChart = false;
                        for (Map<String, Object> packageInfo : packages) {
                            String pkgName = (String) packageInfo.get("name");
                            String version = (String) packageInfo.get("version");
                            
                            if (chartName.equals(pkgName) && version != null && !versions.contains(version)) {
                                versions.add(version);
                                foundMatchingChart = true;
                            }
                        }
                        
                        // 더 이상 매칭되는 차트가 없으면 검색 중단
                        if (!foundMatchingChart) {
                            break;
                        }
                    } else {
                        // 결과가 없으면 검색 중단
                        break;
                    }
                } else {
                    log.warn("Failed to search for chart versions on page {}: {}", page, response.getStatusCode());
                    break;
                }
            }
            
            log.info("Successfully retrieved {} versions for Helm chart: {} (repository: {})", 
                    versions.size(), chartName, repositoryName);
            
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
