package kr.co.mcmp.ape.workflow.repository;

import kr.co.mcmp.ape.workflow.Entity.WorkflowParam;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkflowParamRepository extends JpaRepository<WorkflowParam, Long> {
    void deleteByWorkflow_WorkflowIdx(Long workflowIdx);
    List<WorkflowParam> findByWorkflow_WorkflowIdx(Long workflowIdx);
    List<WorkflowParam> findByWorkflow_WorkflowIdxAndEventListenerYn(Long workflowIdx, String eventListenerYn);
}
