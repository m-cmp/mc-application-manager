package kr.co.mcmp.softwarecatalog.application.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import kr.co.mcmp.softwarecatalog.SoftwareCatalog;
import kr.co.mcmp.softwarecatalog.application.model.PackageInfo;

@Repository
public interface PackageInfoRepository extends JpaRepository<PackageInfo, Long> {
    Optional<PackageInfo> findByCatalog(SoftwareCatalog catalog);
    Optional<PackageInfo> findByCatalogId(Long catalogId);
    void deleteByCatalog(SoftwareCatalog catalog);
    void deleteByCatalogId(Long catalogId);
}