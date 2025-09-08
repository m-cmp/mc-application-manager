package kr.co.mcmp.softwarecatalog.application.dto;

import kr.co.mcmp.softwarecatalog.application.constants.ActionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 애플리케이션 운영 결과를 담는 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationResult {
    
    /**
     * 수행된 작업
     */
    private ActionType operation;
    
    /**
     * 애플리케이션 상태 ID
     */
    private Long applicationStatusId;
    
    /**
     * 작업 성공 여부
     */
    private boolean success;
    
    /**
     * 결과 메시지
     */
    private String message;
    
    /**
     * 작업 결과 데이터
     */
    private Map<String, Object> resultData;
    
    /**
     * 오류 메시지 (실패시)
     */
    private String errorMessage;
    
    /**
     * 실행 시간
     */
    private LocalDateTime executedAt;
    
    /**
     * 실행자
     */
    private String executedBy;
    
    /**
     * 성공 결과를 생성합니다.
     */
    public static OperationResult success(ActionType operation, Long applicationStatusId, 
                                        String message, Map<String, Object> resultData, String executedBy) {
        return OperationResult.builder()
                .operation(operation)
                .applicationStatusId(applicationStatusId)
                .success(true)
                .message(message)
                .resultData(resultData)
                .executedAt(LocalDateTime.now())
                .executedBy(executedBy)
                .build();
    }
    
    /**
     * 실패 결과를 생성합니다.
     */
    public static OperationResult failure(ActionType operation, Long applicationStatusId, 
                                        String errorMessage, String executedBy) {
        return OperationResult.builder()
                .operation(operation)
                .applicationStatusId(applicationStatusId)
                .success(false)
                .errorMessage(errorMessage)
                .executedAt(LocalDateTime.now())
                .executedBy(executedBy)
                .build();
    }
}


