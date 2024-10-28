package kr.co.mcmp.catalog.application.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import kr.co.mcmp.catalog.application.model.ApplicationStatus;
import kr.co.mcmp.catalog.application.model.VmApplicationHistory;

@Repository
public interface VmApplicationHistoryRepository extends JpaRepository<VmApplicationHistory, Integer> {
    
      List<VmApplicationHistory> findByNamespaceAndMciNameAndVmNameAndStatusNot(
        String namespace, String mciName, String vmName, ApplicationStatus status);
      
      
    @Query("SELECT v FROM VmApplicationHistory v " +
           "WHERE v.namespace = :namespace " +
           "AND v.mciName = :mciName " +
           "AND v.vmName = :vmName " +
           "AND v.catalog.id = :catalogId " +
           "AND v.status <> 'UNINSTALL'")
    Optional<VmApplicationHistory> findHistoryByNotUninstall(
            @Param("namespace") String namespace,
            @Param("mciName") String mciName,
            @Param("vmName") String vmName,
            @Param("catalogId") int catalogId);
}
