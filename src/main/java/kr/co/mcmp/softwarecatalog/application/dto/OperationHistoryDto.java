package kr.co.mcmp.softwarecatalog.application.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OperationHistoryDto {

    private Long id;
    private Long applicationStatusId; // ApplicationStatus의 ID
    private Long executedById;
    private String reason;
    private String operationType;
    private LocalDateTime createdAt;

}
