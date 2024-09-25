package kr.co.mcmp.ape.workflow.service.jenkins.jenkinsLog.service;

//import com.cdancy.jenkins.rest.domain.common.RequestStatus;
//import com.cdancy.jenkins.rest.domain.job.PipelineNode;
//import kr.co.strato.workflow.service.jenkins.api.JenkinsRestApi;
//import kr.co.strato.workflow.service.jenkins.model.JenkinsBuildDescribeLog;
//import kr.co.strato.workflow.service.jenkins.model.JenkinsBuildDetailLog;
//import kr.co.strato.workflow.service.jenkins.model.JenkinsWorkflow;
//import kr.co.strato.oss.mapper.OssMapper;
//import kr.co.strato.oss.model.Oss;
//import kr.co.strato.workflow.mapper.WorkflowMapper;
//import kr.co.strato.workflow.model.Workflow;
//import kr.co.strato.workflow.model.WorkflowHistory;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.http.ResponseEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class JenkinsLogService {
//    private final JenkinsRestApi api;
//    private final OssMapper ossMapper;
//    private final WorkflowMapper workflowMapper;
//
//    public List<WorkflowHistory> getLogs(Integer workflowId) {
//        // 1. 정보 Get
//        Workflow workflow = workflowMapper.selectWorkflow(workflowId);
//        Oss jenkins = ossMapper.selectOss(workflow.getJenkinsId());
//
//        // 2. history에서 jenkins build ID 가져오기
//        List<WorkflowHistory> workflowHistoryList = workflowMapper.selectWorkflowHistoryList(workflowId);
//        try {
//            for(WorkflowHistory workflowHistory: workflowHistoryList) {
//                String log = api.getJenkinsBuildConsoleLog(
//                        jenkins.getOssUrl(),
//                        jenkins.getOssUsername(),
//                        jenkins.getOssPassword(),
//                        workflow.getJenkinsJobName(),
//                        workflowHistory.getBuildNumber()
//                );
//                workflowHistory.setLog(log);
//            }
//        } catch (Exception e) {
//            System.err.println(e);
//        }
//        return workflowHistoryList;
//    }
}
