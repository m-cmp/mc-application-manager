package kr.co.mcmp.softwarecatalog.service;

import kr.co.mcmp.softwarecatalog.model.SoftwareSourceMapping;
import kr.co.mcmp.softwarecatalog.application.model.PackageInfo;
import kr.co.mcmp.softwarecatalog.application.model.HelmChart;

import java.util.List;
import java.util.Optional;

public interface SoftwareSourceService {
    
    /**
     * 소프트웨어의 모든 소스 조회
     */
    List<SoftwareSourceMapping> getSourcesByCatalogId(Long catalogId);
    
    /**
     * 특정 타입의 소스 조회
     */
    List<SoftwareSourceMapping> getSourcesByCatalogIdAndType(Long catalogId, String sourceType);
    
    /**
     * 기본 소스 조회
     */
    Optional<SoftwareSourceMapping> getPrimarySource(Long catalogId);
    
    /**
     * DockerHub 소스 조회
     */
    Optional<PackageInfo> getDockerHubSource(Long catalogId);
    
    /**
     * ArtifactHub 소스 조회
     */
    Optional<HelmChart> getArtifactHubSource(Long catalogId);
    
    /**
     * 소스 추가
     */
    SoftwareSourceMapping addSource(Long catalogId, String sourceType, Long sourceId, boolean isPrimary, int priority);
    
    /**
     * 기본 소스 변경
     */
    void setPrimarySource(Long catalogId, Long mappingId);
    
    /**
     * 소스 삭제
     */
    void removeSource(Long mappingId);
}
