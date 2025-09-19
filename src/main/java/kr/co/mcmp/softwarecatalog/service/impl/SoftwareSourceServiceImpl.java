package kr.co.mcmp.softwarecatalog.service.impl;

// import kr.co.mcmp.softwarecatalog.model.SoftwareSourceMapping;
import kr.co.mcmp.softwarecatalog.application.model.PackageInfo;
import kr.co.mcmp.softwarecatalog.application.model.HelmChart;
// import kr.co.mcmp.softwarecatalog.repository.SoftwareSourceMappingRepository;
import kr.co.mcmp.softwarecatalog.application.repository.PackageInfoRepository;
import kr.co.mcmp.softwarecatalog.application.repository.HelmChartRepository;
import kr.co.mcmp.softwarecatalog.service.SoftwareSourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SoftwareSourceServiceImpl implements SoftwareSourceService {
    
    // private final SoftwareSourceMappingRepository mappingRepository;
    private final PackageInfoRepository packageInfoRepository;
    private final HelmChartRepository helmChartRepository;
    
    // @Override
    // public List<SoftwareSourceMapping> getSourcesByCatalogId(Long catalogId) {
    //     log.debug("카탈로그 ID {}의 모든 소스 조회", catalogId);
    //     return mappingRepository.findByCatalogIdOrderByPriorityAsc(catalogId);
    // }
    
    // @Override
    // public List<SoftwareSourceMapping> getSourcesByCatalogIdAndType(Long catalogId, String sourceType) {
    //     log.debug("카탈로그 ID {}의 {} 소스 조회", catalogId, sourceType);
    //     return mappingRepository.findByCatalogIdAndSourceTypeOrderByPriorityAsc(catalogId, sourceType);
    // }
    
    // @Override
    // public Optional<SoftwareSourceMapping> getPrimarySource(Long catalogId) {
    //     log.debug("카탈로그 ID {}의 기본 소스 조회", catalogId);
    //     return mappingRepository.findByCatalogIdAndIsPrimaryTrue(catalogId);
    // }
    
    @Override
    public Optional<PackageInfo> getDockerHubSource(Long catalogId) {
        log.debug("카탈로그 ID {}의 DockerHub 소스 조회", catalogId);
        // SoftwareSourceMapping을 사용하지 않고 직접 PackageInfo 조회
        return packageInfoRepository.findByCatalogId(catalogId);
    }
    
    @Override
    public Optional<HelmChart> getArtifactHubSource(Long catalogId) {
        log.debug("카탈로그 ID {}의 ArtifactHub 소스 조회", catalogId);
        // SoftwareSourceMapping을 사용하지 않고 직접 HelmChart 조회
        return helmChartRepository.findByCatalogId(catalogId);
    }
    
    // @Override
    // @Transactional
    // public SoftwareSourceMapping addSource(Long catalogId, String sourceType, Long sourceId, boolean isPrimary, int priority) {
    //     log.info("카탈로그 ID {}에 {} 소스 추가: sourceId={}, isPrimary={}, priority={}", 
    //             catalogId, sourceType, sourceId, isPrimary, priority);
        
    //     // 기본 소스로 설정하는 경우, 기존 기본 소스 해제
    //     if (isPrimary) {
    //         mappingRepository.clearPrimarySources(catalogId);
    //     }
        
    //     SoftwareSourceMapping mapping = SoftwareSourceMapping.builder()
    //             .catalogId(catalogId)
    //             .sourceType(sourceType)
    //             .sourceId(sourceId)
    //             .isPrimary(isPrimary)
    //             .priority(priority)
    //             .build();
        
    //     return mappingRepository.save(mapping);
    // }
    
    // @Override
    // @Transactional
    // public void setPrimarySource(Long catalogId, Long mappingId) {
    //     log.info("카탈로그 ID {}의 기본 소스를 매핑 ID {}로 변경", catalogId, mappingId);
        
    //     // 기존 기본 소스 해제
    //     mappingRepository.clearPrimarySources(catalogId);
        
    //     // 새로운 기본 소스 설정
    //     mappingRepository.setPrimarySource(mappingId);
    // }
    
    // @Override
    // @Transactional
    // public void removeSource(Long mappingId) {
    //     log.info("매핑 ID {} 소스 삭제", mappingId);
    //     mappingRepository.deleteById(mappingId);
    // }
}
