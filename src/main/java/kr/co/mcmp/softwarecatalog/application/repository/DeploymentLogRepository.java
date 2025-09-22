package kr.co.mcmp.softwarecatalog.application.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import kr.co.mcmp.softwarecatalog.application.model.DeploymentHistory;
import kr.co.mcmp.softwarecatalog.application.model.DeploymentLog;
import kr.co.mcmp.softwarecatalog.users.Entity.User;

@Repository
public interface DeploymentLogRepository extends JpaRepository<DeploymentLog, Long> {
    List<DeploymentLog> findByDeployment(DeploymentHistory deployment);
    List<DeploymentLog> findByDeploymentIdOrderByLoggedAtDesc(Long deploymentId);
    List<DeploymentLog> findByDeploymentIdAndDeployment_ExecutedByOrderByLoggedAtDesc(Long deploymentId, User user);
    
    /**
     * 배포 이력 ID로 배포 로그 삭제
     */
    @Modifying
    @Query("DELETE FROM DeploymentLog dl WHERE dl.deployment.id = :deploymentId")
    void deleteByDeploymentId(@Param("deploymentId") Long deploymentId);
    
    /**
     * 배포 이력 목록으로 배포 로그 삭제
     */
    @Modifying
    @Query("DELETE FROM DeploymentLog dl WHERE dl.deployment IN :deployments")
    void deleteByDeployments(@Param("deployments") List<DeploymentHistory> deployments);
}