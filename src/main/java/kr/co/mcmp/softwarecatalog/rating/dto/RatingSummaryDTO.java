package kr.co.mcmp.softwarecatalog.rating.dto;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "평점 요약 DTO")
public class RatingSummaryDTO {

    @Schema(description = "소프트웨어 카탈로그 ID", example = "1")
    private Long catalogId;

    @Schema(description = "평균 평점", example = "4.2")
    private Double averageRating;

    @Schema(description = "총 평가 수", example = "150")
    private Long totalRatings;

    @Schema(description = "평점별 분포", example = "{\"1\": 5, \"2\": 10, \"3\": 25, \"4\": 60, \"5\": 50}")
    private Map<Integer, Long> ratingDistribution;

    @Schema(description = "카테고리별 평균 평점", example = "{\"Performance\": 4.5, \"Usability\": 3.8, \"Security\": 4.0}")
    private Map<String, Double> categoryAverageRatings;

    @Schema(description = "최근 30일 평가 수", example = "25")
    private Long recentRatings;

    @Schema(description = "평가 트렌드 (증가/감소/유지)", example = "INCREASING")
    private String trend;

    @Schema(description = "신뢰도 점수 (0-100)", example = "85")
    private Integer confidenceScore;
}
