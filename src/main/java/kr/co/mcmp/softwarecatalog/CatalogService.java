package kr.co.mcmp.softwarecatalog;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.persistence.EntityNotFoundException;

import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import kr.co.mcmp.dto.oss.repository.CommonRepository;
import kr.co.mcmp.oss.dto.OssDto;
import kr.co.mcmp.service.oss.repository.CommonModuleRepositoryService;
import kr.co.mcmp.softwarecatalog.Ref.CatalogRefDTO;
import kr.co.mcmp.softwarecatalog.Ref.CatalogRefEntity;
import kr.co.mcmp.softwarecatalog.Ref.CatalogRefRepository;
import kr.co.mcmp.softwarecatalog.application.dto.DockerHubImageRegistrationRequest;
import kr.co.mcmp.softwarecatalog.application.dto.HelmChartDTO;
import kr.co.mcmp.softwarecatalog.application.dto.HelmChartRegistrationRequest;
import kr.co.mcmp.softwarecatalog.application.dto.PackageInfoDTO;
import kr.co.mcmp.softwarecatalog.application.model.HelmChart;
import kr.co.mcmp.softwarecatalog.application.model.PackageInfo;
import kr.co.mcmp.softwarecatalog.application.repository.HelmChartRepository;
import kr.co.mcmp.softwarecatalog.application.repository.PackageInfoRepository;
import kr.co.mcmp.softwarecatalog.application.service.ArtifactHubIntegrationService;
import kr.co.mcmp.softwarecatalog.application.service.DockerHubIntegrationService;
import kr.co.mcmp.softwarecatalog.application.service.HelmChartIntegrationService;
import kr.co.mcmp.softwarecatalog.application.service.NexusIntegrationService;
import kr.co.mcmp.softwarecatalog.users.Entity.User;
import kr.co.mcmp.softwarecatalog.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@Log4j2
@RequiredArgsConstructor
public class CatalogService {

    private final CatalogRepository catalogRepository;
    private final CatalogRefRepository catalogRefRepository;
    private final PackageInfoRepository packageInfoRepository;
    private final HelmChartRepository helmChartRepository;
    private final UserRepository userRepository;
    private final CommonModuleRepositoryService moduleRepositoryService;
    private final NexusIntegrationService nexusIntegrationService;
    private final DockerHubIntegrationService dockerHubIntegrationService;
    private final HelmChartIntegrationService helmChartIntegrationService;
    private final ArtifactHubIntegrationService artifactHubIntegrationService;
    private final RestTemplate restTemplate;

    @Transactional
    public SoftwareCatalogDTO createCatalog(SoftwareCatalogDTO catalogDTO, String username) {
        User user = null;
        if (StringUtils.isNotBlank(username)) {
            user = getUserByUsername(username);
        }

        
        // 1. 내부 DB에 카탈로그 등록
        SoftwareCatalog catalog = catalogDTO.toEntity();
        catalog.setRegisteredBy(user);
        catalog.setCreatedAt(LocalDateTime.now());
        catalog.setUpdatedAt(LocalDateTime.now());

        catalog = catalogRepository.save(catalog);

        // 2. catalogRefs 저장 (catalog id와 함께)
        if (catalogDTO.getCatalogRefs() != null && !catalogDTO.getCatalogRefs().isEmpty()) {
            for (CatalogRefDTO refDTO : catalogDTO.getCatalogRefs()) {
                CatalogRefEntity refEntity = refDTO.toEntity();
                refEntity.setCatalog(catalog); // catalog id 설정
                catalogRefRepository.save(refEntity);
            }
        }

        // 3. sourceType에 따라 기존 데이터 조회하고 catalogId 업데이트
        if (catalogDTO.getSourceType() != null) {
            if ("DOCKERHUB".equalsIgnoreCase(catalogDTO.getSourceType()) ) {
                // DOCKERHUB인 경우 PACKAGE_INFO 테이블에서 조회 후 catalogId 업데이트
                updatePackageInfoCatalogId(catalog, catalogDTO);
            } else if ("ARTIFACTHUB".equalsIgnoreCase(catalogDTO.getSourceType())) {
                // ARTIFACTHUB인 경우 HELM_CHART 테이블에서 조회 후 catalogId 업데이트  
                updateHelmChartCatalogId(catalog, catalogDTO);
            }
        }

        SoftwareCatalogDTO result = SoftwareCatalogDTO.fromEntity(catalog);

        return result;
    }
    
