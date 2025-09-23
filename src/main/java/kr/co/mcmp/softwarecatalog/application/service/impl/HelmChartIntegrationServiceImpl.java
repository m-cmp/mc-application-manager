package kr.co.mcmp.softwarecatalog.application.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import kr.co.mcmp.externalrepo.model.ArtifactHubPackage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.co.mcmp.softwarecatalog.application.dto.HelmChartRegistrationRequest;
import kr.co.mcmp.softwarecatalog.application.model.HelmChart;
import kr.co.mcmp.softwarecatalog.application.repository.HelmChartRepository;
import kr.co.mcmp.softwarecatalog.application.service.ArtifactHubIntegrationService;
import kr.co.mcmp.softwarecatalog.application.service.HelmChartIntegrationService;
import kr.co.mcmp.softwarecatalog.application.service.NexusIntegrationService;
import kr.co.mcmp.softwarecatalog.application.config.NexusConfig;
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
    private final NexusConfig nexusConfig;

    @Override
    public Map<String, Object> searchHelmCharts(String query, int page, int pageSize) {
        return artifactHubIntegrationService.searchHelmCharts(query, page, pageSize);
    }


    @Override
    public List<String> getHelmChartVersions(String packageId) {
        return artifactHubIntegrationService.getHelmChartVersions(packageId);
    }

    @Override
    public Map<String, Object> registerHelmChart(HelmChartRegistrationRequest request, String username) {
        log.info("Registering Helm Chart: packageId={}, chartName={}",
                request.getPackageId(), request.getName());

        Map<String, Object> result = new HashMap<>();

        try {
            // 1. Helm Chart 에서 존재 여부 확인
            ArtifactHubPackage.Package detailInfo = artifactHubIntegrationService.getPackageDetailInfo("helm", request.getRepository().getName(), request.getName(), request.getTag());
            if(detailInfo != null) {
                // 2. Docker 이미지를 Nexus로 푸시 (이미지가 있는 경우)
                String nexusImageRepository = null;
                if (request.getImageRepository() != null && !request.getImageRepository().trim().isEmpty()) {
                    Map<String, Object> imagePushResult = pushDockerImageToNexus(request);
                    if ((Boolean) imagePushResult.get("success")) {
                        nexusImageRepository = (String) imagePushResult.get("imageUrl");
                        log.info("Docker image pushed to Nexus: {}", nexusImageRepository);
                    } else {
                        log.warn("Failed to push Docker image to Nexus: {}", imagePushResult.get("message"));
                    }
                }

                // 3. Helm Chart를 Nexus로 push
                Map<String, Object> pushResult = pushHelmChartToNexus(request);
                if (!(Boolean) pushResult.get("success")) {
                    log.warn("Failed to push Helm Chart to Nexus, but saved to database: {}", pushResult.get("message"));
                }

                // 4. Repository URL 설정 (ArtifactHub에서 가져온 정보 사용)
                if (request.getRepository() == null) {
                    request.setRepository(HelmChartRegistrationRequest.Repository.builder().build());
                }
                if (request.getRepository().getUrl() == null || request.getRepository().getUrl().trim().isEmpty()) {
                    // Nexus Helm repository URL 사용
                    String repositoryUrl = getNexusRepositoryUrl();
                    request.getRepository().setUrl(repositoryUrl);
                    request.getRepository().setName("nexus-helm");
                    request.getRepository().setOfficial(false);
                    log.info("Set Nexus repository URL: {}", repositoryUrl);
                }
                
                // 5. Helm Chart 상세 정보를 HELM_CHART 테이블에 저장 (Nexus 이미지 경로 포함)
                HelmChart savedHelmChart = saveHelmChartToTableDirect(request, username, nexusImageRepository);

                result.put("success", true);
                result.put("message", "Helm Chart registered successfully");
                result.put("helmChart", savedHelmChart);
                result.put("nexusPush", pushResult);
                if (nexusImageRepository != null) {
                    result.put("nexusImageRepository", nexusImageRepository);
                }

            }
            else {
                result.put("success", false);
                result.put("message", "Helm Chart registered Failed");
            }
        } catch (IllegalArgumentException e) {
            // 공식 저장소가 아닌 경우
            log.error("Repository not allowed: {}", e.getMessage());
            result.put("success", false);
            result.put("message", "Repository not allowed: " + e.getMessage());
        } catch (Exception e) {
            // 기타 예외
            log.error("Failed to register Helm Chart: packageId={}", request.getPackageId(), e);
            result.put("success", false);
            result.put("message", "Failed to register Helm Chart: " + e.getMessage());
        }

        return result;
    }

    @Override
    public Map<String, Object> pushHelmChartToNexus(HelmChartRegistrationRequest request) {
        log.info("Starting Helm Chart push to Nexus: {}:{}", request.getName(), request.getVersion());

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
            log.info("Helm Chart push completed successfully: {}:{}", request.getName(), request.getVersion());

        } catch (IllegalArgumentException e) {
            // 공식 저장소가 아닌 경우
            log.error("Repository not allowed: {}", e.getMessage());
            result.put("success", false);
            result.put("message", "Repository not allowed: " + e.getMessage());
        } catch (Exception e) {
            // 기타 예외
            log.error("Error pushing Helm Chart to Nexus: {}:{}", request.getName(), request.getVersion(), e);
            result.put("success", false);
            result.put("message", "Error pushing Helm Chart to Nexus: " + e.getMessage());
        }

        return result;
    }

    @Override
    public Map<String, Object> updateImageRepository(Long helmChartId, String imageName, String tag) {
        log.info("Updating image repository for Helm Chart ID: {}", helmChartId);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 1. Helm Chart 조회
            HelmChart helmChart = helmChartRepository.findById(helmChartId)
                    .orElseThrow(() -> new RuntimeException("Helm Chart not found with ID: " + helmChartId));
            
            // 2. 이미지 이름과 태그 설정
            if (imageName == null || imageName.trim().isEmpty()) {
                imageName = helmChart.getChartName().toLowerCase().replaceAll("\\s+", "-");
            }
            if (tag == null || tag.trim().isEmpty()) {
                tag = "latest";
            }
            
            // 3. 이미지가 이미 Nexus에 있는지 확인
            boolean imageExists = nexusIntegrationService.checkImageExistsInNexus(imageName, tag);
            String nexusImageUrl;
            
            if (imageExists) {
                log.info("Image already exists in Nexus: {}:{}", imageName, tag);
                // 기존 이미지의 Nexus URL 생성
                OssDto nexusInfo = nexusIntegrationService.getNexusInfoFromDB();
                String dockerRepositoryName = nexusIntegrationService.getRepositoryNameByFormat("docker");
                int dockerPort = nexusConfig.getDockerPort();
                nexusImageUrl = nexusInfo.getOssUrl().replace("http://", "").replace("https://", "") + 
                               ":" + dockerPort + 
                               "/" + dockerRepositoryName + "/" + imageName + ":" + tag;
            } else {
                // 4. Nexus에 이미지 푸시
                log.info("Pushing image to Nexus: {}:{}", imageName, tag);
                Map<String, Object> pushResult = nexusIntegrationService.pushImageToNexus(imageName, tag, null);
                
                if (!(Boolean) pushResult.get("success")) {
                    result.put("success", false);
                    result.put("message", "Failed to push image to Nexus: " + pushResult.get("message"));
                    return result;
                }
                
                // 5. Nexus 이미지 URL 생성
                nexusImageUrl = (String) pushResult.get("imageUrl");
                if (nexusImageUrl == null) {
                    OssDto nexusInfo = nexusIntegrationService.getNexusInfoFromDB();
                    String dockerRepositoryName = nexusIntegrationService.getRepositoryNameByFormat("docker");
                    int dockerPort = nexusConfig.getDockerPort();
                    nexusImageUrl = nexusInfo.getOssUrl().replace("http://", "").replace("https://", "") + 
                                   ":" + dockerPort + 
                                   "/" + dockerRepositoryName + "/" + imageName + ":" + tag;
                }
            }
            
            // 6. Helm Chart의 imageRepository 업데이트
            helmChart.setImageRepository(nexusImageUrl);
            helmChartRepository.save(helmChart);
            
            result.put("success", true);
            result.put("message", "Image repository updated successfully");
            result.put("helmChartId", helmChartId);
            result.put("nexusImageUrl", nexusImageUrl);
            result.put("imageName", imageName);
            result.put("tag", tag);
            
            log.info("Successfully updated image repository for Helm Chart {}: {}", helmChartId, nexusImageUrl);
            
        } catch (Exception e) {
            log.error("Failed to update image repository for Helm Chart ID: {}", helmChartId, e);
            result.put("success", false);
            result.put("message", "Failed to update image repository: " + e.getMessage());
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
     * Docker 이미지를 Nexus에 푸시합니다.
     */
    private Map<String, Object> pushDockerImageToNexus(HelmChartRegistrationRequest request) {
        log.info("Pushing Docker image to Nexus: {}", request.getImageRepository());
        
        try {
            // 이미지 이름과 태그 추출
            String imageName = request.getImageRepository();
            String tag = request.getTag();
            
            // 이미지 이름에서 태그가 포함되어 있는지 확인
            if (imageName.contains(":")) {
                String[] parts = imageName.split(":");
                imageName = parts[0];
                tag = parts[1];
            }
            
            // docker.io/ 접두사 제거
            if (imageName.startsWith("docker.io/")) {
                imageName = imageName.substring("docker.io/".length());
            }
            
            // 이미지가 이미 Nexus에 있는지 확인
            boolean imageExists = nexusIntegrationService.checkImageExistsInNexus(imageName, tag);
            if (imageExists) {
                log.info("Image already exists in Nexus: {}:{}", imageName, tag);
                // 기존 이미지의 Nexus URL 생성
                OssDto nexusInfo = nexusIntegrationService.getNexusInfoFromDB();
                String dockerRepositoryName = nexusIntegrationService.getRepositoryNameByFormat("docker");
                int dockerPort = nexusConfig.getDockerPort();
                String nexusImageUrl = nexusInfo.getOssUrl().replace("http://", "").replace("https://", "") + 
                                     ":" + dockerPort + 
                                     "/" + dockerRepositoryName + "/" + imageName + ":" + tag;
                
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("message", "Image already exists in Nexus");
                result.put("imageUrl", nexusImageUrl);
                return result;
            }
            
            // Nexus에 이미지 푸시
            Map<String, Object> pushResult = nexusIntegrationService.pushImageToNexus(imageName, tag, null);
            
            if ((Boolean) pushResult.get("success")) {
                // Nexus 이미지 URL 생성
                String nexusImageUrl = (String) pushResult.get("imageUrl");
                if (nexusImageUrl == null) {
                    // imageUrl이 없으면 직접 생성
                    OssDto nexusInfo = nexusIntegrationService.getNexusInfoFromDB();
                    String dockerRepositoryName = nexusIntegrationService.getRepositoryNameByFormat("docker");
                    int dockerPort = nexusConfig.getDockerPort();
                    nexusImageUrl = nexusInfo.getOssUrl().replace("http://", "").replace("https://", "") + 
                                   ":" + dockerPort + 
                                   "/" + dockerRepositoryName + "/" + imageName + ":" + tag;
                }
                pushResult.put("imageUrl", nexusImageUrl);
                log.info("Successfully pushed image to Nexus: {}", nexusImageUrl);
            }
            
            return pushResult;
            
        } catch (Exception e) {
            log.error("Failed to push Docker image to Nexus: {}", request.getImageRepository(), e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "Failed to push Docker image to Nexus: " + e.getMessage());
            return result;
        }
    }

    /**
     * Helm Chart 상세 정보를 HELM_CHART 테이블에 직접 저장합니다 (요청 데이터 사용).
     */
    // private HelmChart saveHelmChartToTableDirect(HelmChartRegistrationRequest request, String username) {
    //     return saveHelmChartToTableDirect(request, username, null);
    // }

    /**
     * Helm Chart 상세 정보를 HELM_CHART 테이블에 직접 저장합니다 (요청 데이터 사용).
     */
    private HelmChart saveHelmChartToTableDirect(HelmChartRegistrationRequest request, String username, String nexusImageRepository) {
        try {
            // 사용자 정보 조회
            User user = null;
            if (username != null && !username.isEmpty()) {
                user = userRepository.findByUsername(username).orElse(null);
            }

            // HelmChart 엔티티 생성 (요청 데이터 직접 사용)
            HelmChart helmChart = createBaseHelmChart(request, user);

            // 요청 데이터에서 추가 정보 설정
            helmChart.setDescription(request.getDescription());
            helmChart.setHomeUrl(request.getHomepage());
            helmChart.setValuesFile(request.getDocumentationUrl());
            helmChart.setHasValuesSchema(true);
            helmChart.setHomeUrl(request.getHomepage());
            
            // imageRepository 설정 - Nexus 이미지 경로 우선 사용
            String imageRepository;
            if (nexusImageRepository != null && !nexusImageRepository.trim().isEmpty()) {
                // Nexus에 푸시된 이미지 경로 사용
                imageRepository = nexusImageRepository;
                log.info("Using Nexus image repository: {}", imageRepository);
            } else {
                // Nexus 이미지가 없으면 요청 데이터에서 외부 경로를 내부 Nexus 경로로 변환
                String originalImageRepository = request.getImageRepository();
                if (originalImageRepository != null && !originalImageRepository.trim().isEmpty()) {
                    // 외부 이미지 경로를 내부 Nexus 경로로 변환
                    imageRepository = convertToNexusImagePath(originalImageRepository);
                    log.info("Converted external image path to Nexus path: {} -> {}", originalImageRepository, imageRepository);
                } else {
                    // Chart 이름을 기반으로 기본 이미지 저장소 설정
                    imageRepository = request.getName().toLowerCase().replaceAll("\\s+", "-");
                }
            }
            helmChart.setImageRepository(imageRepository);
            
            // Repository 정보 설정 - 항상 Nexus 정보 사용
            if (request.getRepository() != null) {
                helmChart.setRepositoryDisplayName(request.getRepository().getName() != null ? 
                    request.getRepository().getName().toUpperCase() : "NEXUS-HELM");
                helmChart.setRepositoryName(request.getRepository().getName() != null ? 
                    request.getRepository().getName() : "nexus-helm");
                helmChart.setRepositoryOfficial(false); // Nexus는 공식 저장소가 아님
            } else {
                // Repository 정보가 없으면 Nexus 기본값 설정
                helmChart.setRepositoryDisplayName("NEXUS-HELM");
                helmChart.setRepositoryName("nexus-helm");
                helmChart.setRepositoryOfficial(false);
            }
            
            // Repository URL은 항상 Nexus URL로 설정
            helmChart.setChartRepositoryUrl(getNexusRepositoryUrl());
            log.info("Set Nexus repository URL: {}", getNexusRepositoryUrl());
            helmChart.setValuesFile(request.getDocumentationUrl());
            // helmChart.setLicense(request.getLicense());

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
        // Repository URL 설정 - 항상 Nexus Helm Repository URL 사용
        String repositoryUrl = getNexusRepositoryUrl();
        log.info("Using Nexus repository URL: {}", repositoryUrl);

        // imageRepository 설정 - 외부 경로를 내부 Nexus 경로로 변환
        String imageRepository = request.getImageRepository();
        if (imageRepository != null && !imageRepository.trim().isEmpty()) {
            // 외부 이미지 경로를 내부 Nexus 경로로 변환
            imageRepository = convertToNexusImagePath(imageRepository);
        } else {
            // Chart 이름을 기반으로 기본 이미지 저장소 설정
            imageRepository = request.getName().toLowerCase().replaceAll("\\s+", "-");
        }

        return HelmChart.builder()
                .catalog(null) // software_catalog에 저장하지 않음
                .packageId(request.getPackageId())
                .chartName(request.getName())
                .chartVersion(request.getVersion())
                .tag(request.getTag())
                .appVersion(request.getAppVersion())
                .chartRepositoryUrl(repositoryUrl) // 요청에서 받은 repository URL 사용
                .category(request.getCategory())
                .imageRepository(imageRepository)
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
     * 외부 이미지 경로를 내부 Nexus 경로로 변환합니다.
     */
    private String convertToNexusImagePath(String externalImagePath) {
        try {
            // 외부 이미지 경로에서 이미지 이름과 태그 추출
            String imageName = externalImagePath;
            String tag = "latest";
            
            if (externalImagePath.contains(":")) {
                String[] parts = externalImagePath.split(":");
                imageName = parts[0];
                tag = parts[1];
            }
            
            // docker.io/ 접두사 제거
            if (imageName.startsWith("docker.io/")) {
                imageName = imageName.substring("docker.io/".length());
            }
            
            // Nexus 정보 가져오기
            OssDto nexusInfo = nexusIntegrationService.getNexusInfoFromDB();
            String dockerRepositoryName = nexusIntegrationService.getRepositoryNameByFormat("docker");
            int dockerPort = nexusConfig.getDockerPort();
            
            // Nexus 이미지 경로 생성 (Docker 레지스트리 URL 사용)
            String nexusHost = nexusInfo.getOssUrl().replace("http://", "").replace("https://", "");
            if (nexusHost.contains(":")) {
                // 포트가 이미 포함된 경우 호스트만 추출
                nexusHost = nexusHost.split(":")[0];
            }
            String nexusImagePath = nexusHost + ":" + dockerPort + 
                                   "/" + dockerRepositoryName + "/" + imageName + ":" + tag;
            
            return nexusImagePath;
            
        } catch (Exception e) {
            log.error("Failed to convert external image path to Nexus path: {}", externalImagePath, e);
            // 변환 실패 시 원본 경로 반환
            return externalImagePath;
        }
    }

    /**
     * ArtifactHub API 응답에서 데이터를 추출하여 HelmChart에 설정합니다.
     */
    private void extractAndSetArtifactHubData(HelmChart helmChart, Map<String, Object> chartData) {
        // 문자열 값 설정
        setStringValue(helmChart::setChartName, chartData.get("name"));
        setStringValue(helmChart::setChartVersion, chartData.get("version"));
        setStringValue(helmChart::setPackageId, chartData.get("package_id"));
        setStringValue(helmChart::setCategory, chartData.get("category"));
        setStringValue(helmChart::setImageRepository, chartData.get("image_repository"));
        setStringValue(helmChart::setDescription, chartData.get("description"));
        setStringValue(helmChart::setNormalizedName, chartData.get("normalized_name"));
        setStringValue(helmChart::setHomeUrl, chartData.get("home_url"));
        setStringValue(helmChart::setValuesFile, chartData.get("values_file"));

        // Repository 정보 추출 (중첩된 구조에서)
        @SuppressWarnings("unchecked")
        Map<String, Object> repository = (Map<String, Object>) chartData.get("repository");
        if (repository != null) {
            // Repository URL은 항상 Nexus URL로 설정
            helmChart.setChartRepositoryUrl(getNexusRepositoryUrl());
            setStringValue(helmChart::setRepositoryName, repository.get("name"));
            setStringValue(helmChart::setRepositoryDisplayName, repository.get("display_name"));
            helmChart.setRepositoryOfficial(false); // Nexus는 공식 저장소가 아님
            log.info("Set Nexus repository URL: {}", getNexusRepositoryUrl());
        }

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
     * Helm Chart를 HTTP 요청으로 다운로드합니다.
     */
    private boolean downloadHelmChart(HelmChartRegistrationRequest request) {
        log.info("Downloading Helm Chart: {}:{} from {}", request.getName(), request.getVersion(), request.getRepository().getUrl());

        try {
            // Repository URL이 null인 경우 처리
            if (request.getRepository().getUrl() == null || request.getRepository().getUrl().isEmpty()) {
                log.error("Repository URL is null or empty for Helm Chart: {}:{}", request.getName(), request.getVersion());
                return false;
            }

            // Helm Chart 다운로드 URL 구성
            String chartUrl = buildHelmChartDownloadUrl(request);
            log.info("Downloading Helm Chart from URL: {}", chartUrl);

            // HTTP 요청으로 Helm Chart 다운로드 (.tgz 확장자 사용)
            ProcessBuilder curlProcess = new ProcessBuilder("curl", "-L", "-o",request.getName() + "-" + request.getTag() + ".tgz", chartUrl);
            Process curlResult = curlProcess.start();
            int curlExitCode = curlResult.waitFor();

            if (curlExitCode != 0) {
                // 오류 출력 읽기
                String errorOutput = new String(curlResult.getErrorStream().readAllBytes());
                log.error("Failed to download Helm Chart: {}:{} (exit code: {})", request.getName(), request.getVersion(), curlExitCode);
                log.error("Error output: {}", errorOutput);
                return false;
            }

            log.info("Successfully downloaded Helm Chart: {}:{}", request.getName(), request.getVersion());
            return true;

        } catch (Exception e) {
            log.error("Error downloading Helm Chart: {}:{}", request.getName(), request.getVersion(), e);
            return false;
        }
    }

    /**
     * Helm Chart 다운로드 URL을 구성합니다. (공식 저장소만 허용)
     */
    private String buildHelmChartDownloadUrl(HelmChartRegistrationRequest request) {
        String baseUrl = request.getRepository().getUrl();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        // 공식 저장소 검증
        if (!isOfficialRepository(baseUrl)) {
            throw new IllegalArgumentException("Only official repositories are allowed. Found: " + baseUrl);
        }

        // Bitnami repository의 경우 /charts/ 경로가 없음
        if (baseUrl.contains("charts.bitnami.com")) {
            return String.format("%s/%s-%s.tgz", baseUrl, request.getName(), request.getTag());
        } else {
            // 일반적인 Helm Chart URL 패턴: {repository}/charts/{chartName}-{version}.tgz
            return String.format("%s/charts/%s-%s.tgz", baseUrl, request.getName(), request.getTag());
        }
    }

    /**
     * 공식 저장소인지 검증합니다.
     */
    private boolean isOfficialRepository(String repositoryUrl) {
        if (repositoryUrl == null) {
            return false;
        }

        // 허용된 공식 저장소 목록
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
            "kube-ops.gitlab.io",
            "https://kube-ops.gitlab.io/helm/repository",
            "grafana.github.io",
            "https://grafana.github.io/helm-charts",
            "prometheus-community.github.io",
            "https://prometheus-community.github.io/helm-charts"
        };

        for (String officialRepo : officialRepositories) {
            if (repositoryUrl.contains(officialRepo)) {
                log.info("Repository verified as official: {}", repositoryUrl);
                return true;
            }
        }

        log.warn("Repository not in official list: {}", repositoryUrl);
        return false;
    }



    /**
     * Helm Chart를 Nexus에 업로드합니다.
     */
    private boolean uploadHelmChartToNexus(HelmChartRegistrationRequest request) {
        log.info("Uploading Helm Chart to Nexus: {}:{}", request.getName(), request.getTag());

        try {
            // Nexus 정보 가져오기
            var nexusInfo = nexusIntegrationService.getNexusInfoFromDB();
            if (nexusInfo == null) {
                log.error("Nexus information not found");
                return false;
            }

            // 다운로드된 Helm Chart 파일명 (.tgz 확장자 사용)
            String chartFileName = request.getName() + "-" + request.getTag() + ".tgz";

            // Helm Chart 파일이 존재하는지 확인
            java.io.File chartFile = new java.io.File(chartFileName);
            if (!chartFile.exists()) {
                log.error("Helm Chart file not found: {}", chartFileName);
                return false;
            }

            // Nexus에 업로드 (Components API 사용)
            String helmRepositoryName = nexusIntegrationService.getRepositoryNameByFormat("helm");
            // Components API를 사용하여 업로드
            String uploadUrl = nexusInfo.getOssUrl() + "/service/rest/v1/components?repository=" + helmRepositoryName;

            // curl을 사용하여 파일 업로드 (Components API용)
            // 필수 필드: helm.asset, 선택: helm.asset.provenance
            // 상태코드 확인을 위해 -w "%{http_code}" 사용
            ProcessBuilder uploadProcess = new ProcessBuilder(
                "curl", "-X", "POST", "-sS",
                "-u", nexusInfo.getOssUsername() + ":" + kr.co.mcmp.util.Base64Utils.base64Decoding(nexusInfo.getOssPassword()),
                "-H", "Content-Type: multipart/form-data",
                "-F", "helm.asset=@" + chartFileName,
                "-F", "helm.asset.provenance=",
                "-w", "%{http_code}",
                uploadUrl
            );

            Process uploadResult = uploadProcess.start();
            int uploadExitCode = uploadResult.waitFor();

            // 응답 출력/상태코드 읽기
            String rawOutput = new String(uploadResult.getInputStream().readAllBytes());
            String errorOutput = new String(uploadResult.getErrorStream().readAllBytes());
            String responseOutput = rawOutput;
            int httpCode = -1;
            if (rawOutput != null && rawOutput.length() >= 3) {
                String tail = rawOutput.substring(rawOutput.length() - 3);
                try {
                    httpCode = Integer.parseInt(tail);
                    responseOutput = rawOutput.substring(0, Math.max(0, rawOutput.length() - 3));
                } catch (NumberFormatException ignore) {
                    // 상태코드가 포함되지 않은 경우 그대로 둠
                }
            }
            log.info("Nexus upload httpCode: {}", httpCode);
            if (!responseOutput.isEmpty()) {
                log.info("Nexus upload response: {}", responseOutput);
            }
            if (!errorOutput.isEmpty()) {
                log.warn("Nexus upload stderr: {}", errorOutput);
            }
            if (uploadExitCode != 0 || (httpCode >= 300 && httpCode != -1)) {
                log.error("Failed to upload Helm Chart to Nexus: {}:{} (exitCode={}, httpCode={})",
                        request.getName(), request.getVersion(), uploadExitCode, httpCode);
                return false;
            }

            // 업로드 후 간단 검증: 검색 API로 존재여부 확인
            try {
                String helmRepositoryNameVerify = nexusIntegrationService.getRepositoryNameByFormat("helm");
                String verifyUrl = nexusInfo.getOssUrl() + "/service/rest/v1/search?repository=" + helmRepositoryNameVerify + "&name=" + request.getName();
                ProcessBuilder verifyPb = new ProcessBuilder("curl", "-sS", "-u",
                        nexusInfo.getOssUsername() + ":" + kr.co.mcmp.util.Base64Utils.base64Decoding(nexusInfo.getOssPassword()),
                        verifyUrl);
                Process verifyProc = verifyPb.start();
                String verifyOut = new String(verifyProc.getInputStream().readAllBytes());
                verifyProc.waitFor();
                if (verifyOut == null || verifyOut.trim().isEmpty() || !verifyOut.contains(request.getName())) {
                    log.warn("Helm Chart not visible in Nexus search yet. It may require index rebuild.");
                } else {
                    log.info("Helm Chart appears in Nexus search results.");
                }
            } catch (Exception ve) {
                log.warn("Failed to verify Helm Chart in Nexus search: {}", ve.getMessage());
            }

            // 임시 파일 삭제
            try {
                if (chartFile.delete()) {
                    log.info("Temporary Helm Chart file deleted: {}", chartFileName);
                }
            } catch (Exception e) {
                log.warn("Failed to delete temporary Helm Chart file: {}", chartFileName);
            }

            log.info("Successfully uploaded Helm Chart to Nexus: {}:{}", request.getName(), request.getVersion());
            return true;

        } catch (Exception e) {
            log.error("Error uploading Helm Chart to Nexus: {}:{}", request.getName(), request.getVersion(), e);
            return false;
        }
    }
}
