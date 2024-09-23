package kr.co.mcmp.ape.workflowStage.repository;

import kr.co.mcmp.ape.workflowStage.Entity.WorkflowStage;
import kr.co.mcmp.ape.workflowStage.Entity.WorkflowStageType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkflowStageRepository extends JpaRepository<WorkflowStage, Long> {
    List<WorkflowStage> findAll();
    void deleteByWorkflowStageIdx(Long workflowStageIdx);
    WorkflowStage findByWorkflowStageIdx(Long workflowStageIdx);
    Boolean existsByWorkflowStageTypeAndWorkflowStageName(WorkflowStageType workflowStageType, String workflowStageName);

    List<WorkflowStage> findByWorkflowStageType(WorkflowStageType workflowStageType);
    Boolean existsByWorkflowStageType(WorkflowStageType workflowStageType);
}
