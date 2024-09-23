package kr.co.mcmp.ape.workflow.dto.entityMappingDto;

import kr.co.mcmp.oss.dto.OssDto;
import kr.co.mcmp.oss.dto.OssTypeDto;
import kr.co.mcmp.ape.workflow.Entity.WorkflowParam;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class WorkflowParamDto {
    private Long paramIdx;
    private Long workflowIdx;
    private String paramKey;
    private String paramValue;
    private String eventListenerYn;

    // from : 외부 (entity -> dto)
    public static WorkflowParamDto from(WorkflowParam workflowParam) {
        return WorkflowParamDto.builder()
                .paramIdx(workflowParam.getParamIdx())
                .workflowIdx(workflowParam.getWorkflow().getWorkflowIdx())
                .paramKey(workflowParam.getParamKey())
                .paramValue(workflowParam.getParamValue())
                .eventListenerYn(workflowParam.getEventListenerYn())
                .build();
    }

    // of : 내부 (dto -> dto)
    public static WorkflowParamDto of(WorkflowParamDto workflowParamBaseData) {
        return WorkflowParamDto.builder()
                .paramIdx(workflowParamBaseData.getParamIdx())
                .workflowIdx(workflowParamBaseData.getWorkflowIdx())
                .paramKey(workflowParamBaseData.getParamKey())
                .paramValue(workflowParamBaseData.getParamValue())
                .eventListenerYn(workflowParamBaseData.getEventListenerYn())
                .build();
    }

    // toEntity : Entity 변환 (dto -> entity)
    public static WorkflowParam toEntity(WorkflowParamDto WorkflowParamBaseData, WorkflowDto workflowDto, OssDto ossDto, OssTypeDto ossTypeDto) {
        return WorkflowParam.builder()
                .paramIdx(WorkflowParamBaseData.getParamIdx())
                .workflow(WorkflowDto.toEntity(workflowDto, ossDto, ossTypeDto))
                .paramKey(WorkflowParamBaseData.getParamKey())
                .paramValue(WorkflowParamBaseData.getParamValue())
                .eventListenerYn(WorkflowParamBaseData.getEventListenerYn())
                .build();
    }
}
