package kr.co.mcmp.softwarecatalog.application.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import kr.co.mcmp.softwarecatalog.application.model.OperationProfileAnalysis;

@Repository
public interface OperationProfileAnalysisRepository extends JpaRepository<OperationProfileAnalysis, Long> {

    Optional<OperationProfileAnalysis> findTopByDeploymentIdOrderByCreatedAtDesc(Long deploymentId);

    Optional<OperationProfileAnalysis> findTopByDeploymentIdAndAnalysisStartDateAndAnalysisEndDateOrderByCreatedAtDesc(
            Long deploymentId, LocalDate analysisStartDate, LocalDate analysisEndDate);

    boolean existsByDeploymentIdAndAnalysisStartDateAndAnalysisEndDate(
            Long deploymentId, LocalDate analysisStartDate, LocalDate analysisEndDate);

    List<OperationProfileAnalysis> findByDeploymentIdOrderByCreatedAtDesc(Long deploymentId);
}
