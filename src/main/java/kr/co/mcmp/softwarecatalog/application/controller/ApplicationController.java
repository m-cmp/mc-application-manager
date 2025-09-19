package kr.co.mcmp.softwarecatalog.application.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.co.mcmp.response.ResponseWrapper;
import kr.co.mcmp.softwarecatalog.application.constants.ActionType;
import kr.co.mcmp.softwarecatalog.application.dto.ApplicationStatusDto;
import kr.co.mcmp.softwarecatalog.application.model.DeploymentHistory;
import kr.co.mcmp.softwarecatalog.application.model.DeploymentLog;
import kr.co.mcmp.softwarecatalog.application.model.ApplicationStatus;
import kr.co.mcmp.softwarecatalog.application.model.OperationHistory;
import kr.co.mcmp.softwarecatalog.application.service.ApplicationService;
import kr.co.mcmp.softwarecatalog.application.service.ApplicationOrchestrationService;
import kr.co.mcmp.softwarecatalog.application.dto.DeploymentRequest;
import kr.co.mcmp.softwarecatalog.application.dto.DeploymentRequestDTO;
import kr.co.mcmp.softwarecatalog.application.constants.DeploymentType;
import org.springframework.web.bind.annotation.PathVariable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/applications")
@Tag(name="Installed application", description = "Application management API for VM and K8s environments")
@RequiredArgsConstructor
@Slf4j
public class ApplicationController {

    private final ApplicationService applicationService;
    private final ApplicationOrchestrationService applicationOrchestrationService;

    @Operation(summary = "Deploy application to VM", description = "Deploy an application to a specific VM.")
    @PostMapping("/vm/deploy")
    public ResponseEntity<ResponseWrapper<DeploymentHistory>> deployVmApplication(
            @Parameter(description = "Deployment request for VM", required = true) @RequestBody DeploymentRequestDTO requestDTO) {

        // VM 배포 타입 설정
        requestDTO.setDeploymentType(DeploymentType.VM);
        
        DeploymentRequest request = requestDTO.toDeploymentRequest();
        DeploymentHistory result = applicationOrchestrationService.deployApplication(request);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }

    @Operation(summary = "Deploy application to K8s cluster", description = "Deploy an application to a specific K8s cluster.")
    @PostMapping("/k8s/deploy")
    public ResponseEntity<ResponseWrapper<DeploymentHistory>> deployK8sApplication(
            @Parameter(description = "Deployment request for K8s", required = true) @RequestBody DeploymentRequestDTO requestDTO) {

        // K8s 배포 타입 설정
        requestDTO.setDeploymentType(DeploymentType.K8S);
        
        DeploymentRequest request = requestDTO.toDeploymentRequest();
        DeploymentHistory result = applicationOrchestrationService.deployApplication(request);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }

    @Operation(summary = "Check VM resources", description = "Check if there are sufficient resources to deploy an application to the VM.")
    @GetMapping("/vm/check")
    public ResponseEntity<ResponseWrapper<Boolean>> checkVmSpec(
            @Parameter(description = "Namespace for resource check", required = true, example = "default") @RequestParam String namespace,
            @Parameter(description = "MCIS (Multi-Cloud Infrastructure Service) ID", required = true, example = "mci-001") @RequestParam String mciId,
            @Parameter(description = "Virtual Machine ID", required = true, example = "vm-001") @RequestParam String vmId,
            @Parameter(description = "Catalog ID of the application to check", required = true, example = "123") @RequestParam Long catalogId) {
        boolean result = applicationOrchestrationService.checkSpecForVm(namespace, mciId, vmId, catalogId);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }

    @Operation(summary = "Check K8s cluster resources", description = "Check if there are sufficient resources to deploy an application to the K8s cluster.")
    @GetMapping("/k8s/check")
    public ResponseEntity<ResponseWrapper<Boolean>> checkK8sSpec(
            @Parameter(description = "Kubernetes namespace for resource check", required = true, example = "default") @RequestParam String namespace,
            @Parameter(description = "Kubernetes cluster name", required = true, example = "cluster-001") @RequestParam String clusterName,
            @Parameter(description = "Catalog ID of the application to check", required = true, example = "123") @RequestParam Long catalogId) {
        boolean result = applicationOrchestrationService.checkSpecForK8s(namespace, clusterName, catalogId);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }

