package kr.co.mcmp.softwarecatalog.application.repository;

import java.util.List;
import java.util.Optional;

import feign.Param;
import kr.co.mcmp.softwarecatalog.application.constants.PackageType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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

    @Query("SELECT DISTINCT p.packageVersion FROM PackageInfo p WHERE p.packageName = :packageName")
    List<String> findDistinctPackageVersionByPackageName(String packageName);
}