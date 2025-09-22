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
 * 통합 로그 컨트롤러
 */
@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Unified Log Management", description = "통합 로그 관리 API")
public class UnifiedLogController {
    
    private final UnifiedLogService unifiedLogService;
    
    /**
     * 배포 ID로 로그 조회
     */
    @GetMapping("/deployment/{deploymentId}")
    @Operation(summary = "배포 ID로 로그 조회", description = "특정 배포의 모든 로그를 조회합니다.")
    public ResponseEntity<ResponseWrapper<List<UnifiedLogDTO>>> getLogsByDeploymentId(
            @Parameter(description = "배포 ID") @PathVariable Long deploymentId) {
        try {
            List<UnifiedLogDTO> logs = unifiedLogService.getLogsByDeploymentId(deploymentId);
            return ResponseEntity.ok(ResponseWrapper.success(logs));
        } catch (Exception e) {
            log.error("Failed to get logs for deployment: {}", deploymentId, e);
            return ResponseEntity.badRequest().body(ResponseWrapper.error("Failed to get logs: " + e.getMessage()));
        }
    }
    
    /**
     * 배포 ID와 모듈로 로그 조회
     */
    @GetMapping("/deployment/{deploymentId}/module/{module}")
    @Operation(summary = "배포 ID와 모듈로 로그 조회", description = "특정 배포의 특정 모듈 로그를 조회합니다.")
    public ResponseEntity<ResponseWrapper<List<UnifiedLogDTO>>> getLogsByDeploymentIdAndModule(
            @Parameter(description = "배포 ID") @PathVariable Long deploymentId,
            @Parameter(description = "모듈 (KUBERNETES, DOCKER, APPLICATION, SYSTEM)") @PathVariable String module) {
        try {
            List<UnifiedLogDTO> logs = unifiedLogService.getLogsByDeploymentIdAndModule(deploymentId, module);
            return ResponseEntity.ok(ResponseWrapper.success(logs));
        } catch (Exception e) {
            log.error("Failed to get logs for deployment: {}, module: {}", deploymentId, module, e);
            return ResponseEntity.badRequest().body(ResponseWrapper.error("Failed to get logs: " + e.getMessage()));
        }
    }
    
    /**
     * 배포 ID와 심각도로 로그 조회
     */
    @GetMapping("/deployment/{deploymentId}/severity/{severity}")
    @Operation(summary = "배포 ID와 심각도로 로그 조회", description = "특정 배포의 특정 심각도 로그를 조회합니다.")
    public ResponseEntity<ResponseWrapper<List<UnifiedLogDTO>>> getLogsByDeploymentIdAndSeverity(
            @Parameter(description = "배포 ID") @PathVariable Long deploymentId,
            @Parameter(description = "심각도 (ERROR, WARN, INFO, DEBUG)") @PathVariable String severity) {
        try {
            List<UnifiedLogDTO> logs = unifiedLogService.getLogsByDeploymentIdAndSeverity(deploymentId, severity);
            return ResponseEntity.ok(ResponseWrapper.success(logs));
        } catch (Exception e) {
            log.error("Failed to get logs for deployment: {}, severity: {}", deploymentId, severity, e);
            return ResponseEntity.badRequest().body(ResponseWrapper.error("Failed to get logs: " + e.getMessage()));
        }
    }
    
    /**
     * 네임스페이스와 파드 이름으로 로그 조회
     */
    @GetMapping("/kubernetes")
    @Operation(summary = "쿠버네티스 로그 조회", description = "네임스페이스와 파드 이름으로 쿠버네티스 로그를 조회합니다.")
    public ResponseEntity<ResponseWrapper<List<UnifiedLogDTO>>> getKubernetesLogs(
            @Parameter(description = "네임스페이스") @RequestParam String namespace,
            @Parameter(description = "파드 이름") @RequestParam String podName) {
        try {
            List<UnifiedLogDTO> logs = unifiedLogService.getLogsByNamespaceAndPodName(namespace, podName);
            return ResponseEntity.ok(ResponseWrapper.success(logs));
        } catch (Exception e) {
            log.error("Failed to get Kubernetes logs for namespace: {}, pod: {}", namespace, podName, e);
            return ResponseEntity.badRequest().body(ResponseWrapper.error("Failed to get logs: " + e.getMessage()));
        }
    }
    
