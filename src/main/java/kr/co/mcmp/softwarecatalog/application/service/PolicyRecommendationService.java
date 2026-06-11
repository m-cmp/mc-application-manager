package kr.co.mcmp.softwarecatalog.application.service;

import java.util.List;

import kr.co.mcmp.softwarecatalog.application.dto.OperationProfileAnalysisDTO;
import kr.co.mcmp.softwarecatalog.application.dto.PolicyRecommendationDTO;

public interface PolicyRecommendationService {

    OperationProfileAnalysisDTO analyze(Long deploymentId, Integer days);

    List<OperationProfileAnalysisDTO> analyzeStandardPeriods(Long deploymentId);

    OperationProfileAnalysisDTO getLatestAnalysis(Long deploymentId);

    OperationProfileAnalysisDTO getAnalysis(Long deploymentId, Integer days);

    PolicyRecommendationDTO getLatestRecommendation(Long deploymentId);
}
