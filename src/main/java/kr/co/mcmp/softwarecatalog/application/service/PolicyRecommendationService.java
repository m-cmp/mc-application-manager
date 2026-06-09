package kr.co.mcmp.softwarecatalog.application.service;

import kr.co.mcmp.softwarecatalog.application.dto.OperationProfileAnalysisDTO;
import kr.co.mcmp.softwarecatalog.application.dto.PolicyRecommendationDTO;
import kr.co.mcmp.softwarecatalog.application.dto.PolicyRecommendationDecisionRequest;

public interface PolicyRecommendationService {

    OperationProfileAnalysisDTO analyze(Long deploymentId, Integer days);

    OperationProfileAnalysisDTO getLatestAnalysis(Long deploymentId);

    OperationProfileAnalysisDTO getAnalysis(Long deploymentId, Integer days);

    PolicyRecommendationDTO getLatestRecommendation(Long deploymentId);

    PolicyRecommendationDTO decide(Long recommendationId, PolicyRecommendationDecisionRequest request);
}
