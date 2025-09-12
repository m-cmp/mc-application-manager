package kr.co.mcmp.softwarecatalog.application.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import kr.co.mcmp.softwarecatalog.SoftwareCatalog;
import kr.co.mcmp.softwarecatalog.application.model.ApplicationStatus;
import kr.co.mcmp.softwarecatalog.users.Entity.User;

@Repository
public interface ApplicationStatusRepository extends JpaRepository<ApplicationStatus, Long> {
    Optional<ApplicationStatus> findByCatalog(SoftwareCatalog catalog);
    Optional<ApplicationStatus> findByCatalogId(Long catalogId);
    ApplicationStatus findTopByCatalogOrderByCheckedAtDesc(SoftwareCatalog catalog);
    Optional<ApplicationStatus> findByCatalogIdAndExecutedBy(Long catalogId, User user);
    Optional<ApplicationStatus> findTopByExecutedByOrderByCheckedAtDesc(User executedBy);
    
    @Query("SELECT DISTINCT a.namespace, a.mciId, a.vmId FROM ApplicationStatus a")
    List<Object[]> findDistinctVmGroups();

    List<ApplicationStatus> findByNamespaceAndMciIdAndVmId(String namespace, String mciId, String vmId);
    Optional<ApplicationStatus> findTopByCatalogIdOrderByCheckedAtDesc(Long catalogId);

    @Query("SELECT a FROM ApplicationStatus a " +
    "WHERE a.namespace = :namespace " +
    "AND a.clusterName = :clusterName " +
    "AND a.catalog.id = :catalogId " +
    "AND a.checkedAt = (SELECT MAX(a2.checkedAt) FROM ApplicationStatus a2 " +
    "                   WHERE a2.namespace = :namespace " +
    "                   AND a2.clusterName = :clusterName " +
    "                   AND a2.catalog.id = :catalogId)")
    Optional<ApplicationStatus> findLatestByNamespaceAndClusterNameAndCatalogId(
    @Param("namespace") String namespace,
    @Param("clusterName") String clusterName,
    @Param("catalogId") Long catalogId
    );
    
    @Modifying
    @Query("delete from ApplicationStatus a where a.catalog.id = :catalogId")
    void deleteAllByCatalogId(@Param("catalogId") Long catalogId);
    
    void deleteByCatalog(SoftwareCatalog catalog);
}
