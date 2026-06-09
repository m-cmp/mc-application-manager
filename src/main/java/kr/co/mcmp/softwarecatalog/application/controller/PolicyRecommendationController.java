package kr.co.mcmp.softwarecatalog.application.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import kr.co.mcmp.response.ResponseWrapper;
import kr.co.mcmp.softwarecatalog.application.dto.OperationProfileAnalysisDTO;
import kr.co.mcmp.softwarecatalog.application.dto.PolicyRecommendationDTO;
import kr.co.mcmp.softwarecatalog.application.dto.PolicyRecommendationDecisionRequest;
import kr.co.mcmp.softwarecatalog.application.service.PolicyRecommendationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
@Slf4j
public class PolicyRecommendationController {

    private final PolicyRecommendationService policyRecommendationService;

    @Operation(summary = "Analyze operation profile", description = "Analyze archived operation data and create a policy recommendation.")
    @PostMapping("/{deploymentId}/operation-profile/analyze")
    public ResponseEntity<ResponseWrapper<OperationProfileAnalysisDTO>> analyze(
            @Parameter(description = "Deployment ID", required = true, example = "123") @PathVariable Long deploymentId,
            @Parameter(description = "Analysis period days", example = "14") @RequestParam(required = false, defaultValue = "14") Integer days) {
        OperationProfileAnalysisDTO result = policyRecommendationService.analyze(deploymentId, days);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }

    @Operation(summary = "Get latest operation profile analysis", description = "Retrieve the latest operation profile analysis for a deployment.")
    @GetMapping("/{deploymentId}/operation-profile")
    public ResponseEntity<ResponseWrapper<OperationProfileAnalysisDTO>> getLatestAnalysis(
            @Parameter(description = "Deployment ID", required = true, example = "123") @PathVariable Long deploymentId,
            @Parameter(description = "Analysis period days", example = "30") @RequestParam(required = false) Integer days) {
        OperationProfileAnalysisDTO result = days != null
                ? policyRecommendationService.getAnalysis(deploymentId, days)
                : policyRecommendationService.getLatestAnalysis(deploymentId);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }

    @Operation(summary = "Get latest policy recommendation", description = "Retrieve the latest policy recommendation for a deployment.")
    @GetMapping("/{deploymentId}/policy-recommendation")
    public ResponseEntity<ResponseWrapper<PolicyRecommendationDTO>> getLatestRecommendation(
            @Parameter(description = "Deployment ID", required = true, example = "123") @PathVariable Long deploymentId) {
        PolicyRecommendationDTO result = policyRecommendationService.getLatestRecommendation(deploymentId);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }

    @Operation(summary = "Save policy recommendation decision", description = "Save an operator decision for a recommendation.")
    @PutMapping("/policy-recommendations/{recommendationId}/decision")
    public ResponseEntity<ResponseWrapper<PolicyRecommendationDTO>> decide(
            @Parameter(description = "Policy recommendation ID", required = true, example = "456") @PathVariable Long recommendationId,
            @RequestBody PolicyRecommendationDecisionRequest request) {
        PolicyRecommendationDTO result = policyRecommendationService.decide(recommendationId, request);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }
}
