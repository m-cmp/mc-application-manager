package kr.co.mcmp.softwarecatalog.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.co.mcmp.softwarecatalog.application.constants.ActionType;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import javax.validation.constraints.NotNull;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "애플리케이션 작업 요청 DTO")
public class ApplicationOperationRequest {

    @NotNull(message = "Operation type is required")
    @Schema(description = "작업 유형", required = true, example = "START")
    private ActionType operation;

    @NotNull(message = "Application status ID is required")
    @Schema(description = "애플리케이션 상태 ID", required = true, example = "789")
    private Long applicationStatusId;

    @NotNull(message = "Reason is required")
    @Schema(description = "작업 사유", required = true, example = "Scheduled maintenance")
    private String reason;

    @Schema(description = "상세 사유", example = "정기 점검을 위한 애플리케이션 재시작")
    private String detailReason;

    @Schema(description = "작업 수행자 사용자명", example = "admin")
    private String username;
}
