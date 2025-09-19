package kr.co.mcmp.softwarecatalog.repository;

import kr.co.mcmp.softwarecatalog.model.SoftwareSourceMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SoftwareSourceMappingRepository extends JpaRepository<SoftwareSourceMapping, Long> {
    
    /**
     * 카탈로그 ID로 모든 소스 조회
     */
    List<SoftwareSourceMapping> findByCatalogIdOrderByPriorityAsc(Long catalogId);
    
    /**
     * 카탈로그 ID와 소스 타입으로 조회
     */
    List<SoftwareSourceMapping> findByCatalogIdAndSourceTypeOrderByPriorityAsc(Long catalogId, String sourceType);
    
    /**
     * 카탈로그 ID의 기본 소스 조회
     */
    Optional<SoftwareSourceMapping> findByCatalogIdAndIsPrimaryTrue(Long catalogId);
    
    /**
     * 카탈로그 ID의 특정 소스 타입 기본 소스 조회
     */
    Optional<SoftwareSourceMapping> findByCatalogIdAndSourceTypeAndIsPrimaryTrue(Long catalogId, String sourceType);
    
    /**
     * 카탈로그 ID의 소스 개수 조회
     */
    long countByCatalogId(Long catalogId);
    
    /**
     * 카탈로그 ID의 특정 소스 타입 개수 조회
     */
    long countByCatalogIdAndSourceType(Long catalogId, String sourceType);
    
    /**
     * 기본 소스가 아닌 소스들을 기본 소스로 변경
     */
    @Query("UPDATE SoftwareSourceMapping ssm SET ssm.isPrimary = false WHERE ssm.catalogId = :catalogId")
    void clearPrimarySources(@Param("catalogId") Long catalogId);
    
    /**
     * 특정 매핑을 기본 소스로 설정
     */
    @Query("UPDATE SoftwareSourceMapping ssm SET ssm.isPrimary = true WHERE ssm.id = :mappingId")
    void setPrimarySource(@Param("mappingId") Long mappingId);
}
