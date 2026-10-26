package kr.co.mcmp.softwarecatalog.application.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import kr.co.mcmp.externalrepo.model.ArtifactHubPackage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.co.mcmp.softwarecatalog.CatalogRepository;
import kr.co.mcmp.softwarecatalog.SoftwareCatalog;
import kr.co.mcmp.softwarecatalog.application.dto.HelmChartRegistrationRequest;
import kr.co.mcmp.softwarecatalog.application.model.HelmChart;
import kr.co.mcmp.softwarecatalog.application.model.PackageInfo;
import kr.co.mcmp.softwarecatalog.application.repository.HelmChartRepository;
import kr.co.mcmp.softwarecatalog.application.repository.PackageInfoRepository;
import kr.co.mcmp.softwarecatalog.application.service.ArtifactHubIntegrationService;
import kr.co.mcmp.softwarecatalog.application.service.HelmChartIntegrationService;
import kr.co.mcmp.softwarecatalog.application.service.NexusIntegrationService;
import kr.co.mcmp.softwarecatalog.application.config.NexusConfig;
import kr.co.mcmp.softwarecatalog.application.constants.ArtifactSource;
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
    private final PackageInfoRepository packageInfoRepository;
    private final CatalogRepository catalogRepository;
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

        log.info("request to String ::" + request.toString());
        log.info("Registering Helm Chart: packageId={}, chartName={}",
                request.getPackageId(), request.getName());

        Map<String, Object> result = new HashMap<>();
        request.setVersion(firstNonBlank(request.getVersion(), request.getTag()));

        try {
            // 1. Helm Chart 에서 존재 여부 확인
            ArtifactHubPackage.Package detailInfo = artifactHubIntegrationService.getPackageDetailInfo("helm", request.getRepository().getName(), request.getName(), request.getTag());
            if(detailInfo != null) {
                request.setCategory(resolveCategory(request.getCategory(), detailInfo, request.getName()));

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
                    result.put("success", false);
                    result.put("message", "Failed to import Helm chart to Nexus: " + pushResult.get("message"));
                    result.put("nexusPush", pushResult);
                    return result;
                }

                // 4. Repository URL 설정 (ArtifactHub에서 가져온 정보 사용)
                if (request.getRepository() == null) {
                    request.setRepository(HelmChartRegistrationRequest.Repository.builder().build());
                }
                if (request.getRepository().getUrl() == null || request.getRepository().getUrl().trim().isEmpty()) {
                    // Nexus Helm repository URL 사용
                    String repositoryUrl = resolveChartRepositoryUrl(request);
                    request.getRepository().setUrl(repositoryUrl);
                    if (request.getRepository().getName() == null || request.getRepository().getName().trim().isEmpty()) {
                        request.getRepository().setName("nexus-helm");
                    }
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

        log.info("request to string ::" + request.toString());
        log.info("Starting Helm Chart push to Nexus: {}:{}", request.getName(), request.getVersion());

        Map<String, Object> result = new HashMap<>();

        java.nio.file.Path transferDirectory = null;
        try {
            // 1. Helm Chart 다운로드
            transferDirectory = java.nio.file.Files.createTempDirectory("am-helm-import-");
            java.nio.file.Path chartFile = downloadHelmChart(request, transferDirectory);
            if (chartFile == null) {
                result.put("success", false);
                result.put("message", "Failed to download Helm Chart");
                return result;
            }

            // 2. Nexus에 Helm Chart 업로드
            if (!uploadHelmChartToNexus(request, chartFile)) {
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
        } finally {
            if (transferDirectory != null) {
                try (var files = java.nio.file.Files.list(transferDirectory)) {
                    for (var file : files.toList()) java.nio.file.Files.deleteIfExists(file);
                    java.nio.file.Files.deleteIfExists(transferDirectory);
                } catch (java.io.IOException e) {
                    log.warn("Could not remove temporary Helm import files", e);
                }
            }
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
            Optional<HelmChart> existingHelmChart = findExistingHelmChart(request);
            HelmChart helmChart = existingHelmChart.orElse(null);
            if (helmChart == null) {
                helmChart = createBaseHelmChart(request, user);
            }
            if (existingHelmChart.isPresent()) {
                log.info("Updating existing Helm Chart: id={}, packageId={}, chartName={}, chartVersion={}",
                        helmChart.getId(), request.getPackageId(), request.getName(), request.getVersion());
            }

            helmChart.setPackageId(request.getPackageId());
            helmChart.setChartName(request.getName());
            helmChart.setChartVersion(request.getVersion());
            helmChart.setTag(request.getTag());
            helmChart.setAppVersion(request.getAppVersion());
            helmChart.setCategory(resolveCategory(request.getCategory(), null, request.getName()));
            if (user != null) {
                helmChart.setUser(user);
            }

            // ArtifactHub 데이터 추출 및 설정
            extractAndSetArtifactHubData(helmChart, chartData);

            // HELM_CHART 테이블에 저장
            HelmChart savedHelmChart = helmChartRepository.save(helmChart);

            log.info("Helm Chart details {} in HELM_CHART table: packageId={} (helmChartId: {})",
                    existingHelmChart.isPresent() ? "updated" : "saved",
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
            Optional<HelmChart> existingHelmChart = findExistingHelmChart(request);
            HelmChart helmChart = existingHelmChart.orElse(null);
            if (helmChart == null) {
                helmChart = createBaseHelmChart(request, user);
            }
            if (existingHelmChart.isPresent()) {
                log.info("Updating existing Helm Chart: id={}, packageId={}, chartName={}, chartVersion={}",
                        helmChart.getId(), request.getPackageId(), request.getName(), request.getVersion());
            }

            helmChart.setPackageId(request.getPackageId());
            helmChart.setChartName(request.getName());
            helmChart.setChartVersion(request.getVersion());
            helmChart.setTag(request.getTag());
            helmChart.setAppVersion(request.getAppVersion());
            helmChart.setCategory(resolveCategory(request.getCategory(), null, request.getName()));
            if (user != null) {
                helmChart.setUser(user);
            }

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
                // A chart's image is separate from its package. Never invent an internal image location.
                String originalImageRepository = request.getImageRepository();
                if (originalImageRepository != null && !originalImageRepository.trim().isEmpty()) {
                    // No verified image copy: preserve the original chart image metadata.
                    imageRepository = originalImageRepository;
                    log.info("Preserving original chart image metadata: {}", imageRepository);
                } else {
                    // Chart 이름을 기반으로 기본 이미지 저장소 설정
                    imageRepository = request.getName().toLowerCase().replaceAll("\\s+", "-");
                }
            }
            helmChart.setImageRepository(imageRepository);
            
            // 요청에 포함된 Repository 메타데이터 설정
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
            
            // An imported chart must use the exact artifact verified in Nexus.
            String chartRepositoryUrl = requireImportedChartRepositoryUrl(request);
            helmChart.setChartRepositoryUrl(chartRepositoryUrl);
            helmChart.setArtifactSource(ArtifactSource.NEXUS);
            helmChart.setRepositoryName(nexusIntegrationService.getRepositoryNameByFormat("helm"));
            log.info("Set Helm chart repository URL: {}", chartRepositoryUrl);
            helmChart.setValuesFile(request.getDocumentationUrl());
            // helmChart.setLicense(request.getLicense());

            // HELM_CHART 테이블에 저장
            HelmChart savedHelmChart = helmChartRepository.save(helmChart);

            log.info("Helm Chart details {} in HELM_CHART table: packageId={} (helmChartId: {})",
                    existingHelmChart.isPresent() ? "updated" : "saved",
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
        // 정확한 Chart 존재 여부에 따라 Nexus 또는 외부 Repository URL 선택
        String repositoryUrl = resolveChartRepositoryUrl(request);
        log.info("Resolved Helm Chart repository URL: {}", repositoryUrl);

        // Image metadata is separate from the imported chart package.
        String imageRepository = request.getImageRepository();
        if (imageRepository == null || imageRepository.trim().isEmpty()) {
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
                .chartRepositoryUrl(repositoryUrl)
                .category(request.getCategory())
                .imageRepository(imageRepository)
                .user(user)
                .build();
    }

    /**
     * Nexus Repository URL을 가져옵니다.
     */
    private Optional<HelmChart> findExistingHelmChart(HelmChartRegistrationRequest request) {
        String chartName = request.getName();
        String chartVersion = firstNonBlank(request.getVersion(), request.getTag());
        if (isBlank(chartName) || isBlank(chartVersion)) {
            return Optional.empty();
        }

        List<HelmChart> candidates = helmChartRepository.findByChartNameAndChartVersion(chartName, chartVersion);
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }
        // Importing a version must never rewrite the seeded catalog or an already linked package.
        candidates = candidates.stream().filter(chart -> chart.getArtifactSource() == ArtifactSource.NEXUS
                && chart.getCatalog() == null).toList();
        if (candidates.isEmpty()) return Optional.empty();

        if (!isBlank(request.getPackageId())) {
            Optional<HelmChart> packageMatched = candidates.stream()
                    .filter(candidate -> request.getPackageId().equals(candidate.getPackageId()))
                    .findFirst();
            if (packageMatched.isPresent()) {
                return packageMatched;
            }
        }

        return Optional.of(candidates.get(0));
    }

    private String resolveCategory(String requestedCategory, ArtifactHubPackage.Package detailInfo, String chartName) {
        String category = normalizeCategory(requestedCategory);
        if (!isBlank(category)) {
            return category;
        }

        if (detailInfo != null && detailInfo.getCategory() != null) {
            category = normalizeCategory(detailInfo.getCategory().name());
            if (!isBlank(category)) {
                return category;
            }
        }

        category = findCatalogCategoryByName(chartName);
        if (!isBlank(category)) {
            return category;
        }

        category = findPackageCategoryByName(chartName);
        if (!isBlank(category)) {
            return category;
        }

        return null;
    }

    private String findCatalogCategoryByName(String chartName) {
        if (isBlank(chartName)) {
            return null;
        }

        List<SoftwareCatalog> catalogs = catalogRepository.findByNameIgnoreCase(chartName);
        if (catalogs == null) {
            return null;
        }

        return catalogs.stream()
                .map(SoftwareCatalog::getCategory)
                .map(this::normalizeCategory)
                .filter(category -> !isBlank(category))
                .findFirst()
                .orElse(null);
    }

    private String findPackageCategoryByName(String chartName) {
        if (isBlank(chartName)) {
            return null;
        }

        List<PackageInfo> packageInfos = packageInfoRepository.findCategoryCandidatesByPackageName(chartName);
        if (packageInfos == null) {
            return null;
        }

        return packageInfos.stream()
                .map(PackageInfo::getCategories)
                .map(this::firstCategory)
                .map(this::normalizeCategory)
                .filter(category -> !isBlank(category))
                .findFirst()
                .orElse(null);
    }

    private String firstCategory(String categories) {
        if (isBlank(categories)) {
            return null;
        }
        return categories.split(",")[0];
    }

    private String normalizeCategory(String category) {
        if (isBlank(category)) {
            return null;
        }

        String normalized = category.trim()
                .replace("&", "AND")
                .replaceAll("[^a-zA-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "")
                .replaceAll("_+", "_")
                .toUpperCase(Locale.ROOT);

        if ("AI_ML".equals(normalized)) {
            return "AI_MACHINE_LEARNING";
        }
        if ("MONITORING_LOGGING".equals(normalized)) {
            return "MONITORING_AND_LOGGING";
        }

        return normalized;
    }

    private String resolveChartRepositoryUrl(HelmChartRegistrationRequest request) {
        try {
            String helmRepositoryName = nexusIntegrationService.getRepositoryNameByFormat("helm");
            String chartVersion = firstNonBlank(request.getVersion(), request.getTag());
            boolean chartExistsInNexus = nexusIntegrationService.checkHelmChartExistsInNexus(
                    helmRepositoryName,
                    request.getName(),
                    chartVersion);

            if (chartExistsInNexus) {
                OssDto nexusInfo = nexusIntegrationService.getNexusInfoFromDB();
                String nexusRepositoryUrl = nexusInfo.getOssUrl() + "/repository/" + helmRepositoryName;
                log.info("Using Nexus Helm repository because the exact Chart exists: repository={}, chart={}, version={}",
                        helmRepositoryName, request.getName(), chartVersion);
                return nexusRepositoryUrl;
            }

            log.warn("Exact Helm Chart is not available in Nexus. Falling back to requested repository URL: "
                            + "repository={}, chart={}, version={}",
                    helmRepositoryName, request.getName(), chartVersion);
        } catch (Exception e) {
            log.warn("Nexus Helm repository is not available. Falling back to requested repository URL: {}", e.getMessage());
        }

        if (request.getRepository() != null && !isBlank(request.getRepository().getUrl())) {
            return request.getRepository().getUrl();
        }

        return "";
    }

    private String requireImportedChartRepositoryUrl(HelmChartRegistrationRequest request) {
        String repository = nexusIntegrationService.getRepositoryNameByFormat("helm");
        if (!nexusIntegrationService.checkHelmChartExistsInNexus(repository, request.getName(),
                firstNonBlank(request.getVersion(), request.getTag()))) {
            throw new IllegalArgumentException("The imported Helm chart could not be verified in Nexus");
        }
        return getNexusRepositoryUrl();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }

        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }

        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

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
        String resolvedCategory = resolveCategory(
                chartData.get("category") != null ? chartData.get("category").toString() : helmChart.getCategory(),
                null,
                helmChart.getChartName());
        if (!isBlank(resolvedCategory)) {
            helmChart.setCategory(resolvedCategory);
        }
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
            try {
                String nexusRepositoryUrl = getNexusRepositoryUrl();
                if (!isBlank(nexusRepositoryUrl)) {
                    helmChart.setChartRepositoryUrl(nexusRepositoryUrl);
                    log.info("Set Nexus repository URL: {}", nexusRepositoryUrl);
                }
            } catch (Exception e) {
                log.warn("Nexus Helm repository is not available. Keeping current repository URL: {}", e.getMessage());
            }
            setStringValue(helmChart::setRepositoryName, repository.get("name"));
            setStringValue(helmChart::setRepositoryDisplayName, repository.get("display_name"));
            helmChart.setRepositoryOfficial(false); // Nexus는 공식 저장소가 아님
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
    private java.nio.file.Path downloadHelmChart(HelmChartRegistrationRequest request, java.nio.file.Path directory) {
        log.info("Downloading Helm Chart: {}:{} from {}", request.getName(), request.getVersion(), request.getRepository().getUrl());

        try {
            // Repository URL이 null인 경우 처리
            if (request.getRepository().getUrl() == null || request.getRepository().getUrl().isEmpty()) {
                log.error("Repository URL is null or empty for Helm Chart: {}:{}", request.getName(), request.getVersion());
                return null;
            }

            String repository = request.getRepository().getUrl();
            String version = firstNonBlank(request.getVersion(), request.getTag());
            var command = new java.util.ArrayList<String>(List.of("helm", "pull"));
            if (repository.startsWith("oci://")) {
                command.add(repository.replaceAll("/+$", "") + "/" + request.getName());
            } else {
                command.add(request.getName());
                command.addAll(List.of("--repo", repository));
            }
            command.addAll(List.of("--version", version, "--destination", directory.toString()));
            java.nio.file.Path output = directory.resolve("download.log");
            Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile()).start();
            if (!process.waitFor(90, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
                log.error("Helm chart download timed out: {}:{}", request.getName(), version);
                return null;
            }
            if (process.exitValue() != 0) {
                log.error("Helm chart download failed: {}", java.nio.file.Files.readString(output));
                return null;
            }
            try (var files = java.nio.file.Files.list(directory)) {
                var archives = files.filter(path -> path.getFileName().toString().endsWith(".tgz")).toList();
                if (archives.size() != 1) throw new IllegalStateException("Helm did not download exactly one chart archive");
                return archives.get(0);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            log.error("Error downloading Helm Chart: {}:{}", request.getName(), request.getVersion(), e);
            return null;
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

        return true;
        // 허용된 공식 저장소 목록
        // String[] officialRepositories = {
        //     "charts.bitnami.com",
        //     "kubernetes-charts.storage.googleapis.com",
        //     "https://kubernetes-charts.storage.googleapis.com",
        //     "https://charts.bitnami.com/bitnami",
        //     "cowboysysop.github.io",
        //     "https://cowboysysop.github.io/charts/",
        //     "charts.jfrog.io",
        //     "https://charts.jfrog.io",
        //     "marketplace.azurecr.io",
        //     "https://marketplace.azurecr.io/helm/v1/repo",
        //     "kube-ops.gitlab.io",
        //     "https://kube-ops.gitlab.io/helm/repository",
        //     "grafana.github.io",
        //     "https://grafana.github.io/helm-charts",
        //     "prometheus-community.github.io",
        //     "https://prometheus-community.github.io/helm-charts"
        // };

        // for (String officialRepo : officialRepositories) {
        //     if (repositoryUrl.contains(officialRepo)) {
        //         log.info("Repository verified as official: {}", repositoryUrl);
        //         return true;
        //     }
        // }

        // log.warn("Repository not in official list: {}", repositoryUrl);
        // return false;
    }



    /**
     * Helm Chart를 Nexus에 업로드합니다.
     */
    private boolean uploadHelmChartToNexus(HelmChartRegistrationRequest request, java.nio.file.Path archive) {
        log.info("Uploading Helm Chart to Nexus: {}:{}", request.getName(), request.getTag());

        try {
            // Nexus 정보 가져오기
            var nexusInfo = nexusIntegrationService.getNexusInfoFromDB();
            if (nexusInfo == null) {
                log.error("Nexus information not found");
                return false;
            }

            // 다운로드된 Helm Chart 파일명 (.tgz 확장자 사용)
            String chartFileName = archive.toAbsolutePath().toString();

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

            String chartVersion = firstNonBlank(request.getVersion(), request.getTag());
            if (!nexusIntegrationService.checkHelmChartExistsInNexus(
                    helmRepositoryName, request.getName(), chartVersion)) {
                log.error("Uploaded Helm Chart could not be verified in Nexus: repository={}, chart={}, version={}",
                        helmRepositoryName, request.getName(), chartVersion);
                deleteTemporaryChartFile(chartFile, chartFileName);
                return false;
            }

            // 임시 파일 삭제
            deleteTemporaryChartFile(chartFile, chartFileName);

            log.info("Successfully uploaded Helm Chart to Nexus: {}:{}", request.getName(), request.getVersion());
            return true;

        } catch (Exception e) {
            log.error("Error uploading Helm Chart to Nexus: {}:{}", request.getName(), request.getVersion(), e);
            return false;
        }
    }

    private void deleteTemporaryChartFile(java.io.File chartFile, String chartFileName) {
        try {
            if (chartFile.delete()) {
                log.info("Temporary Helm Chart file deleted: {}", chartFileName);
            }
        } catch (Exception e) {
            log.warn("Failed to delete temporary Helm Chart file: {}", chartFileName);
        }
    }
}
