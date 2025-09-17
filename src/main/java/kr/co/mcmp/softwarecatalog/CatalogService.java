package kr.co.mcmp.softwarecatalog;

import kr.co.mcmp.dto.oss.repository.CommonRepository;
import kr.co.mcmp.service.oss.repository.CommonModuleRepositoryService;
import kr.co.mcmp.softwarecatalog.Ref.CatalogRefDTO;
import kr.co.mcmp.softwarecatalog.Ref.CatalogRefEntity;
import kr.co.mcmp.softwarecatalog.Ref.CatalogRefRepository;
import kr.co.mcmp.softwarecatalog.application.dto.HelmChartDTO;
import kr.co.mcmp.softwarecatalog.application.dto.PackageInfoDTO;
import kr.co.mcmp.softwarecatalog.application.model.HelmChart;
import kr.co.mcmp.softwarecatalog.application.model.PackageInfo;
import kr.co.mcmp.softwarecatalog.application.repository.ApplicationStatusRepository;
import kr.co.mcmp.softwarecatalog.application.repository.DeploymentHistoryRepository;
import kr.co.mcmp.softwarecatalog.application.repository.HelmChartRepository;
import kr.co.mcmp.softwarecatalog.application.repository.PackageInfoRepository;
import kr.co.mcmp.softwarecatalog.rating.repository.OverallRatingRepository;
import kr.co.mcmp.softwarecatalog.application.constants.ActionType;
import kr.co.mcmp.softwarecatalog.util.NumberFormatUtil;
import kr.co.mcmp.softwarecatalog.category.entity.IngressConfig;
import kr.co.mcmp.softwarecatalog.category.repository.IngressConfigRepository;
import kr.co.mcmp.softwarecatalog.users.Entity.User;
import kr.co.mcmp.softwarecatalog.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.EntityNotFoundException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private final IngressConfigRepository ingressConfigRepository;
    private final PortMappingRepository portMappingRepository;
    private final OverallRatingRepository overallRatingRepository;
    private final DeploymentHistoryRepository deploymentHistoryRepository;
    private final ApplicationStatusRepository applicationStatusRepository;
    private final EntityManager entityManager;

    @Transactional
    public SoftwareCatalogDTO createCatalog(SoftwareCatalogDTO catalogDTO, String username) {
        User user = null;
        if (StringUtils.isNotBlank(username)) {
            user = getUserByUsername(username);
        }

        // 1. 내부 DB에 카탈로그 등록 (catalogRefs 제외)
        SoftwareCatalog catalog = catalogDTO.toEntity();
        catalog.setCatalogRefs(null); // 중복 저장 방지를 위해 일시적으로 null 설정
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
                if(catalogDTO.getIngressEnabled()){
                    saveIngressConfig(catalog, catalogDTO.getIngressUrl());
                }
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

        SoftwareCatalog catalog = catalogDTO.toEntity();
        catalog.setRegisteredBy(user);
        catalog.setCreatedAt(LocalDateTime.now());
        catalog.setUpdatedAt(LocalDateTime.now());

        catalog = catalogRepository.save(catalog);

        if (catalogDTO.getCatalogRefs() != null && !catalogDTO.getCatalogRefs().isEmpty()) {
            for (CatalogRefDTO refDTO : catalogDTO.getCatalogRefs()) {
                CatalogRefEntity refEntity = refDTO.toEntity();
                refEntity.setCatalog(catalog);
                catalogRefRepository.save(refEntity);
            }
        }

        if (catalogDTO.getPackageInfo() != null) {
            savePackageInfo(catalog, catalogDTO.getPackageInfo());
        } else if (catalogDTO.getHelmChart() != null) {
            saveHelmChart(catalog, catalogDTO.getHelmChart());
        }

        return SoftwareCatalogDTO.fromEntity(catalog);
    }

    public SoftwareCatalogDTO getCatalog(Long catalogId) {
        SoftwareCatalog catalog = catalogRepository.findById(catalogId)
                .orElseThrow(() -> new EntityNotFoundException("Catalog not found"));

        SoftwareCatalogDTO dto = SoftwareCatalogDTO.fromEntity(catalog);

        List<CatalogRefEntity> refs = catalogRefRepository.findByCatalogId(catalogId);

        if("ARTIFACTHUB".equals(dto.getSourceType())) {
            IngressConfig ingressConfig = ingressConfigRepository.findByCatalogId(catalogId);
            if(ingressConfig != null){
                dto.setIngressEnabled(true);
                dto.setIngressUrl(ingressConfig.getPath());
            } else {
                dto.setIngressEnabled(false);
            }
        } else {
            dto.setIngressEnabled(false);
        }

        dto.setCatalogRefs(refs.stream().map(CatalogRefDTO::fromEntity).collect(Collectors.toList()));

        packageInfoRepository.findByCatalogId(catalogId).ifPresent(packageInfo -> dto.setPackageInfo(PackageInfoDTO.fromEntity(packageInfo)));

        helmChartRepository.findByCatalogId(catalogId).ifPresent(helmChart -> dto.setHelmChart(HelmChartDTO.fromEntity(helmChart)));

        return dto;
    }

    public List<SoftwareCatalogDTO> getAllCatalogs() {
        List<SoftwareCatalog> catalogs = catalogRepository.findAll();
        List<SoftwareCatalogDTO> dtos = new ArrayList<>();

        for (SoftwareCatalog catalog : catalogs) {
            SoftwareCatalogDTO dto = SoftwareCatalogDTO.fromEntity(catalog);

            List<CatalogRefEntity> refs = catalogRefRepository.findByCatalogId(catalog.getId());
            dto.setCatalogRefs(refs.stream().map(CatalogRefDTO::fromEntity).collect(Collectors.toList()));

            packageInfoRepository.findByCatalogId(catalog.getId()).ifPresent(packageInfo -> dto.setPackageInfo(PackageInfoDTO.fromEntity(packageInfo)));

            helmChartRepository.findByCatalogId(catalog.getId())
                    .ifPresent(helmChart -> dto.setHelmChart(HelmChartDTO.fromEntity(helmChart)));

            // 평균 평점 추가
            Double averageRating = overallRatingRepository.findAverageRatingByCatalogId(catalog.getId());
            dto.setAverageRating(averageRating != null ? averageRating : 0.0);

            // 평가 횟수 추가
            Long ratingCount = overallRatingRepository.countByCatalogId(catalog.getId());
            dto.setRatingCount(ratingCount != null ? ratingCount : 0L);
            dto.setFormattedRatingCount(NumberFormatUtil.formatNumber(ratingCount != null ? ratingCount : 0L));

            // 다운로드 횟수 추가 (배포 성공한 것만)
            Long downloadCount = deploymentHistoryRepository.countByCatalogIdAndStatusAndActionType(catalog.getId(), "SUCCESS", ActionType.INSTALL);
            dto.setDownloadCount(downloadCount != null ? downloadCount : 0L);
            dto.setFormattedDownloadCount(NumberFormatUtil.formatNumber(downloadCount != null ? downloadCount : 0L));

            dtos.add(dto);
        }

        return dtos;
    }

    public List<SoftwareCatalogDTO> getCatalogsByName(String name) {
        List<SoftwareCatalog> catalogs = catalogRepository.findByNameContainingIgnoreCaseWithCatalogRefs(name);
        List<SoftwareCatalogDTO> dtos = new ArrayList<>();

        for (SoftwareCatalog catalog : catalogs) {
            SoftwareCatalogDTO dto = SoftwareCatalogDTO.fromEntity(catalog);

            List<CatalogRefEntity> refs = catalogRefRepository.findByCatalogId(catalog.getId());
            dto.setCatalogRefs(refs.stream().map(CatalogRefDTO::fromEntity).collect(Collectors.toList()));

            packageInfoRepository.findByCatalogId(catalog.getId()).ifPresent(packageInfo -> dto.setPackageInfo(PackageInfoDTO.fromEntity(packageInfo)));

            helmChartRepository.findByCatalogId(catalog.getId())
                    .ifPresent(helmChart -> dto.setHelmChart(HelmChartDTO.fromEntity(helmChart)));

            // 평균 평점 추가
            Double averageRating = overallRatingRepository.findAverageRatingByCatalogId(catalog.getId());
            dto.setAverageRating(averageRating != null ? averageRating : 0.0);

            // 평가 횟수 추가
            Long ratingCount = overallRatingRepository.countByCatalogId(catalog.getId());
            dto.setRatingCount(ratingCount != null ? ratingCount : 0L);
            dto.setFormattedRatingCount(NumberFormatUtil.formatNumber(ratingCount != null ? ratingCount : 0L));

            // 다운로드 횟수 추가 (배포 성공한 것만)
            Long downloadCount = deploymentHistoryRepository.countByCatalogIdAndStatusAndActionType(catalog.getId(), "SUCCESS", ActionType.INSTALL);
            dto.setDownloadCount(downloadCount != null ? downloadCount : 0L);
            dto.setFormattedDownloadCount(NumberFormatUtil.formatNumber(downloadCount != null ? downloadCount : 0L));

            dtos.add(dto);
        }

        return dtos;
    }

    @Transactional
    public SoftwareCatalogDTO updateCatalog(Long catalogId, SoftwareCatalogDTO catalogDTO, String username) {
        SoftwareCatalog catalog = catalogRepository.findById(catalogId)
                .orElseThrow(() -> new EntityNotFoundException("Catalog not found"));

        User user = null;
        if (StringUtils.isNotBlank(username)) {
            user = getUserByUsername(username);
        }

        updateCatalogFromDTO(catalog, catalogDTO, user);
        catalog = catalogRepository.save(catalog);

        // EntityManager flush를 통해 변경사항을 DB에 반영
        entityManager.flush();

        catalogRefRepository.deleteAllByCatalogId(catalogId);

        // 삭제 후 flush로 변경사항 반영
        entityManager.flush();

        if (catalogDTO.getCatalogRefs() != null && !catalogDTO.getCatalogRefs().isEmpty()) {
            for (CatalogRefDTO refDTO : catalogDTO.getCatalogRefs()) {
                CatalogRefEntity refEntity = refDTO.toEntity();
                refEntity.setCatalog(catalog);
                catalogRefRepository.save(refEntity);
            }
        }

        if("ARTIFACTHUB".equalsIgnoreCase(catalogDTO.getSourceType()) ) {
            if(catalogDTO.getIngressEnabled()){
                ingressConfigRepository.deleteAllByCatalogId(catalogId);
                entityManager.flush();
                saveIngressConfig(catalog, catalogDTO.getIngressUrl());
            }
            else {
                IngressConfig ingressConfig = ingressConfigRepository.findByCatalogId(catalogId);
                if(ingressConfig != null){
                    ingressConfigRepository.delete(ingressConfig);
                    entityManager.flush();
                }
            }
        }

        // 모든 변경사항을 반영한 후 최종 flush
        entityManager.flush();

        // 업데이트된 catalog를 다시 조회하여 반환 (lazy loading 문제 해결)
        return getCatalog(catalogId);
    }

    @Transactional
    public void deleteCatalog(Long catalogId) {
        SoftwareCatalog catalog = catalogRepository.findById(catalogId)
                .orElseThrow(() -> new EntityNotFoundException("Catalog not found"));

        log.info("Starting deletion process for catalog ID: {}", catalogId);

        try {
            // 1. HelmChart의 catalog_id를 NULL로 (Repository 사용)
            helmChartRepository.unlinkCatalogByCatalogId(catalogId);
            entityManager.flush();

            // 2. IngressConfig 삭제 (외래키 제약 조건 때문에 먼저 삭제)
            ingressConfigRepository.deleteAllByCatalogId(catalogId);
            entityManager.flush();

            // 3. PortMapping 삭제
            portMappingRepository.deleteAllByCatalogId(catalogId);
            entityManager.flush();

            // 4. CatalogRef 삭제
            catalogRefRepository.deleteAllByCatalogId(catalogId);
            entityManager.flush();

            // 5. 마지막으로 Catalog 삭제 (Repository bulk delete)
            catalogRepository.deleteByIdBulk(catalogId);
            entityManager.flush();
        } catch (Exception e) {
            log.error("Failed to delete catalog with ID: {}", catalogId, e);
            throw new RuntimeException("Failed to delete catalog: " + e.getMessage(), e);
        }
    }

    private void updateCatalogFromDTO(SoftwareCatalog catalog, SoftwareCatalogDTO dto, User user) {
        catalog.setName(dto.getName());
        catalog.setDescription(dto.getDescription());
        catalog.setCategory(dto.getCategory());
        catalog.setSourceType(dto.getSourceType());
        catalog.setLogoUrlLarge(dto.getLogoUrlLarge());
        catalog.setLogoUrlSmall(dto.getLogoUrlSmall());
        catalog.setSummary(dto.getSummary());
        catalog.setRegisteredBy(user);
        catalog.setUpdatedAt(LocalDateTime.now());
        catalog.setMinCpu(dto.getMinCpu());
        catalog.setRecommendedCpu(dto.getRecommendedCpu());
        catalog.setMinMemory(dto.getMinMemory());
        catalog.setRecommendedMemory(dto.getRecommendedMemory());
        catalog.setMinDisk(dto.getMinDisk());
        catalog.setRecommendedDisk(dto.getRecommendedDisk());
        catalog.setCpuThreshold(dto.getCpuThreshold());
        catalog.setMemoryThreshold(dto.getMemoryThreshold());
        catalog.setMinReplicas(dto.getMinReplicas());
        catalog.setMaxReplicas(dto.getMaxReplicas());
    }

    private User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("User not found with username: " + username));
    }

    private void savePackageInfo(SoftwareCatalog catalog, PackageInfoDTO packageInfoDTO) {
        PackageInfo packageInfo = packageInfoDTO.toEntity();
        packageInfo.setCatalog(catalog);
        packageInfoRepository.save(packageInfo);
    }

    private void saveHelmChart(SoftwareCatalog catalog, HelmChartDTO helmChartDTO) {
        HelmChart helmChart = helmChartDTO.toEntity();
        helmChart.setCatalog(catalog);
        helmChartRepository.save(helmChart);
    }

    public List<CombinedCatalogDTO> getAllCatalogsWithNexusInfo() {
        List<SoftwareCatalog> dbCatalogs = catalogRepository.findAll();
        List<CommonRepository.RepositoryDto> nexusRepositories = moduleRepositoryService.getRepositoryList("nexus");

        Map<String, CommonRepository.RepositoryDto> nexusRepoMap = nexusRepositories.stream()
                .collect(Collectors.toMap(CommonRepository.RepositoryDto::getName, Function.identity()));

        log.info("Nexus Repository Map:");
        nexusRepoMap.forEach((key, value) -> {
            log.info("Key: {}, Value: {}", key, value.toString());
        });

        return dbCatalogs.stream()
                .map(catalog -> {
                    CombinedCatalogDTO combinedDTO = new CombinedCatalogDTO();
                    combinedDTO.setSoftwareCatalogDTO(getSoftwareCatalogDTO(catalog));

                    CommonRepository.RepositoryDto nexusRepo = nexusRepoMap.get(catalog.getName());
                    if (nexusRepo != null) {
                        combinedDTO.setRepositoryDTO(nexusRepo);
                    }

                    return combinedDTO;
                })
                .collect(Collectors.toList());
    }

    public CombinedCatalogDTO getCatalogWithNexusInfo(Long catalogId) {
        SoftwareCatalog catalog = catalogRepository.findById(catalogId)
                .orElseThrow(() -> new EntityNotFoundException("Catalog not found"));

        SoftwareCatalogDTO catalogDTO = getSoftwareCatalogDTO(catalog);

        List<CommonRepository.RepositoryDto> nexusRepositories = moduleRepositoryService.getRepositoryList("nexus");
        CommonRepository.RepositoryDto nexusRepo = nexusRepositories.stream()
                .filter(repo -> repo.getName().equals(catalog.getName()))
                .findFirst()
                .orElse(null);

        CombinedCatalogDTO combinedDTO = new CombinedCatalogDTO();
        combinedDTO.setSoftwareCatalogDTO(catalogDTO);
        combinedDTO.setRepositoryDTO(nexusRepo);

        return combinedDTO;
    }

    private SoftwareCatalogDTO getSoftwareCatalogDTO(SoftwareCatalog catalog) {
        SoftwareCatalogDTO dto = SoftwareCatalogDTO.fromEntity(catalog);

        List<CatalogRefDTO> refDTOs = catalogRefRepository.findByCatalogId(catalog.getId()).stream()
                .map(CatalogRefDTO::fromEntity)
                .collect(Collectors.toList());
        dto.setCatalogRefs(refDTOs);

        packageInfoRepository.findByCatalogId(catalog.getId())
                .ifPresent(packageInfo -> dto.setPackageInfo(PackageInfoDTO.fromEntity(packageInfo)));

        helmChartRepository.findByCatalogId(catalog.getId())
                .ifPresent(helmChart -> dto.setHelmChart(HelmChartDTO.fromEntity(helmChart)));

        return dto;
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

            log.info("Searching for existing HelmChart with category: {}, name: {}, version: {}", category, chartName, chartVersion);

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

    private void saveIngressConfig(SoftwareCatalog catalog, String ingressUrl) {
        try {
            IngressConfig ingressConfig = IngressConfig.builder()
                    .path(ingressUrl)
                    .catalog(catalog)
                    .build();

            ingressConfigRepository.save(ingressConfig);
        } catch (Exception e) {
            log.error("Failed to save ingress config", e);
        }
    }
}