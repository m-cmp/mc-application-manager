package kr.co.mcmp.softwarecatalog.application.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import kr.co.mcmp.response.ResponseWrapper;
import kr.co.mcmp.softwarecatalog.application.dto.ApplicationStatusDto;
import kr.co.mcmp.softwarecatalog.application.model.ApplicationStatus;
import kr.co.mcmp.softwarecatalog.application.service.ApplicationOrchestrationService;
import kr.co.mcmp.softwarecatalog.application.service.ApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 애플리케이션 상태 관련 API 컨트롤러
 */
@RestController
@RequestMapping("/api/applications/status")
@RequiredArgsConstructor
@Slf4j
public class ApplicationStatusController {

    private final ApplicationService applicationService;
    private final ApplicationOrchestrationService applicationOrchestrationService;

    @Operation(summary = "Get all application status", description = "Retrieve all application statuses.")
    @GetMapping("/all")
    public ResponseEntity<ResponseWrapper<List<ApplicationStatus>>> getAllApplicationStatus() {
        List<ApplicationStatus> result = applicationService.getAllApplicationStatus();
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }
    
    @Operation(summary = "Get application error logs", description = "Retrieve error logs for a specific application status.")
    @GetMapping("/error-logs/{applicationStatusId}")
    public ResponseEntity<ResponseWrapper<List<String>>> getApplicationErrorLogs(
            @Parameter(description = "Application status ID to get error logs for", required = true, example = "789") @PathVariable Long applicationStatusId) {
        List<String> result = applicationService.getApplicationErrorLogs(applicationStatusId);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }
    
    @Operation(summary = "Get application groups", description = "Retrieve application groups.")
    @GetMapping("/groups")
    public ResponseEntity<ResponseWrapper<List<ApplicationStatusDto>>> getApplicationGroups() {
        List<ApplicationStatusDto> list = applicationOrchestrationService.getApplicationGroups();
        return ResponseEntity.ok(new ResponseWrapper<>(list));
    }
    
    @Operation(summary = "Get latest application status", description = "Retrieve latest application status for a specific user.")
    @GetMapping("/latest")
    public ResponseEntity<ResponseWrapper<ApplicationStatusDto>> getLatestApplicationStatus(
            @Parameter(description = "Username filter (optional)", example = "admin") @RequestParam(required = false) String username) {
        ApplicationStatusDto status = applicationOrchestrationService.getLatestApplicationStatus(username);
        return ResponseEntity.ok(new ResponseWrapper<>(status));
    }
}
