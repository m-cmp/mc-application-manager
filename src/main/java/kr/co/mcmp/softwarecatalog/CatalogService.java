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


    @Transactional
    public SoftwareCatalogDTO createCatalog(SoftwareCatalogDTO catalogDTO, String username) {
        User user = null;
        if (StringUtils.isNotBlank(username)) {
            user = getUserByUsername(username);
        }
        
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

        catalogRefRepository.deleteAllByCatalogId(catalogId);
        packageInfoRepository.deleteByCatalogId(catalogId);
        helmChartRepository.deleteByCatalogId(catalogId);
        catalogRepository.delete(catalog);
    }

    private void updateCatalogFromDTO(SoftwareCatalog catalog, SoftwareCatalogDTO dto, User user) {
        catalog.setTitle(dto.getTitle());
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
                    
                    CommonRepository.RepositoryDto nexusRepo = nexusRepoMap.get(catalog.getTitle());
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
                .filter(repo -> repo.getName().equals(catalog.getTitle()))
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

}

