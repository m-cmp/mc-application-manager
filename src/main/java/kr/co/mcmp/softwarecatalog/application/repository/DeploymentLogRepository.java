package kr.co.mcmp.softwarecatalog.application.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import kr.co.mcmp.softwarecatalog.application.model.DeploymentHistory;
import kr.co.mcmp.softwarecatalog.application.model.DeploymentLog;
import kr.co.mcmp.softwarecatalog.users.Entity.User;

@Repository
public interface DeploymentLogRepository extends JpaRepository<DeploymentLog, Long> {
    List<DeploymentLog> findByDeployment(DeploymentHistory deployment);
    List<DeploymentLog> findByDeploymentIdOrderByLoggedAtDesc(Long deploymentId);
    List<DeploymentLog> findByDeploymentIdAndDeployment_ExecutedByOrderByLoggedAtDesc(Long deploymentId, User user);
}