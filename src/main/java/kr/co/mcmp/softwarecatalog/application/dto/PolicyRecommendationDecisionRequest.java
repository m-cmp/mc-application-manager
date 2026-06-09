package kr.co.mcmp.softwarecatalog.application.dto;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class PolicyRecommendationDecisionRequest {

    private String status;
    private String decidedBy;
    private String decisionReason;
}
