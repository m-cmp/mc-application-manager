package kr.co.mcmp.ape.workflow.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.co.mcmp.response.ResponseWrapper;
import kr.co.mcmp.ape.workflow.dto.entityMappingDto.WorkflowParamDto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Workflow Param", description = "워크플로우 파라미터 관리")
@RequiredArgsConstructor
@RequestMapping("/workflow/param")
@RestController
public class WorkflowParamController {

    // private final WorkflowService workflowService;

    // @Operation(summary="워크플로우 파라미터 목록 조회")
    // @GetMapping("/list")
    // public ResponseWrapper<List<WorkflowParamDto>> getWorkflowParamList() {
    //     return new ResponseWrapper<>(workflowService.getWorkflowParamList());
    // }
}
