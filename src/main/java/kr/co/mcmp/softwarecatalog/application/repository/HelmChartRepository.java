package kr.co.mcmp.softwarecatalog.application.repository;

import java.util.List;
import java.util.Optional;

import kr.co.mcmp.softwarecatalog.application.constants.PackageType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import kr.co.mcmp.softwarecatalog.SoftwareCatalog;
import kr.co.mcmp.softwarecatalog.application.model.HelmChart;

@Repository
public interface HelmChartRepository extends JpaRepository<HelmChart, Long> {
    
    Optional<HelmChart> findByCatalog(SoftwareCatalog catalog);
    Optional<HelmChart> findByCatalogId(Long catalogId);
    void deleteByCatalog(SoftwareCatalog catalog);
    void deleteByCatalogId(Long catalogId);


    @Query("SELECT DISTINCT hc.category FROM HelmChart hc")
    List<String> findDistinctCategories();


    List<HelmChart> findByCategory(String category);
    List<HelmChart> findByCategoryAndCatalogIsNull(String category);

    @Query("SELECT DISTINCT hc.chartVersion, hc.catalog.id FROM HelmChart hc WHERE hc.chartName = :chartName")
    List<Object[]> findDistinctPackageVersionByChartName(@Param("chartName") String chartName);

    @Modifying
    @Query("UPDATE HelmChart h SET h.catalog = null WHERE h.catalog.id = :catalogId")
    void unlinkCatalogByCatalogId(@Param("catalogId") Long catalogId);
}
