package kr.co.mcmp.softwarecatalog.application.service.impl;

import java.util.*;
import java.util.stream.Collectors;

import kr.co.mcmp.softwarecatalog.SoftwareCatalog;
import kr.co.mcmp.softwarecatalog.application.constants.PackageType;
import kr.co.mcmp.softwarecatalog.application.dto.PackageInfoDTO;
import kr.co.mcmp.softwarecatalog.application.model.HelmChart;
import kr.co.mcmp.softwarecatalog.application.model.PackageInfo;
import kr.co.mcmp.softwarecatalog.application.repository.HelmChartRepository;
import kr.co.mcmp.softwarecatalog.application.repository.PackageInfoRepository;
import kr.co.mcmp.softwarecatalog.catetory.dto.KeyValueDTO;
import kr.co.mcmp.softwarecatalog.catetory.dto.SoftwareCatalogRequestDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.co.mcmp.softwarecatalog.application.service.ApplicationService;
import kr.co.mcmp.softwarecatalog.application.service.NexusIntegrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 애플리케이션 등록 및 수정을 담당하는 서비스 구현체
 * 배포 및 운영 작업은 ApplicationOrchestrationService를 사용하세요
 */
@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class ApplicationServiceImpl implements ApplicationService {

    private final NexusIntegrationService nexusIntegrationService;

    private final PackageInfoRepository packageInfoRepository;

    private final HelmChartRepository helmChartRepository;

    // ===== 넥서스 연동 관련 메서드 (애플리케이션 배포/운영용) =====
    
    /**
     * 넥서스에서 애플리케이션을 조회합니다.
     * 
     * @param applicationName 애플리케이션 이름
     * @return 넥서스 애플리케이션 정보
     */
    @Override
    public Object getApplicationFromNexus(String applicationName) {
        log.info("Getting application from Nexus: {}", applicationName);
        
        return nexusIntegrationService.getFromNexus(applicationName);
    }
    
    /**
     * 넥서스에서 모든 애플리케이션을 조회합니다.
     * 
     * @return 넥서스 애플리케이션 목록
     */
    @Override
    public List<Object> getAllApplicationsFromNexus() {
        log.info("Getting all applications from Nexus");
        
        return (List<Object>) (List<?>) nexusIntegrationService.getAllFromNexus();
    }
    
    /**
     * 넥서스에서 이미지 태그 목록을 조회합니다.
     * 
     * @param imageName 이미지 이름
     * @return 태그 목록
     */
    @Override
    public List<String> getImageTagsFromNexus(String imageName) {
        log.info("Getting image tags from Nexus: {}", imageName);
        
        return nexusIntegrationService.getImageTagsFromNexus(imageName);
    }
    
    /**
     * 넥서스에서 이미지를 풀합니다.
     * 
     * @param imageName 이미지 이름
     * @param tag 이미지 태그
     * @return 풀 결과
     */
    @Override
    public Object pullImageFromNexus(String imageName, String tag) {
        log.info("Pulling image from Nexus: {}:{}", imageName, tag);
        
        return nexusIntegrationService.pullImageFromNexus(imageName, tag);
    }

    /**
     * DB에 Application의 Category를 조회합니다.
     *
     * @param dto
     */
    @Override
    public List<KeyValueDTO> getCategoriesFromDB(SoftwareCatalogRequestDTO.SearchCatalogListDTO dto) {
        log.info("Getting categories from DB: {}", dto.getTarget());
        List<String> result = new ArrayList<>();

        // VM
        if(PackageType.valueOf("DOCKER").equals(dto.getTarget()))
            result = packageInfoRepository.findDistinctCategories();

        // K8S
        else if(PackageType.valueOf("HELM").equals(dto.getTarget()))
            result = helmChartRepository.findDistinctCategories();

        // String -> KeyValueDTO 변환
        return result.stream()
                .filter(Objects::nonNull) // null 값 제거
                .map(category -> KeyValueDTO.builder()
                        .key(category)
                        .value(category) // 필요하다면 다른 값 매핑 가능
                        .build())
                .collect(Collectors.toList());
    }


    /**
     * DB에 Application를 조회합니다.
     *
     * @param dto
     */
    @Override
    public List<KeyValueDTO> getPackageInfoFromDB(SoftwareCatalogRequestDTO.SearchPackageListDTO dto) {
        log.info("Getting package info from DB: {} {}", dto.getTarget(), dto.getCategory());

        List<KeyValueDTO> result = new ArrayList<>();

        // VM
        if(PackageType.valueOf("DOCKER").equals(dto.getTarget())) {
            List<PackageInfo> packageInfoList = packageInfoRepository.findByCategories(dto.getCategory());
            result = packageInfoList.stream()
                    .filter(Objects::nonNull)
                    .map(packageInfo -> KeyValueDTO.builder()
                            .key(packageInfo.getPackageName())
                            .value(packageInfo.getPackageName())
                            .build())
                    .collect(Collectors.toList());
        }

        // K8S
        else if(PackageType.valueOf("HELM").equals(dto.getTarget())) {
            List<HelmChart> helmChartList = helmChartRepository.findByCategory(dto.getCategory());
            result = helmChartList.stream()
                    .map(helmChart -> KeyValueDTO.builder()
                            .key(helmChart.getChartName())
                            .value(helmChart.getChartName())
                            .build())  // 헬름차트 전용 변환 메서드
                    .collect(Collectors.toList());
        }

        return result;
    }

    /**
     * DB에 Application Version을 조회합니다.
     *
     * @param dto
     */
    @Override
    public List<KeyValueDTO> getPackageVersionFromDB(SoftwareCatalogRequestDTO.SearchPackageVersionListDTO dto) {
        log.info("Getting package categories from DB: {}", dto.getApplicationName());
        List<String> result = new ArrayList<>();
        if(PackageType.valueOf("DOCKER").equals(dto.getTarget())) {
            result = packageInfoRepository.findDistinctPackageVersionByPackageName(dto.getApplicationName());
        }
        else if(PackageType.valueOf("HELM").equals(dto.getTarget())) {
            result = helmChartRepository.findDistinctPackageVersionByChartName(dto.getApplicationName());
        }
        // String -> KeyValueDTO 변환
        return result.stream()
                .filter(Objects::nonNull) // null 값 제거
                .map(version -> KeyValueDTO.builder()
                        .key(version)
                        .value(version) // 필요하다면 다른 값 매핑 가능
                        .build())
                .collect(Collectors.toList());
    }
}
