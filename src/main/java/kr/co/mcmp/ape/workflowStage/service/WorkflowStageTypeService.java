package kr.co.mcmp.ape.workflowStage.service;

import kr.co.mcmp.ape.workflowStage.dto.WorkflowStageTypeDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public interface WorkflowStageTypeService {
    List<WorkflowStageTypeDto> getWorkflowStageTypeList();

    Long registWorkflowStage(WorkflowStageTypeDto workflowStageTypeDto);

    Boolean updateWorkflowStageType(WorkflowStageTypeDto workflowStageTypeDto);

    @Transactional
    Boolean deleteWorkflowStageType(Long workflowStageTypeIdx);

    WorkflowStageTypeDto detailWorkflowStageType(Long workflowStageTypeIdx);
}