    /**
     * VM ID로 도커 로그 조회
     */
    @GetMapping("/docker")
    @Operation(summary = "도커 로그 조회", description = "VM ID로 도커 로그를 조회합니다.")
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
     * 시간 범위로 로그 조회
     */
    @GetMapping("/time-range")
    @Operation(summary = "시간 범위로 로그 조회", description = "시작 시간과 종료 시간으로 로그를 조회합니다.")
    public ResponseEntity<ResponseWrapper<List<UnifiedLogDTO>>> getLogsByTimeRange(
            @Parameter(description = "시작 시간 (yyyy-MM-ddTHH:mm:ss)") @RequestParam String startTime,
            @Parameter(description = "종료 시간 (yyyy-MM-ddTHH:mm:ss)") @RequestParam String endTime) {
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
     * 페이지네이션으로 로그 조회
     */
    @GetMapping("/deployment/{deploymentId}/paged")
    @Operation(summary = "페이지네이션으로 로그 조회", description = "특정 배포의 로그를 페이지네이션으로 조회합니다.")
    public ResponseEntity<ResponseWrapper<Page<UnifiedLogDTO>>> getLogsWithPagination(
            @Parameter(description = "배포 ID") @PathVariable Long deploymentId,
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
     * 로그 통계 조회 (모듈별)
     */
    @GetMapping("/deployment/{deploymentId}/statistics/module")
    @Operation(summary = "모듈별 로그 통계", description = "특정 배포의 모듈별 로그 통계를 조회합니다.")
    public ResponseEntity<ResponseWrapper<List<Object[]>>> getLogStatisticsByModule(
            @Parameter(description = "배포 ID") @PathVariable Long deploymentId) {
        try {
            List<Object[]> statistics = unifiedLogService.getLogStatisticsByModule(deploymentId);
            return ResponseEntity.ok(ResponseWrapper.success(statistics));
        } catch (Exception e) {
            log.error("Failed to get log statistics by module for deployment: {}", deploymentId, e);
            return ResponseEntity.badRequest().body(ResponseWrapper.error("Failed to get statistics: " + e.getMessage()));
        }
    }
    
    /**
     * 로그 통계 조회 (심각도별)
     */
    @GetMapping("/deployment/{deploymentId}/statistics/severity")
    @Operation(summary = "심각도별 로그 통계", description = "특정 배포의 심각도별 로그 통계를 조회합니다.")
    public ResponseEntity<ResponseWrapper<List<Object[]>>> getLogStatisticsBySeverity(
            @Parameter(description = "배포 ID") @PathVariable Long deploymentId) {
        try {
            List<Object[]> statistics = unifiedLogService.getLogStatisticsBySeverity(deploymentId);
            return ResponseEntity.ok(ResponseWrapper.success(statistics));
        } catch (Exception e) {
            log.error("Failed to get log statistics by severity for deployment: {}", deploymentId, e);
            return ResponseEntity.badRequest().body(ResponseWrapper.error("Failed to get statistics: " + e.getMessage()));
        }
    }
    
    /**
     * 쿠버네티스 로그 수집
     */
    // @PostMapping("/collect/kubernetes")
    // @Operation(summary = "쿠버네티스 로그 수집", description = "쿠버네티스에서 로그를 수집합니다.")
    // public ResponseEntity<ResponseWrapper<String>> collectKubernetesLogs(
    //         @Parameter(description = "배포 ID") @RequestParam Long deploymentId,
    //         @Parameter(description = "네임스페이스") @RequestParam String namespace,
    //         @Parameter(description = "파드 이름") @RequestParam String podName,
    //         @Parameter(description = "컨테이너 이름") @RequestParam(required = false) String containerName,
    //         @Parameter(description = "클러스터 이름") @RequestParam(required = false) String clusterName) {
    //     try {
    //         unifiedLogService.collectKubernetesLogs(deploymentId, namespace, podName, containerName, clusterName);
    //         return ResponseEntity.ok(ResponseWrapper.success("Kubernetes logs collected successfully"));
    //     } catch (Exception e) {
    //         log.error("Failed to collect Kubernetes logs for deployment: {}", deploymentId, e);
    //         return ResponseEntity.badRequest().body(ResponseWrapper.error("Failed to collect logs: " + e.getMessage()));
    //     }
    // }
    
    /**
     * 도커 로그 수집
     */
    // @PostMapping("/collect/docker")
    // @Operation(summary = "도커 로그 수집", description = "도커에서 로그를 수집합니다.")
    // public ResponseEntity<ResponseWrapper<String>> collectDockerLogs(
    //         @Parameter(description = "배포 ID") @RequestParam Long deploymentId,
    //         @Parameter(description = "VM ID") @RequestParam String vmId,
    //         @Parameter(description = "컨테이너 이름") @RequestParam(required = false) String containerName) {
    //     try {
    //         unifiedLogService.collectDockerLogs(deploymentId, vmId, containerName);
    //         return ResponseEntity.ok(ResponseWrapper.success("Docker logs collected successfully"));
    //     } catch (Exception e) {
    //         log.error("Failed to collect Docker logs for deployment: {}", deploymentId, e);
    //         return ResponseEntity.badRequest().body(ResponseWrapper.error("Failed to collect logs: " + e.getMessage()));
    //     }
    // }
    
    /**
     * 배포 ID로 로그 삭제
     */
    @DeleteMapping("/deployment/{deploymentId}")
    @Operation(summary = "배포 로그 삭제", description = "특정 배포의 모든 로그를 삭제합니다.")
    public ResponseEntity<ResponseWrapper<String>> deleteLogsByDeploymentId(
            @Parameter(description = "배포 ID") @PathVariable Long deploymentId) {
        try {
            unifiedLogService.deleteLogsByDeploymentId(deploymentId);
            return ResponseEntity.ok(ResponseWrapper.success("Logs deleted successfully"));
        } catch (Exception e) {
            log.error("Failed to delete logs for deployment: {}", deploymentId, e);
            return ResponseEntity.badRequest().body(ResponseWrapper.error("Failed to delete logs: " + e.getMessage()));
        }
    }
    
    /**
     * 애플리케이션 상태 ID로 로그 조회
     */
    @GetMapping("/application-status/{applicationStatusId}")
    @Operation(summary = "애플리케이션 상태 ID로 로그 조회", description = "특정 애플리케이션 상태의 모든 로그를 조회합니다.")
    public ResponseEntity<ResponseWrapper<List<UnifiedLogDTO>>> getLogsByApplicationStatusId(
            @Parameter(description = "애플리케이션 상태 ID") @PathVariable Long applicationStatusId) {
        try {
            List<UnifiedLogDTO> logs = unifiedLogService.getLogsByApplicationStatusId(applicationStatusId);
            return ResponseEntity.ok(ResponseWrapper.success(logs));
        } catch (Exception e) {
            log.error("Failed to get logs for application status: {}", applicationStatusId, e);
            return ResponseEntity.badRequest().body(ResponseWrapper.error("Failed to get logs: " + e.getMessage()));
        }
    }
    
    /**
     * 애플리케이션 상태 ID와 모듈로 로그 조회
     */
    @GetMapping("/application-status/{applicationStatusId}/module/{module}")
    @Operation(summary = "애플리케이션 상태 ID와 모듈로 로그 조회", description = "특정 애플리케이션 상태의 특정 모듈 로그를 조회합니다.")
    public ResponseEntity<ResponseWrapper<List<UnifiedLogDTO>>> getLogsByApplicationStatusIdAndModule(
            @Parameter(description = "애플리케이션 상태 ID") @PathVariable Long applicationStatusId,
            @Parameter(description = "모듈 (KUBERNETES, DOCKER, APPLICATION, SYSTEM)") @PathVariable String module) {
        try {
            List<UnifiedLogDTO> logs = unifiedLogService.getLogsByApplicationStatusIdAndModule(applicationStatusId, module);
            return ResponseEntity.ok(ResponseWrapper.success(logs));
        } catch (Exception e) {
            log.error("Failed to get logs for application status: {}, module: {}", applicationStatusId, module, e);
            return ResponseEntity.badRequest().body(ResponseWrapper.error("Failed to get logs: " + e.getMessage()));
        }
    }
    
    /**
     * 애플리케이션 상태 ID와 심각도로 로그 조회
     */
    @GetMapping("/application-status/{applicationStatusId}/severity/{severity}")
    @Operation(summary = "애플리케이션 상태 ID와 심각도로 로그 조회", description = "특정 애플리케이션 상태의 특정 심각도 로그를 조회합니다.")
    public ResponseEntity<ResponseWrapper<List<UnifiedLogDTO>>> getLogsByApplicationStatusIdAndSeverity(
            @Parameter(description = "애플리케이션 상태 ID") @PathVariable Long applicationStatusId,
            @Parameter(description = "심각도 (ERROR, WARN, INFO, DEBUG)") @PathVariable String severity) {
        try {
            List<UnifiedLogDTO> logs = unifiedLogService.getLogsByApplicationStatusIdAndSeverity(applicationStatusId, severity);
            return ResponseEntity.ok(ResponseWrapper.success(logs));
        } catch (Exception e) {
            log.error("Failed to get logs for application status: {}, severity: {}", applicationStatusId, severity, e);
            return ResponseEntity.badRequest().body(ResponseWrapper.error("Failed to get logs: " + e.getMessage()));
        }
    }
    
    /**
     * 애플리케이션 상태 ID로 로그 삭제
     */
    @DeleteMapping("/application-status/{applicationStatusId}")
    @Operation(summary = "애플리케이션 상태 로그 삭제", description = "특정 애플리케이션 상태의 모든 로그를 삭제합니다.")
    public ResponseEntity<ResponseWrapper<String>> deleteLogsByApplicationStatusId(
            @Parameter(description = "애플리케이션 상태 ID") @PathVariable Long applicationStatusId) {
        try {
            unifiedLogService.deleteLogsByApplicationStatusId(applicationStatusId);
            return ResponseEntity.ok(ResponseWrapper.success("Application status logs deleted successfully"));
        } catch (Exception e) {
            log.error("Failed to delete logs for application status: {}", applicationStatusId, e);
            return ResponseEntity.badRequest().body(ResponseWrapper.error("Failed to delete logs: " + e.getMessage()));
        }
    }
    
    /**
     * 오래된 로그 정리
     */
    @DeleteMapping("/cleanup")
    @Operation(summary = "오래된 로그 정리", description = "지정된 날짜 이전의 로그를 삭제합니다.")
    public ResponseEntity<ResponseWrapper<String>> cleanupOldLogs(
            @Parameter(description = "컷오프 날짜 (yyyy-MM-ddTHH:mm:ss)") @RequestParam String cutoffDate) {
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
