package kr.co.mcmp.ape.workflowStage.service;

import kr.co.mcmp.ape.workflowStage.dto.WorkflowStageDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public interface WorkflowStageService {
    List<WorkflowStageDto> getWorkflowStageList();
    Long registWorkflowStage(WorkflowStageDto workflowStageDto);
    Boolean updateWorkflowStage(WorkflowStageDto workflowStageDto);
    @Transactional
    Boolean deleteWorkflowStage(Long workflowStageIdx);
    WorkflowStageDto detailWorkflowStage(Long workflowStageIdx);
    Boolean isWorkflowStageNameDuplicated(String workflowStageTypeName, String workflowStageName);
    List<WorkflowStageDto> getDefaultWorkflowStage(String workflowStageTypeName);
}
