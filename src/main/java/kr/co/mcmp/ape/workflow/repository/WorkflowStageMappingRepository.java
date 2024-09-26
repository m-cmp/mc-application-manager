package kr.co.mcmp.ape.workflow.repository;

import kr.co.mcmp.ape.workflow.Entity.WorkflowStageMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkflowStageMappingRepository extends JpaRepository<WorkflowStageMapping, Long> {
    void deleteByWorkflow_WorkflowIdx(Long workflowIdx);
    List<WorkflowStageMapping> findByWorkflow_WorkflowIdx(Long workflowIdx);

    Boolean existsByWorkflowStageIdx(Long workflowStageIdx);
}