    /**
     * 내부 DB에만 카탈로그를 등록합니다 (넥서스 연동 없이)
     */
    @Transactional
    public SoftwareCatalogDTO createCatalogInternal(SoftwareCatalogDTO catalogDTO, User user) {
        
        SoftwareCatalog paramCatalog = catalogDTO.toEntity();
        paramCatalog.setRegisteredBy(user);
        paramCatalog.setCreatedAt(LocalDateTime.now());
        paramCatalog.setUpdatedAt(LocalDateTime.now());

        paramCatalog = catalogRepository.save(paramCatalog);

        if (catalogDTO.getCatalogRefs() != null && !catalogDTO.getCatalogRefs().isEmpty()) {
            for (CatalogRefDTO refDTO : catalogDTO.getCatalogRefs()) {
                CatalogRefEntity refEntity = refDTO.toEntity();
                refEntity.setCatalog(paramCatalog);
                catalogRefRepository.save(refEntity);
            }
        }


        SoftwareCatalog catalog = SoftwareCatalog.builder()
                .name(catalogDTO.getName())
                .description(catalogDTO.getDescription())
                .version(catalogDTO.getVersion())
                .category(catalogDTO.getCategory())
                .license(catalogDTO.getLicense())
                .homepage(catalogDTO.getHomepage())
                .repositoryUrl(catalogDTO.getRepositoryUrl())
                .documentationUrl(catalogDTO.getDocumentationUrl())
                .registeredBy(user)
                .build();

        SoftwareCatalog savedCatalog = catalogRepository.save(catalog);

        // PackageInfo 저장
        if (catalogDTO.getPackageInfo() != null) {
            savePackageInfo(savedCatalog, catalogDTO.getPackageInfo());
        }

        // HelmChart 저장
        if (catalogDTO.getHelmChart() != null) {
            saveHelmChart(savedCatalog, catalogDTO.getHelmChart());
        }

        // Nexus에 등록 (주석 처리)
        // try {
        // nexusIntegrationService.registerToNexus(result);
        // log.info("Application registered to Nexus successfully: {}",
        // result.getName());
        // } catch (Exception e) {
        // log.warn("Failed to register application to Nexus: {}", result.getName(), e);
        // }

        return SoftwareCatalogDTO.fromEntity(savedCatalog);
    }

    @Transactional(readOnly = true)
    public SoftwareCatalogDTO getCatalog(Long catalogId) {
        SoftwareCatalog catalog = catalogRepository.findById(catalogId)
                .orElseThrow(() -> new EntityNotFoundException("Catalog not found with id: " + catalogId));

        SoftwareCatalogDTO dto = SoftwareCatalogDTO.fromEntity(catalog);

        // PackageInfo 조회
        packageInfoRepository.findByCatalogId(catalogId)
                .ifPresent(packageInfo -> dto.setPackageInfo(PackageInfoDTO.fromEntity(packageInfo)));

        // HelmChart 조회
        helmChartRepository.findByCatalogId(catalogId)
                .ifPresent(helmChart -> dto.setHelmChart(HelmChartDTO.fromEntity(helmChart)));

        return dto;
    }

