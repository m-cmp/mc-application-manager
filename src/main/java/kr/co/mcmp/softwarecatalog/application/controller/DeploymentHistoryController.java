package kr.co.mcmp.softwarecatalog.application.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import kr.co.mcmp.response.ResponseWrapper;
import kr.co.mcmp.softwarecatalog.application.model.DeploymentHistory;
import kr.co.mcmp.softwarecatalog.application.model.DeploymentLog;
import kr.co.mcmp.softwarecatalog.application.service.ApplicationOrchestrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 배포 히스토리 관련 API 컨트롤러
 */
@RestController
@RequestMapping("/api/applications/deployment-history")
@RequiredArgsConstructor
@Slf4j
public class DeploymentHistoryController {

    private final ApplicationOrchestrationService applicationOrchestrationService;

    @Operation(summary = "Get deployment history by catalog ID", description = "Retrieve deployment history for a specific catalog.")
    @GetMapping("/catalog/{catalogId}")
    public ResponseEntity<ResponseWrapper<List<DeploymentHistory>>> getDeploymentHistories(
            @Parameter(description = "Catalog ID to get deployment history for", required = true, example = "123") @PathVariable Long catalogId,
            @Parameter(description = "Username filter (optional)", example = "admin") @RequestParam(required = false) String username) {
        List<DeploymentHistory> result = applicationOrchestrationService.getDeploymentHistories(catalogId, username);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }
    
    @Operation(summary = "Get deployment logs by deployment ID", description = "Retrieve deployment logs for a specific deployment.")
    @GetMapping("/{deploymentId}/logs")
    public ResponseEntity<ResponseWrapper<List<DeploymentLog>>> getDeploymentLogs(
            @Parameter(description = "Deployment ID to get logs for", required = true, example = "123") @PathVariable Long deploymentId,
            @Parameter(description = "Username filter (optional)", example = "admin") @RequestParam(required = false) String username) {
        List<DeploymentLog> result = applicationOrchestrationService.getDeploymentLogs(deploymentId, username);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }
    
    @Operation(summary = "Delete application by deployment history ID", description = "Delete a specific application deployment using deployment history ID.")
    @DeleteMapping("/{deploymentHistoryId}")
    public ResponseEntity<ResponseWrapper<Map<String, Object>>> deleteApplicationByDeploymentHistoryId(
            @Parameter(description = "Deployment History ID to delete", required = true, example = "123") @PathVariable Long deploymentHistoryId,
            @Parameter(description = "Reason for deletion", required = true, example = "Application no longer needed") @RequestParam String reason,
            @Parameter(description = "Username performing the deletion (optional)", example = "admin") @RequestParam(required = false) String username) throws Exception {
        Map<String, Object> result = applicationOrchestrationService.deleteApplicationByDeploymentHistoryId(deploymentHistoryId, reason, username);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }
}
