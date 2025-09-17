package kr.co.mcmp.softwarecatalog.controller;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.co.mcmp.response.ResponseWrapper;
import kr.co.mcmp.softwarecatalog.dto.SelectBoxOptionDTO;
import lombok.extern.log4j.Log4j2;

@Tag(name = "SelectBox Options", description = "SelectBox option retrieval API")
@RestController
@RequestMapping("/catalog/selectbox")
@Log4j2
public class SelectBoxController {

    @Operation(summary = "Get SelectBox Options", description = "Retrieve SelectBox option list by type.")
    @GetMapping("/options")
    public ResponseEntity<ResponseWrapper<List<SelectBoxOptionDTO>>> getSelectBoxOptions(
            @RequestParam String type) {
        
        List<SelectBoxOptionDTO> options = new ArrayList<>();
        
        switch (type.toLowerCase()) {
            case "reason":
                options = getReasonOptions();
                break;
            case "restart_reason":
                options = getRestartReasonOptions();
                break;
            case "stop_reason":
                options = getStopReasonOptions();
                break;
            case "category":
                options = getCategoryOptions();
                break;
            default:
                log.warn("Unknown selectbox type: {}", type);
                break;
        }
        
        return ResponseEntity.ok(new ResponseWrapper<>(options));
    }
    
    private List<SelectBoxOptionDTO> getReasonOptions() {
        List<SelectBoxOptionDTO> options = new ArrayList<>();
        options.add(SelectBoxOptionDTO.builder()
                .value("performance_issues")
                .label("Performance Issues")
                .description("Operations due to performance problems")
                .build());
        options.add(SelectBoxOptionDTO.builder()
                .value("security_vulnerabilities")
                .label("Security Vulnerabilities")
                .description("Operations due to security vulnerabilities")
                .build());
        options.add(SelectBoxOptionDTO.builder()
                .value("cost_optimization")
                .label("Cost Optimization")
                .description("Operations for cost optimization")
                .build());
        options.add(SelectBoxOptionDTO.builder()
                .value("maintenance")
                .label("Maintenance")
                .description("Regular maintenance operations")
                .build());
        options.add(SelectBoxOptionDTO.builder()
                .value("user_request")
                .label("User Request")
                .description("Operations requested by users")
                .build());
        options.add(SelectBoxOptionDTO.builder()
                .value("system_error")
                .label("System Error")
                .description("Operations due to system errors")
                .build());
        options.add(SelectBoxOptionDTO.builder()
                .value("resource_constraints")
                .label("Resource Constraints")
                .description("Operations due to resource limitations")
                .build());
        options.add(SelectBoxOptionDTO.builder()
                .value("compliance_requirements")
                .label("Compliance Requirements")
                .description("Operations for compliance requirements")
                .build());
        return options;
    }
    
    private List<SelectBoxOptionDTO> getRestartReasonOptions() {
        List<SelectBoxOptionDTO> options = new ArrayList<>();
        options.add(SelectBoxOptionDTO.builder()
                .value("performance_issue")
                .label("Performance Issue")
                .description("Restart due to performance issues")
                .build());
        options.add(SelectBoxOptionDTO.builder()
                .value("memory_leak_detected")
                .label("Memory Leak Detected")
                .description("Restart due to detected memory leak")
                .build());
        options.add(SelectBoxOptionDTO.builder()
                .value("configuration_update")
                .label("Configuration Update")
                .description("Restart due to configuration changes")
                .build());
        options.add(SelectBoxOptionDTO.builder()
                .value("scheduled_maintenance")
                .label("Scheduled Maintenance")
                .description("Restart for scheduled maintenance")
                .build());
        options.add(SelectBoxOptionDTO.builder()
                .value("user_request")
                .label("User Request")
                .description("Restart requested by user")
                .build());
        options.add(SelectBoxOptionDTO.builder()
                .value("application_crash")
                .label("Application Crash")
                .description("Restart due to application crash")
                .build());
        options.add(SelectBoxOptionDTO.builder()
                .value("security_patch")
                .label("Security Patch")
                .description("Restart to apply security patch")
                .build());
        options.add(SelectBoxOptionDTO.builder()
                .value("resource_exhaustion")
                .label("Resource Exhaustion")
                .description("Restart due to resource exhaustion")
                .build());
        return options;
    }
    
    private List<SelectBoxOptionDTO> getStopReasonOptions() {
        List<SelectBoxOptionDTO> options = new ArrayList<>();
        options.add(SelectBoxOptionDTO.builder()
                .value("resource_optimization")
                .label("Resource Optimization")
                .description("Stop for resource optimization")
                .build());
        options.add(SelectBoxOptionDTO.builder()
                .value("maintenance_window")
                .label("Maintenance Window")
                .description("Stop during maintenance window")
                .build());
        options.add(SelectBoxOptionDTO.builder()
                .value("security_update_required")
                .label("Security Update Required")
                .description("Stop for security updates")
                .build());
        options.add(SelectBoxOptionDTO.builder()
                .value("application_not_needed")
                .label("Application Not Needed")
                .description("Application no longer needed")
                .build());
        options.add(SelectBoxOptionDTO.builder()
                .value("performance_degradation")
                .label("Performance Degradation")
                .description("Stop due to performance degradation")
                .build());
        options.add(SelectBoxOptionDTO.builder()
                .value("cost_reduction")
                .label("Cost Reduction")
                .description("Stop for cost reduction")
                .build());
        options.add(SelectBoxOptionDTO.builder()
                .value("system_shutdown")
                .label("System Shutdown")
                .description("Stop due to system shutdown")
                .build());
        options.add(SelectBoxOptionDTO.builder()
                .value("emergency_stop")
                .label("Emergency Stop")
                .description("Emergency stop due to critical issues")
                .build());
        return options;
    }
    
    private List<SelectBoxOptionDTO> getCategoryOptions() {
        List<SelectBoxOptionDTO> options = new ArrayList<>();
        options.add(SelectBoxOptionDTO.builder()
                .value("Performance")
                .label("Performance")
                .description("Performance-related evaluation")
                .build());
        options.add(SelectBoxOptionDTO.builder()
                .value("Security")
                .label("Security")
                .description("Security-related evaluation")
                .build());
        options.add(SelectBoxOptionDTO.builder()
                .value("Usability")
                .label("Usability")
                .description("Usability-related evaluation")
                .build());
        options.add(SelectBoxOptionDTO.builder()
                .value("Reliability")
                .label("Reliability")
                .description("Reliability-related evaluation")
                .build());
        options.add(SelectBoxOptionDTO.builder()
                .value("Support")
                .label("Support")
                .description("Support-related evaluation")
                .build());
        options.add(SelectBoxOptionDTO.builder()
                .value("Documentation")
                .label("Documentation")
                .description("Documentation quality evaluation")
                .build());
        options.add(SelectBoxOptionDTO.builder()
                .value("Community")
                .label("Community")
                .description("Community support evaluation")
                .build());
        options.add(SelectBoxOptionDTO.builder()
                .value("Scalability")
                .label("Scalability")
                .description("Scalability evaluation")
                .build());
        return options;
    }
}