package kr.co.mcmp.ape.workflow.dto.entityMappingDto;

import kr.co.mcmp.oss.dto.OssDto;
import kr.co.mcmp.oss.dto.OssTypeDto;
import kr.co.mcmp.ape.workflow.Entity.Workflow;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
@NoArgsConstructor // 기본 생성자 추가
public class WorkflowDto {
    private Long workflowIdx;
    private String workflowName;
    private String workflowPurpose;
    private Long ossIdx;
    private String script;


    // from : 외부 (entity -> dto)
    public static WorkflowDto from(Workflow workflow) {
        return WorkflowDto.builder()
                .workflowIdx(workflow.getWorkflowIdx())
                .workflowName(workflow.getWorkflowName())
                .workflowPurpose(workflow.getWorkflowPurpose())
                .ossIdx(workflow.getOss().getOssIdx())
                .script(workflow.getScript())
                .build();
    }

    // of : 내부 (dto -> dto)
    public static WorkflowDto of(WorkflowDto workflowDto) {
        return WorkflowDto.builder()
                .workflowIdx(workflowDto.getWorkflowIdx())
                .workflowName(workflowDto.getWorkflowName())
                .workflowPurpose(workflowDto.getWorkflowPurpose())
                .ossIdx(workflowDto.getOssIdx())
                .script(workflowDto.getScript())
                .build();
    }

    // toEntity : Entity 변환 (dto -> entity)
    public static Workflow toEntity(WorkflowDto workflowDto, OssDto ossDto, OssTypeDto ossTypeDto) {
        return Workflow.builder()
                .workflowIdx(workflowDto.getWorkflowIdx())
                .workflowName(workflowDto.getWorkflowName())
                .workflowPurpose(workflowDto.getWorkflowPurpose())
                .oss(OssDto.toEntity(ossDto, ossTypeDto))
                .script(workflowDto.getScript())
                .build();
    }

//    // registWorkflow : Workflow 등록 / 수정
//    public static Workflow saveWorkflow(WorkflowParamDto.WorkflowParamList workflowDto, OssDto ossDto, OssTypeDto ossTypeDto) {
//        return Workflow.builder()
//                .workflowIdx(workflowDto.getWorkflowIdx())
//                .workflowName(workflowDto.getWorkflowName())
//                .workflowPurpose(workflowDto.getWorkflowPurpose())
//                .oss(OssDto.toEntity(ossDto, ossTypeDto))
//                .script(workflowDto.getScript())
//                .build();
//    }
//
//    // WorkflowStageMappingDto -> WorkflowDto 변환
//    public static WorkflowDto workflowStageToWorkflowDto(WorkflowStageMappingDto.WorkflowStageMappingList workflowStageMappingList) {
//        return WorkflowDto.builder()
//                .workflowIdx(workflowStageMappingList.getWorkflowIdx())
//                .workflowName(workflowStageMappingList.getWorkflowName())
//                .workflowPurpose(workflowStageMappingList.getWorkflowPurpose())
//                .ossIdx(workflowStageMappingList.getOssIdx())
//                .script(workflowStageMappingList.getScript())
//                .build();
//    }
}