    @Operation(summary = "Get deployment history", description = "Retrieve deployment history for a specific catalog ID.")
    @GetMapping("/history")
    public ResponseEntity<ResponseWrapper<List<DeploymentHistory>>> getDeploymentHistories(
            @Parameter(description = "Catalog ID to get deployment history for", required = true, example = "123") @RequestParam Long catalogId, 
            @Parameter(description = "Username filter (optional)", example = "admin") @RequestParam(required = false) String username) {
        List<DeploymentHistory> histories = applicationOrchestrationService.getDeploymentHistories(catalogId, username);
        return ResponseEntity.ok(new ResponseWrapper<>(histories));
    }

    @Operation(summary = "Get deployment logs", description = "Retrieve logs for a specific deployment.")
    @GetMapping("/logs")
    public ResponseEntity<ResponseWrapper<List<DeploymentLog>>> getDeploymentLogs(
            @Parameter(description = "Deployment ID to get logs for", required = true, example = "456") @RequestParam Long deploymentId, 
            @Parameter(description = "Username filter (optional)", example = "admin") @RequestParam(required = false) String username) {
        List<DeploymentLog> logs = applicationOrchestrationService.getDeploymentLogs(deploymentId, username);
        return ResponseEntity.ok(new ResponseWrapper<>(logs));
    }

    @Operation(summary = "Get application status", description = "Retrieve application status for a specific catalog ID.")
    @GetMapping("/status")
    public ResponseEntity<ResponseWrapper<ApplicationStatusDto>> getLatestApplicationStatus(
            @Parameter(description = "Username filter (optional)", example = "admin") @RequestParam(required = false) String username) {
        ApplicationStatusDto status = applicationOrchestrationService.getLatestApplicationStatus(username);
        return ResponseEntity.ok(new ResponseWrapper<>(status));
    }
    
    @Operation(summary = "Get application groups", description = "Retrieve application groups.")
    @GetMapping("/groups")
    public ResponseEntity<ResponseWrapper<List<ApplicationStatusDto>>> getApplicationGroups() {
        List<ApplicationStatusDto> list = applicationOrchestrationService.getApplicationGroups();
        return ResponseEntity.ok(new ResponseWrapper<>(list));
    }

    @Operation(summary = "Perform VM application operation", description = "Perform application operations on VM.")
    @GetMapping("/vm/action")
    public ResponseEntity<ResponseWrapper<Map<String, Object>>> performDockerOperation(
            @Parameter(description = "Operation type to perform", required = true, example = "START") @RequestParam ActionType operation,
            @Parameter(description = "Application status ID", required = true, example = "789") @RequestParam Long applicationStatusId, 
            @Parameter(description = "Reason for the operation", required = true, example = "Scheduled maintenance") @RequestParam String reason, 
            @Parameter(description = "Username performing the operation (optional)", example = "admin") @RequestParam(required = false) String username) throws Exception {
        Map<String, Object> result = applicationOrchestrationService.performOperation(operation, applicationStatusId, reason, username);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }

    @Operation(summary = "Perform K8s application operation", description = "Perform application operations on K8s.")
    @GetMapping("/k8s/action")
    public ResponseEntity<ResponseWrapper<Map<String, Object>>> performDockerOperationForK8s(
            @Parameter(description = "Operation type to perform", required = true, example = "START") @RequestParam ActionType operation,
            @Parameter(description = "Application status ID", required = true, example = "789") @RequestParam Long applicationStatusId, 
            @Parameter(description = "Reason for the operation", required = true, example = "Scheduled maintenance") @RequestParam String reason, 
            @Parameter(description = "Username performing the operation (optional)", example = "admin") @RequestParam(required = false) String username) throws Exception {
        Map<String, Object> result = applicationOrchestrationService.performOperation(operation, applicationStatusId, reason, username);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }
    
    
    // ===== Nexus Integration API Endpoints (for application deployment/operation) =====
    
    @Operation(summary = "Get application from Nexus", description = "Retrieve a specific application from Nexus.")
    @GetMapping("/nexus/application/{applicationName}")
    public ResponseEntity<ResponseWrapper<Object>> getApplicationFromNexus(
            @Parameter(description = "Application name to retrieve from Nexus", required = true, example = "nginx") @PathVariable String applicationName) {
        Object result = applicationService.getApplicationFromNexus(applicationName);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }
    
    @Operation(summary = "Get all applications from Nexus", description = "Retrieve all applications from Nexus.")
    @GetMapping("/nexus/applications")
    public ResponseEntity<ResponseWrapper<List<Object>>> getAllApplicationsFromNexus() {
        List<Object> result = applicationService.getAllApplicationsFromNexus();
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }

    
}
