package kr.co.mcmp.softwarecatalog;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.persistence.EntityNotFoundException;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.co.mcmp.dto.oss.repository.CommonRepository;
import kr.co.mcmp.service.oss.repository.CommonModuleRepositoryService;
import kr.co.mcmp.softwarecatalog.Ref.CatalogRefDTO;
import kr.co.mcmp.softwarecatalog.Ref.CatalogRefEntity;
import kr.co.mcmp.softwarecatalog.Ref.CatalogRefRepository;
import kr.co.mcmp.softwarecatalog.application.dto.HelmChartDTO;
import kr.co.mcmp.softwarecatalog.application.dto.PackageInfoDTO;
import kr.co.mcmp.softwarecatalog.application.model.HelmChart;
import kr.co.mcmp.softwarecatalog.application.model.PackageInfo;
import kr.co.mcmp.softwarecatalog.application.repository.HelmChartRepository;
import kr.co.mcmp.softwarecatalog.application.repository.PackageInfoRepository;
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
        SoftwareCatalogDTO result = SoftwareCatalogDTO.fromEntity(catalog);
//        SoftwareCatalogDTO result = createCatalogInternal(catalogDTO, user);
//
//        // 2. 넥서스에 애플리케이션 등록
//        try {
//            nexusIntegrationService.registerToNexus(result);
//            log.info("Application registered to Nexus successfully: {}", result.getName());
//        } catch (Exception e) {
//            log.warn("Failed to register application to Nexus: {}", result.getName(), e);
//            // 넥서스 등록 실패해도 DB 등록은 유지
//        }
//
//        log.info("Software catalog registered successfully with ID: {}", result.getId());
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

        catalogRefRepository.deleteAllByCatalogId(catalogId);
        if (catalogDTO.getCatalogRefs() != null && !catalogDTO.getCatalogRefs().isEmpty()) {
            for (CatalogRefDTO refDTO : catalogDTO.getCatalogRefs()) {
                CatalogRefEntity refEntity = refDTO.toEntity();
                refEntity.setCatalog(catalog);
                catalogRefRepository.save(refEntity);
            }
        }

        if (catalogDTO.getPackageInfo() != null) {
            updatePackageInfo(catalog, catalogDTO.getPackageInfo());
        } else if (catalogDTO.getHelmChart() != null) {
            updateHelmChart(catalog, catalogDTO.getHelmChart());
        }

        return SoftwareCatalogDTO.fromEntity(catalog);
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
        packageInfoRepository.deleteByCatalogId(catalogId);
        helmChartRepository.deleteByCatalogId(catalogId);
        catalogRepository.delete(catalog);
        
        log.info("Software catalog deleted successfully with ID: {}", catalogId);
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

    private void updatePackageInfo(SoftwareCatalog catalog, PackageInfoDTO packageInfoDTO) {
        PackageInfo packageInfo = packageInfoRepository.findByCatalogId(catalog.getId())
                .orElse(new PackageInfo());
        packageInfo.updateFromDTO(packageInfoDTO);
        packageInfo.setCatalog(catalog);
        packageInfoRepository.save(packageInfo);
    }

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
    
    // ===== 넥서스 연동 관련 메서드 (카탈로그 관리용) =====
    
    /**
     * 넥서스에 이미지를 푸시하고 카탈로그에 등록합니다.
     * 
     * @param catalog 소프트웨어 카탈로그 정보
     * @param username 사용자명
     * @return 등록 결과
     */
    @Transactional
    public Map<String, Object> pushImageAndRegisterCatalog(SoftwareCatalogDTO catalog, String username) {
        log.info("Pushing image and registering catalog: {}", catalog.getName());
        
        // 1. 이미지 푸시 및 넥서스 등록
        Map<String, Object> nexusResult = nexusIntegrationService.pushImageAndRegisterCatalog(catalog);
        
        // 2. DB에 카탈로그 등록
        User user = null;
        if (StringUtils.isNotBlank(username)) {
            user = getUserByUsername(username);
        }
        
        SoftwareCatalogDTO result = createCatalogInternal(catalog, user);
        
        // 3. 결과 통합
        Map<String, Object> finalResult = new java.util.HashMap<>();
        finalResult.put("success", true);
        finalResult.put("message", "Image pushed and catalog registered successfully");
        finalResult.put("catalog", result);
        finalResult.put("nexusResult", nexusResult);
        
        return finalResult;
    }
    
    /**
     * 넥서스에 이미지가 존재하는지 확인합니다.
     * 
     * @param imageName 이미지 이름
     * @param tag 이미지 태그
     * @return 존재 여부
     */
    public boolean checkImageExistsInNexus(String imageName, String tag) {
        return nexusIntegrationService.checkImageExistsInNexus(imageName, tag);
    }
    
    /**
     * 넥서스에 이미지를 푸시합니다.
     * 
     * @param imageName 이미지 이름
     * @param tag 이미지 태그
     * @param imageData 이미지 데이터
     * @return 푸시 결과
     */
    public Map<String, Object> pushImageToNexus(String imageName, String tag, byte[] imageData) {
        return nexusIntegrationService.pushImageToNexus(imageName, tag, imageData);
    }
    
    /**
     * 넥서스에서 이미지를 풀합니다.
     * 
     * @param imageName 이미지 이름
     * @param tag 이미지 태그
     * @return 풀 결과
     */
    public Map<String, Object> pullImageFromNexus(String imageName, String tag) {
        return nexusIntegrationService.pullImageFromNexus(imageName, tag);
    }
    
    /**
     * 카탈로그 ID로 이미지를 풀합니다.
     * 
     * @param catalogId 카탈로그 ID
     * @return 풀 결과
     */
    public Map<String, Object> pullImageByCatalogId(Long catalogId) {
        log.info("Pulling image by catalog ID: {}", catalogId);
        
        // 카탈로그 정보 조회
        SoftwareCatalogDTO catalog = getCatalog(catalogId);
        
        if (catalog.getPackageInfo() == null) {
            throw new IllegalArgumentException("PackageInfo is not available for catalog ID: " + catalogId);
        }
        
        String imageName = catalog.getPackageInfo().getPackageName();
        String tag = catalog.getPackageInfo().getPackageVersion();
        
        log.info("Pulling image for catalog '{}': {}:{}", catalog.getName(), imageName, tag);
        
        return nexusIntegrationService.pullImageFromNexus(imageName, tag);
    }
    
    /**
     * 넥서스에서 이미지 태그 목록을 조회합니다.
     * 
     * @param imageName 이미지 이름
     * @return 태그 목록
     */
    public List<String> getImageTagsFromNexus(String imageName) {
        return nexusIntegrationService.getImageTagsFromNexus(imageName);
    }

}

