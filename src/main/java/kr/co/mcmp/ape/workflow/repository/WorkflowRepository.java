package kr.co.mcmp.ape.workflow.repository;

import kr.co.mcmp.oss.entity.Oss;
import kr.co.mcmp.ape.workflow.Entity.Workflow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkflowRepository extends JpaRepository<Workflow, Long> {
    List<Workflow> findAll();
    Workflow findByWorkflowIdx(Long workflowIdx);
    Workflow findByWorkflowName(String workflowName);
    void deleteByWorkflowIdx(Long workflowIdx);
    Boolean existsByOss_OssIdx(Long ossIdx);
}
