package kr.co.mcmp.ape.workflowStage.dto;

import kr.co.mcmp.ape.workflowStage.Entity.WorkflowStage;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class WorkflowStageDto {
    private Long workflowStageIdx;
    private Long workflowStageTypeIdx;
    private String workflowStageTypeName;
    private Integer workflowStageOrder;
    private String workflowStageName;
    private String workflowStageDesc;
    private String workflowStageContent;


    // from : 외부 (entity -> dto)
    public static WorkflowStageDto from(WorkflowStage workflowStage) {
        return WorkflowStageDto.builder()
                .workflowStageIdx(workflowStage.getWorkflowStageIdx())
                .workflowStageTypeIdx(workflowStage.getWorkflowStageType().getWorkflowStageTypeIdx())
                .workflowStageTypeName(workflowStage.getWorkflowStageType().getWorkflowStageTypeName())
                .workflowStageOrder(workflowStage.getWorkflowStageOrder())
                .workflowStageName(workflowStage.getWorkflowStageName())
                .workflowStageDesc(workflowStage.getWorkflowStageDesc())
                .workflowStageContent(workflowStage.getWorkflowStageContent())
                .build();
    }

    // of : 내부 (dto -> dto)
    public static WorkflowStageDto of(WorkflowStageDto workflowStageDto) {
        return WorkflowStageDto.builder()
                .workflowStageIdx(workflowStageDto.getWorkflowStageIdx())
                .workflowStageTypeIdx(workflowStageDto.getWorkflowStageTypeIdx())
                .workflowStageTypeName(workflowStageDto.getWorkflowStageTypeName())
                .workflowStageOrder(workflowStageDto.getWorkflowStageOrder())
                .workflowStageName(workflowStageDto.getWorkflowStageName())
                .workflowStageDesc(workflowStageDto.getWorkflowStageDesc())
                .workflowStageContent(workflowStageDto.getWorkflowStageContent())
                .build();
    }

    // toEntity : Entity 변환 (dto -> entity)
    public static WorkflowStage toEntity(WorkflowStageDto workflowStageDto, WorkflowStageTypeDto workflowStageTypeDto) {
        return WorkflowStage.builder()
                .workflowStageIdx(workflowStageDto.getWorkflowStageIdx())
                .workflowStageType(WorkflowStageTypeDto.toEntity(workflowStageTypeDto))
                .workflowStageOrder(workflowStageDto.getWorkflowStageOrder())
                .workflowStageName(workflowStageDto.getWorkflowStageName())
                .workflowStageDesc(workflowStageDto.getWorkflowStageDesc())
                .workflowStageContent(workflowStageDto.getWorkflowStageContent())
                .build();
    }

    // default Script Set
    public static WorkflowStageDto setWorkflowStageDefaultScript(Long workflowStageTypeIdx,String workflowStageContent) {
        return WorkflowStageDto.builder()
                .workflowStageTypeIdx(workflowStageTypeIdx)
                .workflowStageContent(workflowStageContent)
                .build();
    }

    // default Script List Set
    public static List<WorkflowStageDto> setWorkflowStageDefaultScriptList(Long workflowStageTypeIdx, String workflowStageContent) {
        WorkflowStageDto workflowStageDto = WorkflowStageDto.builder()
                .workflowStageTypeIdx(workflowStageTypeIdx)
                .workflowStageContent(workflowStageContent)
                .build();
        return List.of(workflowStageDto);
    }
}
