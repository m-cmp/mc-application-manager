package kr.co.mcmp.softwarecatalog.application.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import kr.co.mcmp.softwarecatalog.application.model.InfraSpecSnapshot;

@Repository
public interface InfraSpecSnapshotRepository extends JpaRepository<InfraSpecSnapshot, Long> {

    Optional<InfraSpecSnapshot> findByDeploymentId(Long deploymentId);

    boolean existsByDeploymentId(Long deploymentId);
}
