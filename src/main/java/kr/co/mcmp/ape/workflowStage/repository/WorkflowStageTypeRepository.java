package kr.co.mcmp.ape.workflowStage.repository;

import kr.co.mcmp.ape.workflowStage.Entity.WorkflowStageType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkflowStageTypeRepository extends JpaRepository<WorkflowStageType, Long> {
    List<WorkflowStageType> findAll();
    WorkflowStageType save(WorkflowStageType workflowStageType);
    void deleteById(Long workflowStageTypeIdx);
    WorkflowStageType findByWorkflowStageTypeIdx(Long workflowStageTypeIdx);
    WorkflowStageType findByWorkflowStageTypeName(String workflowStageTypeName);
    Boolean existsByWorkflowStageTypeName(String workflowStageTypeName);
}
