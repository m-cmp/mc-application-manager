package kr.co.mcmp.softwarecatalog.application.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.co.mcmp.response.ResponseWrapper;
import kr.co.mcmp.softwarecatalog.application.dto.UnifiedLogDTO;
import kr.co.mcmp.softwarecatalog.application.service.UnifiedLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Unified Log Controller
 */
@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Unified Log Management", description = "Unified log management API")
public class UnifiedLogController {
    
    private final UnifiedLogService unifiedLogService;
    
    /**
     * Get logs by deployment ID
     */
    @GetMapping("/deployment/{deploymentId}")
    @Operation(summary = "Get logs by deployment ID", description = "Retrieve all logs for a specific deployment.")
    public ResponseEntity<ResponseWrapper<List<UnifiedLogDTO>>> getLogsByDeploymentId(
            @Parameter(description = "Deployment ID") @PathVariable Long deploymentId) {
        try {
            List<UnifiedLogDTO> logs = unifiedLogService.getLogsByDeploymentId(deploymentId);
            return ResponseEntity.ok(ResponseWrapper.success(logs));
        } catch (Exception e) {
            log.error("Failed to get logs for deployment: {}", deploymentId, e);
            return ResponseEntity.badRequest().body(ResponseWrapper.error("Failed to get logs: " + e.getMessage()));
        }
    }
    
    /**
     * Get logs by deployment ID and module
     */
    @GetMapping("/deployment/{deploymentId}/module/{module}")
    @Operation(summary = "Get logs by deployment ID and module", description = "Retrieve logs for a specific module of a specific deployment.")
    public ResponseEntity<ResponseWrapper<List<UnifiedLogDTO>>> getLogsByDeploymentIdAndModule(
            @Parameter(description = "Deployment ID") @PathVariable Long deploymentId,
            @Parameter(description = "Module (KUBERNETES, DOCKER, APPLICATION, SYSTEM)") @PathVariable String module) {
        try {
            List<UnifiedLogDTO> logs = unifiedLogService.getLogsByDeploymentIdAndModule(deploymentId, module);
            return ResponseEntity.ok(ResponseWrapper.success(logs));
        } catch (Exception e) {
            log.error("Failed to get logs for deployment: {}, module: {}", deploymentId, module, e);
            return ResponseEntity.badRequest().body(ResponseWrapper.error("Failed to get logs: " + e.getMessage()));
        }
    }
    
    /**
     * Get logs by deployment ID and severity
     */
    @GetMapping("/deployment/{deploymentId}/severity/{severity}")
    @Operation(summary = "Get logs by deployment ID and severity", description = "Retrieve logs for a specific severity level of a specific deployment.")
    public ResponseEntity<ResponseWrapper<List<UnifiedLogDTO>>> getLogsByDeploymentIdAndSeverity(
            @Parameter(description = "Deployment ID") @PathVariable Long deploymentId,
            @Parameter(description = "Severity (ERROR, WARN, INFO, DEBUG)") @PathVariable String severity) {
        try {
            List<UnifiedLogDTO> logs = unifiedLogService.getLogsByDeploymentIdAndSeverity(deploymentId, severity);
            return ResponseEntity.ok(ResponseWrapper.success(logs));
        } catch (Exception e) {
            log.error("Failed to get logs for deployment: {}, severity: {}", deploymentId, severity, e);
            return ResponseEntity.badRequest().body(ResponseWrapper.error("Failed to get logs: " + e.getMessage()));
        }
    }
    
    /**
     * Get logs by namespace and pod name
     */
    @GetMapping("/kubernetes")
    @Operation(summary = "Get Kubernetes logs", description = "Retrieve Kubernetes logs by namespace and pod name.")
    public ResponseEntity<ResponseWrapper<List<UnifiedLogDTO>>> getKubernetesLogs(
            @Parameter(description = "Namespace") @RequestParam String namespace,
            @Parameter(description = "Pod name") @RequestParam String podName) {
        try {
            List<UnifiedLogDTO> logs = unifiedLogService.getLogsByNamespaceAndPodName(namespace, podName);
            return ResponseEntity.ok(ResponseWrapper.success(logs));
        } catch (Exception e) {
            log.error("Failed to get Kubernetes logs for namespace: {}, pod: {}", namespace, podName, e);
            return ResponseEntity.badRequest().body(ResponseWrapper.error("Failed to get logs: " + e.getMessage()));
        }
    }
    
    /**
     * Get Docker logs by VM ID
     */
    @GetMapping("/docker")
    @Operation(summary = "Get Docker logs", description = "Retrieve Docker logs by VM ID.")
    public ResponseEntity<ResponseWrapper<List<UnifiedLogDTO>>> getDockerLogs(
            @Parameter(description = "VM ID") @RequestParam String vmId) {
        try {
            List<UnifiedLogDTO> logs = unifiedLogService.getLogsByVmId(vmId);
            return ResponseEntity.ok(ResponseWrapper.success(logs));
        } catch (Exception e) {
            log.error("Failed to get Docker logs for VM: {}", vmId, e);
            return ResponseEntity.badRequest().body(ResponseWrapper.error("Failed to get logs: " + e.getMessage()));
        }
    }
    
