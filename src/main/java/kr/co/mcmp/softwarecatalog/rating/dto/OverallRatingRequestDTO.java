package kr.co.mcmp.softwarecatalog.rating.dto;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "전체 평가 등록 요청 DTO")
public class OverallRatingRequestDTO {

    @NotNull(message = "카탈로그 ID는 필수입니다.")
    @Schema(description = "소프트웨어 카탈로그 ID", example = "1")
    private Long catalogId;

    @NotNull(message = "평점은 필수입니다.")
    @Min(value = 1, message = "평점은 1점 이상이어야 합니다.")
    @Max(value = 5, message = "평점은 5점 이하여야 합니다.")
    @Schema(description = "전체 평점 (1-5)", example = "4")
    private Integer rating;

    @NotBlank(message = "카테고리는 필수입니다.")
    @Schema(description = "평가 카테고리", example = "Performance")
    private String category;

    @Size(max = 1000, message = "상세 코멘트는 1000자를 초과할 수 없습니다.")
    @Schema(description = "상세 코멘트", example = "성능이 우수하고 안정적입니다.")
    private String detailedComments;

    @NotBlank(message = "이름은 필수입니다.")
    @Size(max = 100, message = "이름은 100자를 초과할 수 없습니다.")
    @Schema(description = "평가자 이름", example = "홍길동")
    private String name;

    @NotBlank(message = "이메일은 필수입니다.")
    @Size(max = 255, message = "이메일은 255자를 초과할 수 없습니다.")
    @Schema(description = "평가자 이메일", example = "hong@example.com")
    private String email;

    @Schema(description = "추가 메타데이터", example = "{\"version\": \"1.0.0\", \"environment\": \"production\"}")
    private String metadata;
}