    @Transactional(readOnly = true)
    public List<SoftwareCatalogDTO> getAllCatalogs() {
        List<SoftwareCatalog> catalogs = catalogRepository.findAll();
        return catalogs.stream()
                .map(catalog -> {
                    SoftwareCatalogDTO dto = SoftwareCatalogDTO.fromEntity(catalog);

                    // PackageInfo 조회
                    packageInfoRepository.findByCatalogId(catalog.getId())
                            .ifPresent(packageInfo -> dto.setPackageInfo(PackageInfoDTO.fromEntity(packageInfo)));

                    // HelmChart 조회
                    helmChartRepository.findByCatalogId(catalog.getId())
                            .ifPresent(helmChart -> dto.setHelmChart(HelmChartDTO.fromEntity(helmChart)));

                    return dto;
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public SoftwareCatalogDTO updateCatalog(Long catalogId, SoftwareCatalogDTO catalogDTO) {
        SoftwareCatalog catalog = catalogRepository.findById(catalogId)
                .orElseThrow(() -> new EntityNotFoundException("Catalog not found with id: " + catalogId));

        catalog.updateFromDTO(catalogDTO);

        // PackageInfo 업데이트
        if (catalogDTO.getPackageInfo() != null) {
            updatePackageInfo(catalog, catalogDTO.getPackageInfo());
        }

        // HelmChart 업데이트
        if (catalogDTO.getHelmChart() != null) {
            updateHelmChart(catalog, catalogDTO.getHelmChart());
        }

        SoftwareCatalog savedCatalog = catalogRepository.save(catalog);

        // Nexus에서 삭제 (주석 처리)
        // try {
        // nexusIntegrationService.deleteFromNexus(catalog.getName());
        // log.info("Application deleted from Nexus successfully: {}",
        // catalog.getName());
        // } catch (Exception e) {
        // log.warn("Failed to delete application from Nexus: {}", catalog.getName(),
        // e);
        // }

        return SoftwareCatalogDTO.fromEntity(savedCatalog);
    }

    @Transactional

    public void deleteCatalog(Long catalogId) {
        SoftwareCatalog catalog = catalogRepository.findById(catalogId)
                .orElseThrow(() -> new EntityNotFoundException("Catalog not found"));
        // 1. 넥서스에서 애플리케이션 삭제
        try {
            nexusIntegrationService.deleteFromNexus(catalog.getName());
            log.info("Application deleted from Nexus successfully: {}", catalog.getName());
        } catch (Exception e) {
            log.warn("Failed to delete application from Nexus: {}", catalog.getName(), e);
            // 넥서스 삭제 실패해도 DB 삭제는 진행
        }
        // 2. 내부 DB에서 카탈로그 삭제
        catalogRefRepository.deleteAllByCatalogId(catalogId);
        // packageInfoRepository.deleteByCatalogId(catalogId);
        // helmChartRepository.deleteByCatalogId(catalogId);
        catalogRepository.delete(catalog);
        log.info("Software catalog deleted successfully with ID: {}", catalogId);
    }

    // ==================== PackageInfo 관련 메서드 ====================

    private void savePackageInfo(SoftwareCatalog catalog, PackageInfoDTO packageInfoDTO) {
        PackageInfo packageInfo = packageInfoDTO.toEntity();
        packageInfo.setCatalog(catalog);
        packageInfoRepository.save(packageInfo);
    }

    private void updatePackageInfo(SoftwareCatalog catalog, PackageInfoDTO packageInfoDTO) {
        PackageInfo packageInfo = packageInfoRepository.findByCatalogId(catalog.getId())
                .orElse(new PackageInfo());
        packageInfo.updateFromDTO(packageInfoDTO);
        packageInfo.setCatalog(catalog);
        packageInfoRepository.save(packageInfo);
    }

    // ==================== HelmChart 관련 메서드 ====================

    private void saveHelmChart(SoftwareCatalog catalog, HelmChartDTO helmChartDTO) {
        HelmChart helmChart = helmChartDTO.toEntity();
        helmChart.setCatalog(catalog);
        helmChartRepository.save(helmChart);
    }

    private void updateHelmChart(SoftwareCatalog catalog, HelmChartDTO helmChartDTO) {
        HelmChart helmChart = helmChartRepository.findByCatalogId(catalog.getId())
                .orElse(new HelmChart());
        helmChart.updateFromDTO(helmChartDTO);
        helmChart.setCatalog(catalog);
        helmChartRepository.save(helmChart);
    }

    // ==================== Nexus 통합 관련 메서드 ====================

    @Transactional(readOnly = true)
    public List<CombinedCatalogDTO> getAllCatalogsWithNexusInfo() {
        List<SoftwareCatalogDTO> catalogs = getAllCatalogs();
        List<CommonRepository.RepositoryDto> nexusRepositories = moduleRepositoryService.getRepositoryList("nexus");

        // Nexus repository를 Map으로 변환 (이름을 키로 사용)
        Map<String, CommonRepository.RepositoryDto> nexusRepoMap = nexusRepositories.stream()
                .collect(Collectors.toMap(
                        CommonRepository.RepositoryDto::getName,
                        Function.identity(),
                        (existing, replacement) -> existing));

        log.info("Nexus Repository Map:");
        nexusRepoMap.forEach((key, value) -> {
            log.info("  {}: {}", key, value.getUrl());
        });

        return catalogs.stream()
                .map(catalog -> {
                    CombinedCatalogDTO combinedDTO = new CombinedCatalogDTO();
                    combinedDTO.setCatalog(catalog);

                    // Nexus repository 정보 매핑
                    CommonRepository.RepositoryDto nexusRepo = nexusRepoMap.get(catalog.getName());
                    if (nexusRepo != null) {
                        combinedDTO.setRepositoryDTO(nexusRepo);
                    }

                    return combinedDTO;
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CombinedCatalogDTO getCatalogWithNexusInfo(Long catalogId) {
        SoftwareCatalogDTO catalog = getCatalog(catalogId);

        // Nexus repository 정보 조회
        List<CommonRepository.RepositoryDto> nexusRepositories = moduleRepositoryService.getRepositoryList("nexus");
        CommonRepository.RepositoryDto nexusRepo = nexusRepositories.stream()
                .filter(repo -> Objects.equals(repo.getName(), catalog.getName()))
                .findFirst()
                .orElse(null);

        CombinedCatalogDTO combinedDTO = new CombinedCatalogDTO();
        combinedDTO.setCatalog(catalog);
        combinedDTO.setRepositoryDTO(nexusRepo);

        return combinedDTO;
    }

    // ==================== Docker Hub 통합 관련 메서드 ====================

    @Transactional
    public Map<String, Object> pushImageAndRegisterCatalog(SoftwareCatalogDTO catalog) {
        log.info("Pushing image and registering catalog: {}", catalog.getName());

        Map<String, Object> result = new HashMap<>();

        try {
            // Nexus에 이미지 푸시 및 카탈로그 등록
            Map<String, Object> nexusResult = nexusIntegrationService.pushImageAndRegisterCatalog(catalog);

            result.put("success", true);
            result.put("message", "Image pushed and catalog registered successfully");
            result.put("nexusResult", nexusResult);

        } catch (Exception e) {
            log.error("Failed to push image and register catalog: {}", catalog.getName(), e);
            result.put("success", false);
            result.put("message", "Failed to push image and register catalog: " + e.getMessage());
        }

        return result;
    }

    public boolean checkImageExistsInNexus(String imageName, String tag) {
        return nexusIntegrationService.checkImageExistsInNexus(imageName, tag);
    }

    public Map<String, Object> pushImageToNexus(String imageName, String tag, byte[] imageData) {
        return nexusIntegrationService.pushImageToNexus(imageName, tag, imageData);
    }

    public Map<String, Object> pullImageFromNexus(String imageName, String tag) {
        return nexusIntegrationService.pullImageFromNexus(imageName, tag);
    }

    public Map<String, Object> pullImageFromNexusWithAuth(String imageName, String tag) {
        log.info("Pulling image from Nexus with authentication: {}:{}", imageName, tag);

        Map<String, Object> result = new HashMap<>();

        try {
            // Nexus에서 이미지 풀
            Map<String, Object> pullResult = nexusIntegrationService.pullImageFromNexus(imageName, tag);

            if ((Boolean) pullResult.get("success")) {
                result.put("success", true);
                result.put("message", "Image pulled successfully from Nexus");
                result.put("imageUrl", pullResult.get("imageUrl"));
                result.put("nexusInfo", pullResult.get("nexusInfo"));
            } else {
                result.put("success", false);
                result.put("message", "Failed to pull image from Nexus: " + pullResult.get("message"));
            }

        } catch (Exception e) {
            log.error("Error pulling image from Nexus: {}:{}", imageName, tag, e);
            result.put("success", false);
            result.put("message", "Error pulling image from Nexus: " + e.getMessage());
        }

        return result;
    }

    public List<String> getImageTagsFromNexus(String imageName) {
        return nexusIntegrationService.getImageTagsFromNexus(imageName);
    }

    // ==================== Docker Hub 이미지 등록 관련 메서드 ====================

    @Transactional
    public Map<String, Object> registerDockerHubImage(DockerHubImageRegistrationRequest request, String username) {
        log.info("Registering Docker Hub image: {}:{}", request.getImageName(), request.getTag());

        Map<String, Object> result = new HashMap<>();

        try {
            // 1. Docker Hub에서 이미지 정보 조회
            Map<String, Object> imageInfo = dockerHubIntegrationService.getImageInfo(request.getImageName(),
                    request.getTag());
            if (!(Boolean) imageInfo.get("success")) {
                result.put("success", false);
                result.put("message", "Failed to get image info from Docker Hub: " + imageInfo.get("message"));
                return result;
            }

            // 2. 소프트웨어 카탈로그 생성
            SoftwareCatalogDTO catalogDTO = createCatalogFromDockerImage(request, imageInfo, username);
            SoftwareCatalogDTO savedCatalog = createCatalog(catalogDTO, username);

            // 3. Docker Hub 이미지를 Nexus에 푸시
            Map<String, Object> pushResult = dockerHubIntegrationService.pushImageToNexus(request.getImageName(),
                    request.getTag());

            result.put("success", true);
            result.put("message", "Docker Hub image registered successfully");
            result.put("catalog", savedCatalog);
            result.put("nexusPush", pushResult);

        } catch (Exception e) {
            log.error("Failed to register Docker Hub image: {}:{}", request.getImageName(), request.getTag(), e);
            result.put("success", false);
            result.put("message", "Failed to register Docker Hub image: " + e.getMessage());
        }

        return result;
    }

    private SoftwareCatalogDTO createCatalogFromDockerImage(DockerHubImageRegistrationRequest request,
            Map<String, Object> imageInfo, String username) {
        @SuppressWarnings("unchecked")
        Map<String, Object> imageData = (Map<String, Object>) imageInfo.get("data");

        return SoftwareCatalogDTO.builder()
                .name(request.getImageName())
                .description((String) imageData.get("description"))
                .version(request.getTag())
                .category("Docker Image")
                .license((String) imageData.get("license"))
                .homepage((String) imageData.get("homepage"))
                .repositoryUrl((String) imageData.get("repository_url"))
                .documentationUrl((String) imageData.get("documentation_url"))
                .build();
    }

    private Map<String, Object> createDockerImageInfo(Map<String, Object> imageData) {
        return Map.of(
                "name", imageData.get("name"),
                "description", imageData.get("description"),
                "version", imageData.get("version"),
                "license", imageData.get("license"),
                "homepage", imageData.get("homepage"),
                "repository_url", imageData.get("repository_url"),
                "documentation_url", imageData.get("documentation_url"),
                "status", imageData.get("status"),
                "is_private", imageData.get("is_private"));
    }

    // ==================== Helm Chart 관련 메서드 ====================

    /**
     * ArtifactHub에서 Helm Chart를 검색합니다.
     */
    public Map<String, Object> searchHelmCharts(String query, int page, int pageSize) {
        return helmChartIntegrationService.searchHelmCharts(query, page, pageSize);
    }

    /**
     * ArtifactHub에서 Helm Chart 상세 정보를 조회합니다.
     */
    public Map<String, Object> getHelmChartDetails(String packageId) {
        return helmChartIntegrationService.getHelmChartDetails(packageId);
    }

    /**
     * ArtifactHub에서 Helm Chart 버전 목록을 조회합니다.
     */
    public List<String> getHelmChartVersions(String packageId) {
        return helmChartIntegrationService.getHelmChartVersions(packageId);
    }

    /**
     * Helm Chart를 등록하고 Nexus에 푸시합니다.
     */
    public Map<String, Object> registerHelmChart(HelmChartRegistrationRequest request, String username) {
        return helmChartIntegrationService.registerHelmChart(request, username);
    }

    // ==================== Docker Hub 관련 메서드 ====================

    /**
     * Docker Hub에서 이미지를 검색합니다.
     */
    public Map<String, Object> searchDockerHubImages(String query, int page, int pageSize) {
        return dockerHubIntegrationService.searchDockerHubImages(query, page, pageSize);
    }

    /**
     * Docker Hub에서 이미지 상세 정보를 조회합니다.
     */
    public Map<String, Object> getDockerHubImageDetails(String imageName, String tag) {
        return dockerHubIntegrationService.getDockerHubImageDetails(imageName, tag);
    }

    /**
     * 카탈로그 ID로 이미지를 풀합니다.
     */
    public Map<String, Object> pullImageByCatalogId(Long catalogId) {
        SoftwareCatalogDTO catalog = getCatalog(catalogId);
        if (catalog == null) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "Catalog not found with id: " + catalogId);
            return result;
        }

        return pullImageFromNexusWithAuth(catalog.getName(), catalog.getVersion());
    }

    /**
     * DOCKERHUB 소스타입인 경우 PACKAGE_INFO 테이블에서 기존 데이터를 조회하고 catalogId만 업데이트합니다.
     */
    private void updatePackageInfoCatalogId(SoftwareCatalog catalog, SoftwareCatalogDTO catalogDTO) {
        try {
            String category = catalogDTO.getCategory();
            String packageName = catalogDTO.getPackageName();
            String packageVersion = catalogDTO.getVersion();
            
            log.info("Searching for existing PackageInfo with category: {}, name: {}, version: {}", 
                    category, packageName, packageVersion);
            
            // category, packageName, packageVersion으로 기존 PackageInfo 조회 (catalog_id가 null인 것)
            List<PackageInfo> existingPackageInfos = packageInfoRepository.findByCategoriesAndCatalogIsNull(category);
            
            PackageInfo targetPackageInfo = existingPackageInfos.stream()
                    .filter(pkg -> packageName.equals(pkg.getPackageName()) && 
                                  packageVersion.equals(pkg.getPackageVersion()))
                    .findFirst()
                    .orElse(null);
            
            if (targetPackageInfo != null) {
                // catalogId만 업데이트 (기존 데이터는 그대로 유지)
                targetPackageInfo.setCatalog(catalog);
                packageInfoRepository.save(targetPackageInfo);
                log.info("Updated PackageInfo catalogId only: {} -> {}", targetPackageInfo.getId(), catalog.getId());
            } else {
                log.warn("No matching PackageInfo found for category: {}, name: {}, version: {}", 
                        category, packageName, packageVersion);
            }
            
        } catch (Exception e) {
            log.error("Failed to update PackageInfo catalogId", e);
        }
    }

    /**
     * ARTIFACTHUB 소스타입인 경우 HELM_CHART 테이블에서 기존 데이터를 조회하고 catalogId만 업데이트합니다.
     */
    private void updateHelmChartCatalogId(SoftwareCatalog catalog, SoftwareCatalogDTO catalogDTO) {
        try {
            String category = catalogDTO.getCategory();
            String chartName = catalogDTO.getPackageName();
            String chartVersion = catalogDTO.getVersion();
            
            log.info("Searching for existing HelmChart with category: {}, name: {}, version: {}", 
                    category, chartName, chartVersion);
            
            // category, chartName, chartVersion으로 기존 HelmChart 조회 (catalog_id가 null인 것)
            List<HelmChart> existingHelmCharts = helmChartRepository.findByCategoryAndCatalogIsNull(category);
            
            HelmChart targetHelmChart = existingHelmCharts.stream()
                    .filter(chart -> chartName.equals(chart.getChartName()) && 
                                    chartVersion.equals(chart.getChartVersion()))
                    .findFirst()
                    .orElse(null);
            
            if (targetHelmChart != null) {
                // catalogId만 업데이트 (기존 데이터는 그대로 유지)
                targetHelmChart.setCatalog(catalog);
                helmChartRepository.save(targetHelmChart);
                log.info("Updated HelmChart catalogId only: {} -> {}", targetHelmChart.getId(), catalog.getId());
            } else {
                log.warn("No matching HelmChart found for category: {}, name: {}, version: {}", 
                        category, chartName, chartVersion);
            }
            
        } catch (Exception e) {
            log.error("Failed to update HelmChart catalogId", e);
        }
    }



    // ==================== 유틸리티 메서드 ====================

    private User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("User not found with username: " + username));
    }
}