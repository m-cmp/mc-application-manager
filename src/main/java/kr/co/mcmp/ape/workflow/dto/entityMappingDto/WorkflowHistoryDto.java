package kr.co.mcmp.ape.workflow.dto.entityMappingDto;

import kr.co.mcmp.oss.dto.OssDto;
import kr.co.mcmp.oss.dto.OssTypeDto;
import kr.co.mcmp.ape.workflow.Entity.WorkflowHistory;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Builder
@Getter
public class WorkflowHistoryDto {
    private Long workflowHistoryIdx;
    private Long workflowIdx;
    private String runScript;
    private String runUserId;
    private LocalDateTime runDate;

    // from : 외부 (entity -> dto)
    public static WorkflowHistoryDto from(WorkflowHistory workflowHistory) {
        return WorkflowHistoryDto.builder()
                .workflowHistoryIdx(workflowHistory.getWorkflowHistoryIdx())
                .workflowIdx(workflowHistory.getWorkflow().getWorkflowIdx())
                .runScript(workflowHistory.getRunScript())
                .runUserId(workflowHistory.getRunUserId())
                .runDate(workflowHistory.getRunDate())
                .build();
    }

    // of : 내부 (dto -> dto)
    public static WorkflowHistoryDto of(WorkflowHistoryDto workflowHistoryDto) {
        return WorkflowHistoryDto.builder()
                .workflowHistoryIdx(workflowHistoryDto.getWorkflowHistoryIdx())
                .workflowIdx(workflowHistoryDto.getWorkflowIdx())
                .runScript(workflowHistoryDto.getRunScript())
                .runUserId(workflowHistoryDto.getRunUserId())
                .runDate(workflowHistoryDto.getRunDate())
                .build();
    }

    // toEntity : Entity 변환 (dto -> entity)
    public static WorkflowHistory toEntity(WorkflowHistoryDto workflowHistoryDto, WorkflowDto workflowDto, OssDto ossDto, OssTypeDto ossTypeDto) {
        return WorkflowHistory.builder()
                .workflowHistoryIdx(workflowHistoryDto.getWorkflowHistoryIdx())
                .workflow(WorkflowDto.toEntity(workflowDto, ossDto, ossTypeDto))
                .runScript(workflowHistoryDto.getRunScript())
                .runUserId(workflowHistoryDto.getRunUserId())
                .runDate(workflowHistoryDto.getRunDate())
                .build();
    }

    //
    public static WorkflowHistory buildEntity(
                                                WorkflowDto workflowDto,
                                                OssDto ossDto,
                                                OssTypeDto ossTypeDto,
                                                String runScript,
                                                String runUserId,
                                                LocalDateTime runDate) {
        return WorkflowHistory.builder()
                .workflow(WorkflowDto.toEntity(workflowDto, ossDto, ossTypeDto))
                .runScript(runScript)
                .runUserId(runUserId)
                .runDate(runDate)
                .build();
    }
}
