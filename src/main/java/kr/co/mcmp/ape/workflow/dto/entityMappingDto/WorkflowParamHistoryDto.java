package kr.co.mcmp.ape.workflow.dto.entityMappingDto;

import kr.co.mcmp.oss.dto.OssDto;
import kr.co.mcmp.oss.dto.OssTypeDto;
import kr.co.mcmp.ape.workflow.Entity.WorkflowParamHistory;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Builder
@Getter
public class WorkflowParamHistoryDto {
    private Long workflowParamHistoryIdx;
    private Long workflowIdx;
    private String runUserId;
    private LocalDateTime runDate;


    // from : 외부 (entity -> dto)
    public static WorkflowParamHistoryDto from(WorkflowParamHistory workflowParamHistory) {
        return WorkflowParamHistoryDto.builder()
                .workflowParamHistoryIdx(workflowParamHistory.getWorkflowParamHistoryIdx())
                .workflowIdx(workflowParamHistory.getWorkflow().getWorkflowIdx())
                .runUserId(workflowParamHistory.getRunUserId())
                .runDate(workflowParamHistory.getRunDate())
                .build();
    }

    // of : 내부 (dto -> dto)
    public static WorkflowParamHistoryDto of(WorkflowParamHistoryDto workflowParamHistoryDto) {
        return WorkflowParamHistoryDto.builder()
                .workflowParamHistoryIdx(workflowParamHistoryDto.getWorkflowParamHistoryIdx())
                .workflowIdx(workflowParamHistoryDto.getWorkflowIdx())
                .runUserId(workflowParamHistoryDto.getRunUserId())
                .runDate(workflowParamHistoryDto.getRunDate())
                .build();
    }

    // toEntity : Entity 변환 (dto -> entity)
    public static WorkflowParamHistory toEntity(WorkflowParamHistoryDto workflowParamHistoryDto, WorkflowDto workflowDto, OssDto ossDto, OssTypeDto ossTypeDto) {
        return WorkflowParamHistory.builder()
                .workflowParamHistoryIdx(workflowParamHistoryDto.getWorkflowParamHistoryIdx())
                .workflow(WorkflowDto.toEntity(workflowDto, ossDto, ossTypeDto))
                .runUserId(workflowParamHistoryDto.getRunUserId())
                .runDate(workflowParamHistoryDto.getRunDate())
                .build();
    }
}
