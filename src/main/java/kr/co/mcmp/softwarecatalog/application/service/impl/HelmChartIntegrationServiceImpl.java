package kr.co.mcmp.softwarecatalog.application.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.co.mcmp.softwarecatalog.application.dto.HelmChartRegistrationRequest;
import kr.co.mcmp.softwarecatalog.application.model.HelmChart;
import kr.co.mcmp.softwarecatalog.application.repository.HelmChartRepository;
import kr.co.mcmp.softwarecatalog.application.service.ArtifactHubIntegrationService;
import kr.co.mcmp.softwarecatalog.application.service.HelmChartIntegrationService;
import kr.co.mcmp.softwarecatalog.application.service.NexusIntegrationService;
import kr.co.mcmp.softwarecatalog.users.Entity.User;
import kr.co.mcmp.softwarecatalog.users.repository.UserRepository;
import kr.co.mcmp.oss.dto.OssDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Helm Chart 통합 서비스 구현체
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class HelmChartIntegrationServiceImpl implements HelmChartIntegrationService {
    
    private final ArtifactHubIntegrationService artifactHubIntegrationService;
    private final NexusIntegrationService nexusIntegrationService;
    private final HelmChartRepository helmChartRepository;
    private final UserRepository userRepository;
    
    @Override
    public Map<String, Object> searchHelmCharts(String query, int page, int pageSize) {
        return artifactHubIntegrationService.searchHelmCharts(query, page, pageSize);
    }
    
    @Override
    public Map<String, Object> getHelmChartDetails(String packageId) {
        return artifactHubIntegrationService.getHelmChartDetails(packageId);
    }
    
    @Override
    public List<String> getHelmChartVersions(String packageId) {
        return artifactHubIntegrationService.getHelmChartVersions(packageId);
    }
    
    @Override
    public Map<String, Object> registerHelmChart(HelmChartRegistrationRequest request, String username) {
        log.info("Registering Helm Chart: packageId={}, chartName={}", 
                request.getPackageId(), request.getChartName());
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 1. ArtifactHub에서 Helm Chart 정보 조회
            Map<String, Object> chartDetails = getHelmChartDetails(request.getPackageId());
            if (!(Boolean) chartDetails.get("success")) {
                result.put("success", false);
                result.put("message", "Failed to get Helm Chart details: " + chartDetails.get("message"));
                return result;
            }
            
            // 2. Helm Chart 상세 정보를 HELM_CHART 테이블에 저장
            HelmChart savedHelmChart = saveHelmChartToTable(request, chartDetails, username);
            
            // 3. Helm Chart를 Nexus로 push
            Map<String, Object> pushResult = pushHelmChartToNexus(request);
            if (!(Boolean) pushResult.get("success")) {
                log.warn("Failed to push Helm Chart to Nexus, but saved to database: {}", pushResult.get("message"));
            }
            
            result.put("success", true);
            result.put("message", "Helm Chart registered successfully");
            result.put("helmChart", savedHelmChart);
            result.put("nexusPush", pushResult);
            
        } catch (Exception e) {
            log.error("Failed to register Helm Chart: packageId={}", request.getPackageId(), e);
            result.put("success", false);
            result.put("message", "Failed to register Helm Chart: " + e.getMessage());
        }
        
        return result;
    }
    
    @Override
    public Map<String, Object> pushHelmChartToNexus(HelmChartRegistrationRequest request) {
        log.info("Starting Helm Chart push to Nexus: {}:{}", request.getChartName(), request.getChartVersion());
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 1. Helm Chart 다운로드
            if (!downloadHelmChart(request)) {
                result.put("success", false);
                result.put("message", "Failed to download Helm Chart");
                return result;
            }
            
            // 2. Nexus에 Helm Chart 업로드
            if (!uploadHelmChartToNexus(request)) {
                result.put("success", false);
                result.put("message", "Failed to upload Helm Chart to Nexus");
                return result;
            }
            
            result.put("success", true);
            result.put("message", "Helm Chart successfully pushed to Nexus");
            log.info("Helm Chart push completed successfully: {}:{}", request.getChartName(), request.getChartVersion());
            
        } catch (Exception e) {
            log.error("Error pushing Helm Chart to Nexus: {}:{}", request.getChartName(), request.getChartVersion(), e);
            result.put("success", false);
            result.put("message", "Error pushing Helm Chart to Nexus: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Helm Chart 상세 정보를 HELM_CHART 테이블에 저장합니다.
     */
    private HelmChart saveHelmChartToTable(HelmChartRegistrationRequest request, Map<String, Object> chartDetails, String username) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> chartData = (Map<String, Object>) chartDetails.get("data");
            
            // 사용자 정보 조회
            User user = null;
            if (username != null && !username.isEmpty()) {
                user = userRepository.findByUsername(username).orElse(null);
            }
            
            // HelmChart 엔티티 생성
            HelmChart helmChart = createBaseHelmChart(request, user);
            
            // ArtifactHub 데이터 추출 및 설정
            extractAndSetArtifactHubData(helmChart, chartData);
            
            // HELM_CHART 테이블에 저장
            HelmChart savedHelmChart = helmChartRepository.save(helmChart);
            
            log.info("Helm Chart details saved to HELM_CHART table: packageId={} (helmChartId: {})", 
                    request.getPackageId(), savedHelmChart.getId());
            
            return savedHelmChart;
            
        } catch (Exception e) {
            log.error("Failed to save Helm Chart details to HELM_CHART table: packageId={}", 
                    request.getPackageId(), e);
            throw new RuntimeException("Failed to save Helm Chart details", e);
        }
    }
    
    /**
     * 기본 HelmChart 엔티티를 생성합니다.
     */
    private HelmChart createBaseHelmChart(HelmChartRegistrationRequest request, User user) {
        // Nexus URL로 repository URL 설정
        String nexusRepositoryUrl = getNexusRepositoryUrl();
        
        return HelmChart.builder()
                .catalog(null) // software_catalog에 저장하지 않음
                .packageId(request.getPackageId())
                .chartName(request.getChartName())
                .chartVersion(request.getChartVersion())
                .chartRepositoryUrl(nexusRepositoryUrl) // Nexus URL 사용
                .category(request.getCategory())
                .imageRepository(request.getImageRepository())
                .user(user)
                .build();
    }
    
    /**
     * Nexus Repository URL을 가져옵니다.
     */
    private String getNexusRepositoryUrl() {
        try {
            OssDto nexusInfo = nexusIntegrationService.getNexusInfoFromDB();
            String helmRepositoryName = nexusIntegrationService.getRepositoryNameByFormat("helm");
            return nexusInfo.getOssUrl() + "/repository/" + helmRepositoryName;
        } catch (Exception e) {
            log.error("Failed to get Nexus repository URL", e);
            throw new RuntimeException("Failed to get Nexus repository URL", e);
        }
    }
    
    /**
     * ArtifactHub API 응답에서 데이터를 추출하여 HelmChart에 설정합니다.
     */
    private void extractAndSetArtifactHubData(HelmChart helmChart, Map<String, Object> chartData) {
        // 문자열 값 설정
        setStringValue(helmChart::setChartName, chartData.get("name"));
        setStringValue(helmChart::setChartVersion, chartData.get("version"));
        setStringValue(helmChart::setChartRepositoryUrl, chartData.get("repository_url"));
        setStringValue(helmChart::setPackageId, chartData.get("package_id"));
        setStringValue(helmChart::setCategory, chartData.get("category"));
        setStringValue(helmChart::setImageRepository, chartData.get("image_repository"));
        setStringValue(helmChart::setDescription, chartData.get("description"));
        setStringValue(helmChart::setNormalizedName, chartData.get("normalized_name"));
        setStringValue(helmChart::setRepositoryName, chartData.get("repository_name"));
        setStringValue(helmChart::setRepositoryDisplayName, chartData.get("repository_display_name"));
        setStringValue(helmChart::setHomeUrl, chartData.get("home_url"));
        setStringValue(helmChart::setValuesFile, chartData.get("values_file"));
        
        // 불린 값 설정
        setBooleanValue(helmChart::setHasValuesSchema, chartData.get("has_values_schema"));
        setBooleanValue(helmChart::setRepositoryOfficial, chartData.get("repository_official"));
        
        // 추가 정보 로깅
        logAdditionalArtifactHubInfo(chartData);
    }
    
    /**
     * 추가 ArtifactHub 정보를 로깅합니다.
     */
    private void logAdditionalArtifactHubInfo(Map<String, Object> chartData) {
        log.debug("Repository official: {}, Has values schema: {}, Category: {}", 
                chartData.get("repository_official"), 
                chartData.get("has_values_schema"), 
                chartData.get("category"));
    }
    
    /**
     * 문자열 값을 안전하게 설정합니다.
     */
    private void setStringValue(java.util.function.Consumer<String> setter, Object value) {
        if (value != null) {
            setter.accept(value.toString());
        }
    }
    
    /**
     * 불린 값을 안전하게 설정합니다.
     */
    private void setBooleanValue(java.util.function.Consumer<Boolean> setter, Object value) {
        if (value instanceof Boolean) {
            setter.accept((Boolean) value);
        } else if (value != null) {
            setter.accept(Boolean.parseBoolean(value.toString()));
        }
    }
    
    /**
     * Helm Chart를 다운로드합니다.
     */
    private boolean downloadHelmChart(HelmChartRegistrationRequest request) {
        log.info("Downloading Helm Chart: {}:{} from {}", request.getChartName(), request.getChartVersion(), request.getRepositoryUrl());
        
        try {
            // Helm repo add 명령어 실행
            ProcessBuilder repoAddProcess = new ProcessBuilder("helm", "repo", "add", "temp-repo", request.getRepositoryUrl());
            Process repoAddResult = repoAddProcess.start();
            int repoAddExitCode = repoAddResult.waitFor();
            
            if (repoAddExitCode != 0) {
                log.error("Failed to add Helm repository: {}", request.getRepositoryUrl());
                return false;
            }
            
            // Helm repo update 명령어 실행
            ProcessBuilder repoUpdateProcess = new ProcessBuilder("helm", "repo", "update");
            Process repoUpdateResult = repoUpdateProcess.start();
            int repoUpdateExitCode = repoUpdateResult.waitFor();
            
            if (repoUpdateExitCode != 0) {
                log.error("Failed to update Helm repository");
                return false;
            }
            
            // Helm pull 명령어 실행
            ProcessBuilder pullProcess = new ProcessBuilder("helm", "pull", "temp-repo/" + request.getChartName(), 
                    "--version", request.getChartVersion(), "--untar");
            Process pullResult = pullProcess.start();
            int pullExitCode = pullResult.waitFor();
            
            if (pullExitCode != 0) {
                log.error("Failed to download Helm Chart: {}:{}", request.getChartName(), request.getChartVersion());
                return false;
            }
            
            log.info("Successfully downloaded Helm Chart: {}:{}", request.getChartName(), request.getChartVersion());
            return true;
            
        } catch (Exception e) {
            log.error("Error downloading Helm Chart: {}:{}", request.getChartName(), request.getChartVersion(), e);
            return false;
        }
    }
    
    /**
     * Helm Chart를 Nexus에 업로드합니다.
     */
    private boolean uploadHelmChartToNexus(HelmChartRegistrationRequest request) {
        log.info("Uploading Helm Chart to Nexus: {}:{}", request.getChartName(), request.getChartVersion());
        
        try {
            // Nexus 정보 가져오기
            var nexusInfo = nexusIntegrationService.getNexusInfoFromDB();
            if (nexusInfo == null) {
                log.error("Nexus information not found");
                return false;
            }
            
            // Helm Chart 패키징
            ProcessBuilder packageProcess = new ProcessBuilder("helm", "package", request.getChartName(), 
                    "--version", request.getChartVersion());
            Process packageProcessResult = packageProcess.start();
            int packageExitCode = packageProcessResult.waitFor();
            
            if (packageExitCode != 0) {
                log.error("Failed to package Helm Chart: {}:{}", request.getChartName(), request.getChartVersion());
                return false;
            }
            
            // Nexus에 업로드 (RestTemplate 사용)
            String chartFileName = request.getChartName() + "-" + request.getChartVersion() + ".tgz";
            String helmRepositoryName = nexusIntegrationService.getRepositoryNameByFormat("helm");
            String uploadUrl = nexusInfo.getOssUrl() + "/repository/" + helmRepositoryName + "/" + chartFileName;
            
            // 파일을 읽어서 업로드
            java.io.File chartFile = new java.io.File(chartFileName);
            if (!chartFile.exists()) {
                log.error("Chart file not found: {}", chartFileName);
                return false;
            }
            
            // RestTemplate을 사용하여 업로드
            boolean uploadSuccess = nexusIntegrationService.uploadFileToNexus(chartFile, uploadUrl, nexusInfo);
            
            if (uploadSuccess) {
                log.info("Successfully uploaded Helm Chart to Nexus: {}:{}", request.getChartName(), request.getChartVersion());
                
                // 임시 파일 정리
                try {
                    if (chartFile.exists()) {
                        chartFile.delete();
                        log.debug("Cleaned up temporary chart file: {}", chartFileName);
                    }
                } catch (Exception cleanupException) {
                    log.warn("Failed to cleanup temporary chart file: {}", cleanupException.getMessage());
                }
            }
            
            return uploadSuccess;
            
        } catch (Exception e) {
            log.error("Error uploading Helm Chart to Nexus: {}:{}", request.getChartName(), request.getChartVersion(), e);
            return false;
        }
    }
}
