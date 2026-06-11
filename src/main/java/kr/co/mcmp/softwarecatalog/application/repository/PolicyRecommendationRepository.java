package kr.co.mcmp.softwarecatalog.application.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import kr.co.mcmp.softwarecatalog.application.model.PolicyRecommendation;

@Repository
public interface PolicyRecommendationRepository extends JpaRepository<PolicyRecommendation, Long> {

    Optional<PolicyRecommendation> findTopByDeploymentIdOrderByCreatedAtDescIdDesc(Long deploymentId);

    List<PolicyRecommendation> findByDeploymentIdOrderByCreatedAtDescIdDesc(Long deploymentId);
}
