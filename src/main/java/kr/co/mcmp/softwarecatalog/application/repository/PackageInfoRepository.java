package kr.co.mcmp.softwarecatalog.application.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import kr.co.mcmp.softwarecatalog.SoftwareCatalog;
import kr.co.mcmp.softwarecatalog.application.model.PackageInfo;

@Repository
public interface PackageInfoRepository extends JpaRepository<PackageInfo, Long> {
    Optional<PackageInfo> findByCatalog(SoftwareCatalog catalog);
    Optional<PackageInfo> findByCatalogId(Long catalogId);
    void deleteByCatalog(SoftwareCatalog catalog);
    void deleteByCatalogId(Long catalogId);

    @Query("SELECT DISTINCT p.categories FROM PackageInfo p")
    List<String> findDistinctCategories();

    List<PackageInfo> findByCategories(String categories);
    List<PackageInfo> findByCategoriesAndCatalogIsNull(String categories);

    @Query("SELECT DISTINCT p.packageVersion, p.catalog.id FROM PackageInfo p WHERE p.packageName = :packageName")
    List<Object[]> findDistinctPackageVersionByPackageName(@Param("packageName") String packageName);

    /**
     * packageName, packageVersion, sourceType으로 PackageInfo 조회
     */
    List<PackageInfo> findByPackageNameAndPackageVersion(String packageName, String packageVersion);

    /**
     * catalog_id를 null로 설정 (카탈로그 삭제 시 외래키 제약조건 해결)
     */
    @Modifying
    @Query("UPDATE PackageInfo p SET p.catalog = null WHERE p.catalog.id = :catalogId")
    void unlinkCatalogByCatalogId(@Param("catalogId") Long catalogId);
}