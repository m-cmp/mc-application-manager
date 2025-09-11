package kr.co.mcmp.softwarecatalog.application.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import kr.co.mcmp.softwarecatalog.SoftwareCatalog;
import kr.co.mcmp.softwarecatalog.application.constants.ActionType;
import kr.co.mcmp.softwarecatalog.application.model.ApplicationStatus;
import kr.co.mcmp.softwarecatalog.application.model.DeploymentHistory;
import kr.co.mcmp.softwarecatalog.users.Entity.User;


@Repository
public interface DeploymentHistoryRepository extends JpaRepository<DeploymentHistory, Long> {
    List<DeploymentHistory> findByCatalog(SoftwareCatalog catalog);
    List<DeploymentHistory> findByExecutedBy(User user);
    // List<DeploymentHistory> findByNamespaceAndMciIdAndVmIdAndStatusNot(String namespace, String mciId, String vmId, String status);
    // List<DeploymentHistory> findByNamespaceAndClusterNameAndStatusNot(String namespace, String clusterName, String status);
    List<DeploymentHistory> findByNamespaceAndMciIdAndVmIdAndActionTypeNotAndStatus(
        String namespace, 
        String mciId, 
        String vmId, 
        ActionType actionType, 
        String status
    );
    List<DeploymentHistory> findByNamespaceAndClusterNameAndActionTypeNotAndStatus(
        String namespace, 
        String clusterName, 
        ActionType actionType, 
        String status
    );
    
    List<DeploymentHistory> findByCatalogIdOrderByExecutedAtDesc(Long catalogId);
    List<DeploymentHistory> findByCatalogIdAndExecutedByOrderByExecutedAtDesc(Long catalogId, User user);

    @Modifying
    @Query("delete from DeploymentHistory d where d.catalog.id = :catalogId")
    void deleteAllByCatalogId(@Param("catalogId") Long catalogId);
    
    void deleteByCatalog(SoftwareCatalog catalog);
}
