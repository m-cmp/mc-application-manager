package kr.co.mcmp.ape.service.jenkins.jenkinsLog.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
//import kr.co.strato.workflow.service.jenkins.jenkinsLog.service.JenkinsLogService;
//import kr.co.strato.workflow.model.WorkflowHistory;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "jenkinsLog", description ="젠킨스 로그")
@RequiredArgsConstructor
@RequestMapping("/jenkins")
@RestController
public class JenkinsLogController {
//    private final JenkinsLogService jenkinsLogService;
//
//    @Operation(summary="로그 조회")
//    @GetMapping("/logs/{workflowId}")
//    public ResponseWrapper<List<WorkflowHistory>> getLog(@PathVariable Integer workflowId) {
//        return new ResponseWrapper<>(jenkinsLogService.getLogs(workflowId));
//    }
}
