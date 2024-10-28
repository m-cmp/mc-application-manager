package kr.co.mcmp.catalog.application.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import kr.co.mcmp.catalog.application.model.ApplicationStatus;
import kr.co.mcmp.catalog.application.model.K8sApplicationHistory;

public interface K8sApplicationHistoryRepository extends JpaRepository<K8sApplicationHistory, Integer> {

       List<K8sApplicationHistory> findByNamespaceAndClusterNameAndStatusNot(
        String namespace, String clusterName, ApplicationStatus status);

        @Query("SELECT k FROM K8sApplicationHistory k " +
           "WHERE k.namespace = :namespace " +
           "AND k.clusterName = :clusterName " +
           "AND k.catalog.id = :catalogId " +
           "AND k.status <> 'UNINSTALL'")
        Optional<K8sApplicationHistory> findHistoryByUninstall(
            @Param("namespace") String namespace,
            @Param("clusterName") String clusterName,
            @Param("catalogId") int catalogId);
}