    /**
     * Get logs by time range
     */
    @GetMapping("/time-range")
    @Operation(summary = "Get logs by time range", description = "Retrieve logs within a specific time range.")
    public ResponseEntity<ResponseWrapper<List<UnifiedLogDTO>>> getLogsByTimeRange(
            @Parameter(description = "Start time (yyyy-MM-ddTHH:mm:ss)") @RequestParam String startTime,
            @Parameter(description = "End time (yyyy-MM-ddTHH:mm:ss)") @RequestParam String endTime) {
        try {
            LocalDateTime start = LocalDateTime.parse(startTime);
            LocalDateTime end = LocalDateTime.parse(endTime);
            List<UnifiedLogDTO> logs = unifiedLogService.getLogsByTimeRange(start, end);
            return ResponseEntity.ok(ResponseWrapper.success(logs));
        } catch (Exception e) {
            log.error("Failed to get logs by time range: {} to {}", startTime, endTime, e);
            return ResponseEntity.badRequest().body(ResponseWrapper.error("Failed to get logs: " + e.getMessage()));
        }
    }
    
    /**
     * Get logs with pagination
     */
    @GetMapping("/deployment/{deploymentId}/paged")
    @Operation(summary = "Get logs with pagination", description = "Retrieve logs for a specific deployment with pagination.")
    public ResponseEntity<ResponseWrapper<Page<UnifiedLogDTO>>> getLogsWithPagination(
            @Parameter(description = "Deployment ID") @PathVariable Long deploymentId,
            Pageable pageable) {
        try {
            Page<UnifiedLogDTO> logs = unifiedLogService.getLogsByDeploymentIdWithPagination(deploymentId, pageable);
            return ResponseEntity.ok(ResponseWrapper.success(logs));
        } catch (Exception e) {
            log.error("Failed to get logs with pagination for deployment: {}", deploymentId, e);
            return ResponseEntity.badRequest().body(ResponseWrapper.error("Failed to get logs: " + e.getMessage()));
        }
    }
    
    /**
     * Get log statistics by module
     */
    @GetMapping("/deployment/{deploymentId}/statistics/module")
    @Operation(summary = "Get log statistics by module", description = "Retrieve log statistics by module for a specific deployment.")
    public ResponseEntity<ResponseWrapper<List<Object[]>>> getLogStatisticsByModule(
            @Parameter(description = "Deployment ID") @PathVariable Long deploymentId) {
        try {
            List<Object[]> statistics = unifiedLogService.getLogStatisticsByModule(deploymentId);
            return ResponseEntity.ok(ResponseWrapper.success(statistics));
        } catch (Exception e) {
            log.error("Failed to get log statistics by module for deployment: {}", deploymentId, e);
            return ResponseEntity.badRequest().body(ResponseWrapper.error("Failed to get statistics: " + e.getMessage()));
        }
    }
    
    /**
     * Get log statistics by severity
     */
    @GetMapping("/deployment/{deploymentId}/statistics/severity")
    @Operation(summary = "Get log statistics by severity", description = "Retrieve log statistics by severity level for a specific deployment.")
    public ResponseEntity<ResponseWrapper<List<Object[]>>> getLogStatisticsBySeverity(
            @Parameter(description = "Deployment ID") @PathVariable Long deploymentId) {
        try {
            List<Object[]> statistics = unifiedLogService.getLogStatisticsBySeverity(deploymentId);
            return ResponseEntity.ok(ResponseWrapper.success(statistics));
        } catch (Exception e) {
            log.error("Failed to get log statistics by severity for deployment: {}", deploymentId, e);
            return ResponseEntity.badRequest().body(ResponseWrapper.error("Failed to get statistics: " + e.getMessage()));
        }
    }
    
    
    /**
     * Delete logs by deployment ID
     */
    @DeleteMapping("/deployment/{deploymentId}")
    @Operation(summary = "Delete deployment logs", description = "Delete all logs for a specific deployment.")
    public ResponseEntity<ResponseWrapper<String>> deleteLogsByDeploymentId(
            @Parameter(description = "Deployment ID") @PathVariable Long deploymentId) {
        try {
            unifiedLogService.deleteLogsByDeploymentId(deploymentId);
            return ResponseEntity.ok(ResponseWrapper.success("Logs deleted successfully"));
        } catch (Exception e) {
            log.error("Failed to delete logs for deployment: {}", deploymentId, e);
            return ResponseEntity.badRequest().body(ResponseWrapper.error("Failed to delete logs: " + e.getMessage()));
        }
    }
    
