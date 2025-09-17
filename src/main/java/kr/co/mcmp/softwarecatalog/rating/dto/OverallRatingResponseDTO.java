package kr.co.mcmp.softwarecatalog.rating.dto;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "전체 평가 응답 DTO")
public class OverallRatingResponseDTO {

    @Schema(description = "평가 ID", example = "1")
    private Long ratingId;

    @Schema(description = "소프트웨어 카탈로그 ID", example = "1")
    private Long catalogId;

    @Schema(description = "전체 평점 (1-5)", example = "4")
    private Integer rating;

    @Schema(description = "평가 카테고리", example = "Performance")
    private String category;

    @Schema(description = "상세 코멘트", example = "성능이 우수하고 안정적입니다.")
    private String detailedComments;

    @Schema(description = "평가자 이름", example = "홍길동")
    private String name;

    @Schema(description = "평가자 이메일", example = "hong@example.com")
    private String email;

    @Schema(description = "추가 메타데이터", example = "{\"version\": \"1.0.0\", \"environment\": \"production\"}")
    private String metadata;

    @Schema(description = "평가 등록일시", example = "2024-01-15T10:30:00")
    private LocalDateTime createdAt;

    @Schema(description = "평가 수정일시", example = "2024-01-15T11:00:00")
    private LocalDateTime updatedAt;

    @Schema(description = "평가자 사용자명", example = "hong123")
    private String username;
}
