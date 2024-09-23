package kr.co.mcmp.ape.workflow.dto.entityMappingDto;

import kr.co.mcmp.oss.dto.OssDto;
import kr.co.mcmp.oss.dto.OssTypeDto;
import kr.co.mcmp.ape.workflow.Entity.WorkflowStageMapping;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class WorkflowStageMappingDto {
    private Long mappingIdx;
    private Long workflowIdx;
    private Integer stageOrder;
    private Long workflowStageIdx;
    private String stageContent;

    // from : 외부 (entity -> dto)
    public static WorkflowStageMappingDto from(WorkflowStageMapping workflowStageMapping) {
        return WorkflowStageMappingDto.builder()
                .mappingIdx(workflowStageMapping.getMappingIdx())
                .workflowIdx(workflowStageMapping.getWorkflow().getWorkflowIdx())
                .stageOrder(workflowStageMapping.getStageOrder())
                .workflowStageIdx(workflowStageMapping.getWorkflowStageIdx())
                .stageContent(workflowStageMapping.getStageContent())
                .build();
    }

    // of : 내부 (dto -> dto)
    public static WorkflowStageMappingDto of(WorkflowStageMappingDto workflowStageMappingDto) {
        return WorkflowStageMappingDto.builder()
                .mappingIdx(workflowStageMappingDto.getMappingIdx())
                .workflowIdx(workflowStageMappingDto.getWorkflowIdx())
                .stageOrder(workflowStageMappingDto.getStageOrder())
                .workflowStageIdx(workflowStageMappingDto.getWorkflowStageIdx())
                .stageContent(workflowStageMappingDto.getStageContent())
                .build();
    }

    // toEntity : Entity 변환 (dto -> entity)
    public static WorkflowStageMapping toEntity(WorkflowStageMappingDto workflowStageMappingBaseData, WorkflowDto workflowDto, OssDto ossDto, OssTypeDto ossTypeDto) {
        return WorkflowStageMapping.builder()
                .mappingIdx(workflowStageMappingBaseData.getMappingIdx())
                .workflow(WorkflowDto.toEntity(workflowDto, ossDto, ossTypeDto))
                .stageOrder(workflowStageMappingBaseData.getStageOrder())
                .workflowStageIdx(workflowStageMappingBaseData.getWorkflowStageIdx())
                .stageContent(workflowStageMappingBaseData.getStageContent())
                .build();
    }

    // default Script Set
    public static WorkflowStageMappingDto setWorkflowTemplate(String workflowStageContent) {
        return WorkflowStageMappingDto.builder()
                .stageContent(workflowStageContent)
                .build();
    }
}