    /**
     * Get logs by application status ID
     */
    @GetMapping("/application-status/{applicationStatusId}")
    @Operation(summary = "Get logs by application status ID", description = "Retrieve all logs for a specific application status.")
    public ResponseEntity<ResponseWrapper<List<UnifiedLogDTO>>> getLogsByApplicationStatusId(
            @Parameter(description = "Application status ID") @PathVariable Long applicationStatusId) {
        try {
            List<UnifiedLogDTO> logs = unifiedLogService.getLogsByApplicationStatusId(applicationStatusId);
            return ResponseEntity.ok(ResponseWrapper.success(logs));
        } catch (Exception e) {
            log.error("Failed to get logs for application status: {}", applicationStatusId, e);
            return ResponseEntity.badRequest().body(ResponseWrapper.error("Failed to get logs: " + e.getMessage()));
        }
    }
    
    /**
     * Get logs by application status ID and module
     */
    @GetMapping("/application-status/{applicationStatusId}/module/{module}")
    @Operation(summary = "Get logs by application status ID and module", description = "Retrieve logs for a specific module of a specific application status.")
    public ResponseEntity<ResponseWrapper<List<UnifiedLogDTO>>> getLogsByApplicationStatusIdAndModule(
            @Parameter(description = "Application status ID") @PathVariable Long applicationStatusId,
            @Parameter(description = "Module (KUBERNETES, DOCKER, APPLICATION, SYSTEM)") @PathVariable String module) {
        try {
            List<UnifiedLogDTO> logs = unifiedLogService.getLogsByApplicationStatusIdAndModule(applicationStatusId, module);
            return ResponseEntity.ok(ResponseWrapper.success(logs));
        } catch (Exception e) {
            log.error("Failed to get logs for application status: {}, module: {}", applicationStatusId, module, e);
            return ResponseEntity.badRequest().body(ResponseWrapper.error("Failed to get logs: " + e.getMessage()));
        }
    }
    
    /**
     * Get logs by application status ID and severity
     */
    @GetMapping("/application-status/{applicationStatusId}/severity/{severity}")
    @Operation(summary = "Get logs by application status ID and severity", description = "Retrieve logs for a specific severity level of a specific application status.")
    public ResponseEntity<ResponseWrapper<List<UnifiedLogDTO>>> getLogsByApplicationStatusIdAndSeverity(
            @Parameter(description = "Application status ID") @PathVariable Long applicationStatusId,
            @Parameter(description = "Severity (ERROR, WARN, INFO, DEBUG)") @PathVariable String severity) {
        try {
            List<UnifiedLogDTO> logs = unifiedLogService.getLogsByApplicationStatusIdAndSeverity(applicationStatusId, severity);
            return ResponseEntity.ok(ResponseWrapper.success(logs));
        } catch (Exception e) {
            log.error("Failed to get logs for application status: {}, severity: {}", applicationStatusId, severity, e);
            return ResponseEntity.badRequest().body(ResponseWrapper.error("Failed to get logs: " + e.getMessage()));
        }
    }
    
    /**
     * Delete logs by application status ID
     */
    @DeleteMapping("/application-status/{applicationStatusId}")
    @Operation(summary = "Delete application status logs", description = "Delete all logs for a specific application status.")
    public ResponseEntity<ResponseWrapper<String>> deleteLogsByApplicationStatusId(
            @Parameter(description = "Application status ID") @PathVariable Long applicationStatusId) {
        try {
            unifiedLogService.deleteLogsByApplicationStatusId(applicationStatusId);
            return ResponseEntity.ok(ResponseWrapper.success("Application status logs deleted successfully"));
        } catch (Exception e) {
            log.error("Failed to delete logs for application status: {}", applicationStatusId, e);
            return ResponseEntity.badRequest().body(ResponseWrapper.error("Failed to delete logs: " + e.getMessage()));
        }
    }
    
    /**
     * Cleanup old logs
     */
    @DeleteMapping("/cleanup")
    @Operation(summary = "Cleanup old logs", description = "Delete logs before the specified date.")
    public ResponseEntity<ResponseWrapper<String>> cleanupOldLogs(
            @Parameter(description = "Cutoff date (yyyy-MM-ddTHH:mm:ss)") @RequestParam String cutoffDate) {
        try {
            LocalDateTime cutoff = LocalDateTime.parse(cutoffDate);
            unifiedLogService.cleanupOldLogs(cutoff);
            return ResponseEntity.ok(ResponseWrapper.success("Old logs cleaned up successfully"));
        } catch (Exception e) {
            log.error("Failed to cleanup old logs before {}", cutoffDate, e);
            return ResponseEntity.badRequest().body(ResponseWrapper.error("Failed to cleanup logs: " + e.getMessage()));
        }
    }
}
