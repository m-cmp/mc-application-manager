package kr.co.mcmp.ape.workflowStage.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.co.mcmp.response.ResponseCode;
import kr.co.mcmp.response.ResponseWrapper;
import kr.co.mcmp.ape.workflowStage.dto.WorkflowStageDto;
import kr.co.mcmp.ape.workflowStage.service.WorkflowStageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Workflow Stage", description = "워크플로우 스테이지 관리")
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/workflowStage")
@RestController
public class WorkflowStageController {

    private final WorkflowStageService workflowStageService;

    @Operation(summary="워크플로우 스테이지 목록")
    @GetMapping("/list")
    public ResponseWrapper<List<WorkflowStageDto>> getWorkflowStageList() {
        return new ResponseWrapper<>(workflowStageService.getWorkflowStageList());
    }

    @Operation(summary="워크플로우 스테이지 등록")
    @PostMapping
    public ResponseWrapper<Long> registWorkflowStage(@RequestBody WorkflowStageDto workflowStageDto) {
        return new ResponseWrapper<>(workflowStageService.registWorkflowStage(workflowStageDto));
    }

    @Operation(summary="워크플로우 스테이지 수정")
    @PatchMapping("/{workflowStageIdx}")
    public ResponseWrapper<Boolean> updateWorkflowStage(@PathVariable Long workflowStageIdx, @RequestBody WorkflowStageDto workflowStageDto) {
        if ( workflowStageIdx != 0 || workflowStageDto.getWorkflowStageIdx() != 0 ) {
            return new ResponseWrapper<>(workflowStageService.updateWorkflowStage(workflowStageDto));
        }
        return new ResponseWrapper<>(ResponseCode.BAD_REQUEST, "workflowStageIdx");
    }

    @Operation(summary="워크플로우 스테이지 삭제")
    @DeleteMapping("/{workflowStageIdx}")
    public ResponseWrapper<Boolean> deleteWorkflowStage(@PathVariable Long workflowStageIdx) {
        return new ResponseWrapper<>(workflowStageService.deleteWorkflowStage(workflowStageIdx));
    }

    @Operation(summary="워크플로우 스테이지 상세")
    @GetMapping("/{workflowStageIdx}")
    public ResponseWrapper<WorkflowStageDto> detailWorkflowStage(@PathVariable Long workflowStageIdx) {
        return new ResponseWrapper<>(workflowStageService.detailWorkflowStage(workflowStageIdx));
    }

    @Operation(summary="워크플로우 스테이지 명 중복 체크", description="true : 중복 / false : 중복 아님")
    @GetMapping("/duplicate")
    public ResponseWrapper<Boolean> isWorkflowStageNameDuplicated(@RequestParam String workflowStageTypeName, @RequestParam String workflowStageName) {
        if ( StringUtils.isBlank(workflowStageTypeName) ) {
            return new ResponseWrapper<>(ResponseCode.BAD_REQUEST, "workflowStageTypeName");
        }
        else if ( StringUtils.isBlank(workflowStageName) ) {
            return new ResponseWrapper<>(ResponseCode.BAD_REQUEST, "workflowStageName");
        }

        return new ResponseWrapper<>(workflowStageService.isWorkflowStageNameDuplicated(workflowStageTypeName, workflowStageName));
    }

    @Operation(summary="워크플로우 스테이지 생성시 > 기본 스크립트 조회")
    @GetMapping("/default/script/{workflowStageTypeName}")
    public ResponseWrapper<List<WorkflowStageDto>> getDefaultWorkflowStage(@PathVariable String workflowStageTypeName) {
        return new ResponseWrapper<>(workflowStageService.getDefaultWorkflowStage(workflowStageTypeName));
    }
}
