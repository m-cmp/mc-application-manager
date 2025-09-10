package kr.co.mcmp.softwarecatalog;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import kr.co.mcmp.softwarecatalog.application.dto.DockerHubImageRegistrationRequest;
import kr.co.mcmp.softwarecatalog.application.dto.HelmChartDTO;
import kr.co.mcmp.softwarecatalog.application.dto.PackageInfoDTO;
import kr.co.mcmp.softwarecatalog.application.model.HelmChart;
import kr.co.mcmp.softwarecatalog.application.model.PackageInfo;
import kr.co.mcmp.softwarecatalog.application.repository.HelmChartRepository;
import kr.co.mcmp.softwarecatalog.application.repository.PackageInfoRepository;
import kr.co.mcmp.softwarecatalog.application.service.DockerHubIntegrationService;
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
//        packageInfoRepository.deleteByCatalogId(catalogId);
//        helmChartRepository.deleteByCatalogId(catalogId);
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

    
    /**
     * Docker Hub에서 이미지를 검색합니다.
     * 
     * @param query 검색 쿼리
     * @param page 페이지 번호
     * @param pageSize 페이지 크기
     * @return 검색 결과
     */
    public Map<String, Object> searchDockerHubImages(String query, int page, int pageSize) {
        return dockerHubIntegrationService.searchImages(query, page, pageSize);
    }
    
    /**
     * Docker Hub에서 이미지 상세 정보를 조회합니다.
     * 
     * @param imageName 이미지 이름
     * @param tag 이미지 태그
     * @return 이미지 상세 정보
     */
    public Map<String, Object> getDockerHubImageDetails(String imageName, String tag) {
        return dockerHubIntegrationService.getImageDetails(imageName, tag);
    }
    
    /**
     * Docker Hub 이미지를 package_info에만 등록합니다.
     * 
     * @param request Docker Hub 이미지 등록 요청
     * @param username 사용자명
     * @return 등록 결과
     */
    public Map<String, Object> registerDockerHubImage(DockerHubImageRegistrationRequest request, String username) {
        log.info("Registering Docker Hub image to package_info: {}:{}", request.getImageName(), request.getTag());
        
        try {
            // 1. Docker Hub에서 이미지 정보 조회
            Map<String, Object> imageDetails = getDockerHubImageDetails(request.getImageName(), request.getTag());
            if (!(Boolean) imageDetails.get("success")) {
                return imageDetails;
            }
            
            // 2. Docker Hub 이미지 상세 정보를 package_info에만 저장
            PackageInfo savedPackageInfo = saveDockerHubImageToPackageInfo(request, imageDetails);
            
            // 3. Docker Hub 이미지를 Nexus에 푸시
            Map<String, Object> pushResult = dockerHubIntegrationService.pushImageToNexus(request.getImageName(), request.getTag());
            
            Map<String, Object> result = new java.util.HashMap<>();
            result.put("success", true);
            result.put("message", "Docker Hub image registered to package_info successfully");
            result.put("packageInfo", savedPackageInfo);
            result.put("pushResult", pushResult);
            
            return result;
            
        } catch (Exception e) {
            log.error("Failed to register Docker Hub image: {}:{}", request.getImageName(), request.getTag(), e);
            
            Map<String, Object> result = new java.util.HashMap<>();
            result.put("success", false);
            result.put("message", "Failed to register Docker Hub image: " + e.getMessage());
            return result;
        }
    }
    
    
    /**
     * Docker Hub 이미지 상세 정보를 package_info 테이블에 저장합니다.
     */
    private PackageInfo saveDockerHubImageToPackageInfo(DockerHubImageRegistrationRequest request, Map<String, Object> imageDetails) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> imageData = (Map<String, Object>) imageDetails.get("data");
            
            if (imageData == null) {
                throw new RuntimeException("No image data found in Docker Hub response");
            }
            
            // PackageInfo 엔티티 생성 및 기본 정보 설정
            PackageInfo packageInfo = createBasePackageInfo(request);
            
            // Docker Hub API 응답에서 데이터 추출 및 설정
            extractAndSetDockerHubData(packageInfo, imageData);
            
            // package_info 테이블에 저장
            PackageInfo savedPackageInfo = packageInfoRepository.save(packageInfo);
            
            log.info("Docker Hub image details saved to package_info: {}:{} (packageInfoId: {})", 
                    request.getImageName(), request.getTag(), savedPackageInfo.getId());
            
            return savedPackageInfo;
            
        } catch (Exception e) {
            log.error("Failed to save Docker Hub image details to package_info: {}:{}", 
                    request.getImageName(), request.getTag(), e);
            throw e;
        }
    }
    
    /**
     * 기본 PackageInfo 엔티티를 생성합니다.
     */
    private PackageInfo createBasePackageInfo(DockerHubImageRegistrationRequest request) {
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.setCatalog(null); // software_catalog에 저장하지 않음
        packageInfo.setPackageType(kr.co.mcmp.softwarecatalog.application.constants.PackageType.DOCKER);
        packageInfo.setPackageName(request.getImageName());
        packageInfo.setPackageVersion(request.getTag());
        packageInfo.setRepositoryUrl("https://hub.docker.com/r/" + request.getImageName());
        return packageInfo;
    }
    
    /**
     * Docker Hub API 응답에서 데이터를 추출하여 PackageInfo에 설정합니다.
     */
    private void extractAndSetDockerHubData(PackageInfo packageInfo, Map<String, Object> imageData) {
        // 기본 정보 설정
        setStringValue(packageInfo::setDockerImageId, imageData.get("id"));
        setStringValue(packageInfo::setDockerPublisher, imageData.get("user"));
        setStringValue(packageInfo::setDockerShortDescription, imageData.get("description"));
        setStringValue(packageInfo::setDockerSource, imageData.get("affiliation"));
        
        // 숫자 정보 설정
        setIntegerValue(packageInfo::setStarCount, imageData.get("star_count"));
        setLongValue(packageInfo::setPullCount, imageData.get("pull_count"));
        
        // 불린 정보 설정
        setBooleanValue(packageInfo::setIsOfficial, isOfficialImage(imageData));
        setBooleanValue(packageInfo::setIsAutomated, imageData.get("is_automated"));
        setBooleanValue(packageInfo::setIsArchived, imageData.get("is_archived"));
        
        // 날짜 정보 설정
        setDateTimeValue(packageInfo::setDockerUpdatedAt, imageData.get("last_updated"));
        setDateTimeValue(packageInfo::setDockerCreatedAt, imageData.get("date_registered"));
        
        // 운영체제 및 아키텍처 정보 설정
        setOperatingSystems(packageInfo, imageData);
        setArchitectures(packageInfo, imageData);
        setCategories(packageInfo, imageData);
        
        // 추가 정보 로깅
        logAdditionalDockerHubInfo(imageData);
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
     * 정수 값을 안전하게 설정합니다.
     */
    private void setIntegerValue(java.util.function.Consumer<Integer> setter, Object value) {
        if (value instanceof Number) {
            setter.accept(((Number) value).intValue());
        }
    }
    
    /**
     * Long 값을 안전하게 설정합니다.
     */
    private void setLongValue(java.util.function.Consumer<String> setter, Object value) {
        if (value instanceof Number) {
            setter.accept(String.valueOf(((Number) value).longValue()));
        }
    }
    
    /**
     * 불린 값을 안전하게 설정합니다.
     */
    private void setBooleanValue(java.util.function.Consumer<Boolean> setter, Object value) {
        if (value instanceof Boolean) {
            setter.accept((Boolean) value);
        }
    }
    
    /**
     * 날짜/시간 값을 안전하게 설정합니다.
     */
    private void setDateTimeValue(java.util.function.Consumer<java.time.LocalDateTime> setter, Object value) {
        if (value != null) {
            try {
                String dateStr = value.toString().replace("Z", "");
                setter.accept(java.time.LocalDateTime.parse(dateStr));
            } catch (Exception e) {
                log.warn("Failed to parse date: {}", value);
            }
        }
    }
    
    /**
     * 공식 이미지인지 확인합니다.
     */
    private Boolean isOfficialImage(Map<String, Object> imageData) {
        Object namespace = imageData.get("namespace");
        return namespace != null && "library".equals(namespace.toString());
    }
    
    /**
     * 운영체제 정보를 설정합니다.
     */
    private void setOperatingSystems(PackageInfo packageInfo, Map<String, Object> imageData) {
        @SuppressWarnings("unchecked")
        List<String> osList = (List<String>) imageData.get("operating_systems");
        if (osList != null && !osList.isEmpty()) {
            packageInfo.setOperatingSystems(String.join(",", osList));
        } else {
            packageInfo.setOperatingSystems("linux"); // 기본값
        }
    }
    
    /**
     * 아키텍처 정보를 설정합니다.
     */
    private void setArchitectures(PackageInfo packageInfo, Map<String, Object> imageData) {
        @SuppressWarnings("unchecked")
        List<String> archList = (List<String>) imageData.get("architectures");
        if (archList != null && !archList.isEmpty()) {
            packageInfo.setArchitectures(String.join(",", archList));
        } else {
            // full_description에서 아키텍처 정보 추출 시도
            String fullDesc = (String) imageData.get("full_description");
            if (fullDesc != null && fullDesc.contains("Supported architectures")) {
                List<String> extractedArchs = extractArchitecturesFromDescription(fullDesc);
                if (!extractedArchs.isEmpty()) {
                    packageInfo.setArchitectures(String.join(",", extractedArchs));
                }
            }
            // 기본값 설정
            if (packageInfo.getArchitectures() == null) {
                packageInfo.setArchitectures("amd64");
            }
        }
    }
    
    /**
     * full_description에서 아키텍처 정보를 추출합니다.
     */
    private List<String> extractArchitecturesFromDescription(String fullDesc) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\[`([^`]+)`\\]");
        java.util.regex.Matcher matcher = pattern.matcher(fullDesc);
        List<String> archList = new java.util.ArrayList<>();
        
        while (matcher.find()) {
            String arch = matcher.group(1);
            if (!arch.contains("nginx") && !arch.contains("bookworm") && !arch.contains("alpine") && 
                !arch.contains("perl") && !arch.contains("otel") && !arch.contains("slim")) {
                archList.add(arch);
            }
        }
        return archList;
    }
    
    /**
     * 카테고리 정보를 설정합니다.
     */
    private void setCategories(PackageInfo packageInfo, Map<String, Object> imageData) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> categoryList = (List<Map<String, Object>>) imageData.get("categories");
        if (categoryList != null && !categoryList.isEmpty()) {
            List<String> categoryNames = categoryList.stream()
                    .map(category -> (String) category.get("name"))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            if (!categoryNames.isEmpty()) {
                packageInfo.setCategories(String.join(",", categoryNames));
            }
        }
    }
    
    /**
     * 추가 Docker Hub 정보를 로깅합니다.
     */
    private void logAdditionalDockerHubInfo(Map<String, Object> imageData) {
        // 핵심 정보만 로깅
        log.debug("Repository type: {}, Status: {}, Is private: {}", 
                imageData.get("repository_type"), 
                imageData.get("status"), 
                imageData.get("is_private"));
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


}

