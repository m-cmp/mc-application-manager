package kr.co.mcmp.ape.workflow.dto.resDto;

import kr.co.mcmp.ape.workflow.dto.entityMappingDto.WorkflowDto;
import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Builder
@Getter
public class WorkflowLogResDto {
    private int buildIdx;
    private String buildLog;

    // of (dto -> dto)
    public static WorkflowLogResDto of(int buildIdx, String buildLog) {
        return WorkflowLogResDto.builder()
                .buildIdx(buildIdx)
                .buildLog(buildLog)
                .build();
    }

    public static List<WorkflowLogResDto> createList() {
        List<WorkflowLogResDto> list = new ArrayList<>();
        return list;
    }

    public static List<WorkflowLogResDto> addToList(List<WorkflowLogResDto> list, int buildIdx, String buildLog) {
        WorkflowLogResDto workflowLogResDto = WorkflowLogResDto.builder()
            .buildIdx(buildIdx)
            .buildLog(buildLog)
            .build();

        list.add(workflowLogResDto);
        return list;
    }



}
