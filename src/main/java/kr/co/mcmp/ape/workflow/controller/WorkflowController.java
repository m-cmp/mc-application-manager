package kr.co.mcmp.ape.workflow.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.co.mcmp.response.ResponseCode;
import kr.co.mcmp.response.ResponseWrapper;
import kr.co.mcmp.ape.workflow.dto.entityMappingDto.WorkflowStageMappingDto;
import kr.co.mcmp.ape.workflow.dto.reqDto.WorkflowReqDto;
import kr.co.mcmp.ape.workflow.dto.resDto.WorkflowDetailResDto;
import kr.co.mcmp.ape.workflow.dto.resDto.WorkflowListResDto;
import kr.co.mcmp.ape.workflow.dto.resDto.WorkflowLogResDto;
import kr.co.mcmp.ape.workflow.dto.resDto.WorkflowStageTypeAndStageNameResDto;
import kr.co.mcmp.ape.workflow.service.AppProvEngineService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Workflow", description = "워크플로우 배포 관리")
@RequiredArgsConstructor
@RequestMapping("/workflow")
@RestController
public class WorkflowController {
    
    private final AppProvEngineService AppProvEngineService;

    // @Operation(summary="워크플로우 목록 조회")
    // @GetMapping("/list")
    // public ResponseWrapper<List<WorkflowListResDto>> getWorkflowList() {
    //     return new ResponseWrapper<>(workflowService.getWorkflowList());
    // }

    // @Operation(summary="워크플로우 등록")
    // @PostMapping
    // public ResponseWrapper<Long> registWorkflow(@RequestBody WorkflowReqDto workflowReqDto) {

    //     if ( StringUtils.isBlank(workflowReqDto.getWorkflowInfo().getWorkflowName()) ) {
    //         return new ResponseWrapper<>(ResponseCode.BAD_REQUEST, "WorkflowName");
    //     }
    //     else if ( StringUtils.isBlank(workflowReqDto.getWorkflowInfo().getWorkflowPurpose()) ) {
    //         return new ResponseWrapper<>(ResponseCode.BAD_REQUEST, "WorkflowPurpose");
    //     }
    //     else if ( workflowReqDto.getWorkflowInfo().getOssIdx() == 0) {
    //         return new ResponseWrapper<>(ResponseCode.BAD_REQUEST, "OssIdx");
    //     }
    //     else if ( StringUtils.isBlank(workflowReqDto.getWorkflowInfo().getScript()) ) {
    //         return new ResponseWrapper<>(ResponseCode.BAD_REQUEST, "Script");
    //     }

    //     return new ResponseWrapper<>(workflowService.registWorkflow(workflowReqDto));
    // }


    // @Operation(summary="워크플로우 수정")
    // @PatchMapping("/{workflowIdx}")
    // public ResponseWrapper<Boolean> updateWorkflow(@PathVariable Long workflowIdx, @RequestBody WorkflowReqDto workflowReqDto) {
    //     if ( workflowIdx == 0 || workflowReqDto.getWorkflowInfo().getWorkflowIdx() == 0) {
    //         return new ResponseWrapper<>(ResponseCode.BAD_REQUEST, "WorkflowIdx");
    //     }

    //     return new ResponseWrapper<>(workflowService.updateWorkflow(workflowReqDto));
    // }


    // @Operation(summary="워크플로우 배포 삭제")
    // @DeleteMapping("/{workflowIdx}")
    // public ResponseWrapper<Boolean> deleteWorkflow(@PathVariable Long workflowIdx) {
    //     return new ResponseWrapper<>(workflowService.deleteWorkflow(workflowIdx));
    // }


    // @Operation(summary="워크플로우 상세 조회")
    // @GetMapping("/{workflowIdx}")
    // public ResponseWrapper<WorkflowDetailResDto> getWorkflow(@PathVariable Long workflowIdx) {
    //     return new ResponseWrapper<>(workflowService.getWorkflow(workflowIdx));
    // }


    // @Operation(summary="워크플로우 명 중복 체크", description="true : 중복 / false : 중복 아님")
    // @GetMapping("/name/duplicate")
    // public ResponseWrapper<Boolean> isWorkflowNameDuplicated(@RequestParam String workflowName) {
    //     if ( StringUtils.isBlank(workflowName) ) {
    //         return new ResponseWrapper<>(ResponseCode.BAD_REQUEST, "workflowName");
    //     }

    //     return new ResponseWrapper<>(workflowService.isWorkflowNameDuplicated(workflowName));
    // }


    // @Operation(summary="스테이지 타입 별 목록 조회")
    // @GetMapping("/workflowStageList")
    // public ResponseWrapper<List<WorkflowStageTypeAndStageNameResDto>> getWorkflowStageList() {
    //     return new ResponseWrapper<>(workflowService.getWorkflowStageList());
    // }


    // @Operation(summary="Template 조회")
    // @GetMapping("/template/{workflowName}")
    // public ResponseWrapper<List<WorkflowStageMappingDto>> getWorkflowTemplate(@PathVariable String workflowName) {
    //     if ( StringUtils.isBlank(workflowName) ) {
    //         return new ResponseWrapper<>(ResponseCode.BAD_REQUEST, "workflowName");
    //     }
    //     return new ResponseWrapper<>(workflowService.getWorkflowTemplate(workflowName));
    // }


    // @Operation(summary="워크플로우 배포 실행")
    // @GetMapping("/run/{workflowIdx}")
    // public ResponseWrapper<Boolean> runWorkflowGet(@PathVariable Long workflowIdx) {
    //     return new ResponseWrapper<>(workflowService.runWorkflow(workflowIdx));
    // }


    // @Operation(summary="워크플로우 배포 실행")
    // @PostMapping("/run")
    // public ResponseWrapper<Object> runWorkflowPost(@RequestBody WorkflowReqDto workflowReqDto) {
    //     return new ResponseWrapper<>(workflowService.runWorkflow(workflowReqDto));
    // }

    // @Operation(summary="워크플로우 로그")
    // @GetMapping("/log/{workflowIdx}")
    // public ResponseWrapper<List<WorkflowLogResDto>> getWorkflowLog(@PathVariable Long workflowIdx) {
    //     return new ResponseWrapper<>(workflowService.getWorkflowLog(workflowIdx));
    // }
}
