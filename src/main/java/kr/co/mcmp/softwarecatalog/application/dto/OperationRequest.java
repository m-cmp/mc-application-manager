package kr.co.mcmp.softwarecatalog.application.dto;

import kr.co.mcmp.softwarecatalog.application.constants.ActionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 애플리케이션 운영 요청 정보를 담는 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationRequest {
    
    /**
     * 수행할 작업
     */
    private ActionType operation;
    
    /**
     * 애플리케이션 상태 ID
     */
    private Long applicationStatusId;
    
    /**
     * 작업 사유
     */
    private String reason;
    
    /**
     * 사용자명
     */
    private String username;
    
    /**
     * 추가 파라미터
     */
    private java.util.Map<String, Object> additionalParams;
}


