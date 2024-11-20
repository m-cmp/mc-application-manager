package kr.co.mcmp.softwarecatalog.application.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import kr.co.mcmp.softwarecatalog.SoftwareCatalog;
import kr.co.mcmp.softwarecatalog.application.model.HelmChart;

@Repository
public interface HelmChartRepository extends JpaRepository<HelmChart, Long> {
    
    Optional<HelmChart> findByCatalog(SoftwareCatalog catalog);
    Optional<HelmChart> findByCatalogId(Long catalogId);
    void deleteByCatalog(SoftwareCatalog catalog);
    void deleteByCatalogId(Long catalogId);
}