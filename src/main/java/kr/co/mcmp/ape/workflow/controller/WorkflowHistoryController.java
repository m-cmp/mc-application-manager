package kr.co.mcmp.ape.workflow.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.co.mcmp.response.ResponseWrapper;
import kr.co.mcmp.ape.workflow.dto.entityMappingDto.WorkflowHistoryDto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Workflow History", description = "워크플로우 이력 관리")
@RequiredArgsConstructor
@RequestMapping("/workflow/history")
@RestController
public class WorkflowHistoryController {

    // private final WorkflowService workflowService;

    // @Operation(summary="워크플로우 이력 조회")
    // @GetMapping("/{workflowIdx}")
    // public ResponseWrapper<List<WorkflowHistoryDto>> getWorkflowHistoryList(@PathVariable Long workflowIdx) {
    //     return new ResponseWrapper<>(workflowService.getWorkflowHistoryList(workflowIdx));
    // }
}
