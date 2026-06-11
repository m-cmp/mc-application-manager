package kr.co.mcmp.softwarecatalog.application.dto;

import java.time.LocalDateTime;

import kr.co.mcmp.softwarecatalog.application.model.PolicyRecommendation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PolicyRecommendationDTO {

    private Long id;
    private Long deploymentId;
    private Long analysisId;
    private String selectedResourceType;
    private String recommendedResourceType;
    private Boolean mismatch;
    private String actions;
    private Double confidence;
    private String message;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static PolicyRecommendationDTO from(PolicyRecommendation recommendation) {
        if (recommendation == null) {
            return null;
        }
        return PolicyRecommendationDTO.builder()
                .id(recommendation.getId())
                .deploymentId(recommendation.getDeploymentId())
                .analysisId(recommendation.getAnalysisId())
                .selectedResourceType(recommendation.getSelectedResourceType())
                .recommendedResourceType(recommendation.getRecommendedResourceType())
                .mismatch(recommendation.getMismatch())
                .actions(recommendation.getActions())
                .confidence(recommendation.getConfidence())
                .message(recommendation.getMessage())
                .createdAt(recommendation.getCreatedAt())
                .updatedAt(recommendation.getUpdatedAt())
                .build();
    }
}
