package kr.co.mcmp.softwarecatalog.application.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.PathVariable;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/applications")
@Tag(name="Installed application", description = "Application management API for VM and K8s environments")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;
    private final ApplicationOrchestrationService applicationOrchestrationService;

    @Operation(summary = "Deploy application to VM", description = "Deploy an application to a specific VM.")
    @GetMapping("/vm/deploy")
    public ResponseEntity<ResponseWrapper<DeploymentHistory>> deployVmApplication(
            @RequestParam String namespace,@RequestParam String mciId,@RequestParam String vmId,@RequestParam Long catalogId,@RequestParam Integer servicePort,@RequestParam(required = false) String username) {
        DeploymentRequest request = DeploymentRequest.forVm(namespace, mciId, vmId, catalogId, servicePort, username);
        DeploymentHistory result = applicationOrchestrationService.deployApplication(request);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }

    @Operation(summary = "Deploy application to K8s cluster", description = "Deploy an application to a specific K8s cluster.")
    @GetMapping("/k8s/deploy")
    public ResponseEntity<ResponseWrapper<DeploymentHistory>> deployK8sApplication(
            @RequestParam String namespace,
            @RequestParam String clusterName,
            @RequestParam Long catalogId,
            @RequestParam(required = false) String username) {
        DeploymentRequest request = DeploymentRequest.forKubernetes(namespace, clusterName, catalogId, username);
        DeploymentHistory result = applicationOrchestrationService.deployApplication(request);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }

    @Operation(summary = "Check VM resources", description = "Check if there are sufficient resources to deploy an application to the VM.")
    @GetMapping("/vm/check")
    public ResponseEntity<ResponseWrapper<Boolean>> checkVmSpec(
            @RequestParam String namespace,
            @RequestParam String mciId,
            @RequestParam String vmId,
            @RequestParam Long catalogId) {
        boolean result = applicationOrchestrationService.checkSpecForVm(namespace, mciId, vmId, catalogId);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }

    @Operation(summary = "Check K8s cluster resources", description = "Check if there are sufficient resources to deploy an application to the K8s cluster.")
    @GetMapping("/k8s/check")
    public ResponseEntity<ResponseWrapper<Boolean>> checkK8sSpec(
            @RequestParam String namespace,
            @RequestParam String clusterName,
            @RequestParam Long catalogId) {
        boolean result = applicationOrchestrationService.checkSpecForK8s(namespace, clusterName, catalogId);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }

    @Operation(summary = "Get deployment history", description = "Retrieve deployment history for a specific catalog ID.")
    @GetMapping("/history")
    public ResponseEntity<ResponseWrapper<List<DeploymentHistory>>> getDeploymentHistories(@RequestParam Long catalogId, @RequestParam(required = false) String username) {
        List<DeploymentHistory> histories = applicationOrchestrationService.getDeploymentHistories(catalogId, username);
        return ResponseEntity.ok(new ResponseWrapper<>(histories));
    }

    @Operation(summary = "Get deployment logs", description = "Retrieve logs for a specific deployment.")
    @GetMapping("/logs")
    public ResponseEntity<ResponseWrapper<List<DeploymentLog>>> getDeploymentLogs(@RequestParam Long deploymentId, @RequestParam(required = false) String username) {
        List<DeploymentLog> logs = applicationOrchestrationService.getDeploymentLogs(deploymentId, username);
        return ResponseEntity.ok(new ResponseWrapper<>(logs));
    }

    @Operation(summary = "Get application status", description = "Retrieve application status for a specific catalog ID.")
    @GetMapping("/status")
    public ResponseEntity<ResponseWrapper<ApplicationStatusDto>> getLatestApplicationStatus(@RequestParam(required = false) String username) {
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
    public ResponseEntity<ResponseWrapper<Map<String, Object>>> performDockerOperation(@RequestParam ActionType operation,@RequestParam Long applicationStatusId, @RequestParam String reason, @RequestParam(required = false) String username) throws Exception {
        Map<String, Object> result = applicationOrchestrationService.performOperation(operation, applicationStatusId, reason, username);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }

    @Operation(summary = "Perform K8s application operation", description = "Perform application operations on K8s.")
    @GetMapping("/k8s/action")
    public ResponseEntity<ResponseWrapper<Map<String, Object>>> performDockerOperationForK8s(@RequestParam ActionType operation,@RequestParam Long applicationStatusId, @RequestParam String reason, @RequestParam(required = false) String username) throws Exception {
        Map<String, Object> result = applicationOrchestrationService.performOperation(operation, applicationStatusId, reason, username);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }
    
    
    // ===== Nexus Integration API Endpoints (for application deployment/operation) =====
    
    @Operation(summary = "Get application from Nexus", description = "Retrieve a specific application from Nexus.")
    @GetMapping("/nexus/application/{applicationName}")
    public ResponseEntity<ResponseWrapper<Object>> getApplicationFromNexus(@PathVariable String applicationName) {
        Object result = applicationService.getApplicationFromNexus(applicationName);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }
    
    @Operation(summary = "Get all applications from Nexus", description = "Retrieve all applications from Nexus.")
    @GetMapping("/nexus/applications")
    public ResponseEntity<ResponseWrapper<List<Object>>> getAllApplicationsFromNexus() {
        List<Object> result = applicationService.getAllApplicationsFromNexus();
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }

    // ===== Application Status/Deployment Related Query APIs =====
    
    @Operation(summary = "Get all application status", description = "Retrieve all application statuses.")
    @GetMapping("/status/all")
    public ResponseEntity<ResponseWrapper<List<ApplicationStatus>>> getAllApplicationStatus() {
        List<ApplicationStatus> result = applicationService.getAllApplicationStatus();
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }
    
    @Operation(summary = "Get application error logs", description = "Retrieve error logs for a specific application status.")
    @GetMapping("/error-logs/{applicationStatusId}")
    public ResponseEntity<ResponseWrapper<List<String>>> getApplicationErrorLogs(@PathVariable Long applicationStatusId) {
        List<String> result = applicationService.getApplicationErrorLogs(applicationStatusId);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }
    
    @Operation(summary = "Get all deployment history", description = "Retrieve all deployment history.")
    @GetMapping("/deployment-history/all")
    public ResponseEntity<ResponseWrapper<List<DeploymentHistory>>> getAllDeploymentHistory() {
        List<DeploymentHistory> result = applicationService.getAllDeploymentHistory();
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }
    
    @Operation(summary = "Get all deployment logs", description = "Retrieve all deployment logs.")
    @GetMapping("/deployment-logs/all")
    public ResponseEntity<ResponseWrapper<List<DeploymentLog>>> getAllDeploymentLogs() {
        List<DeploymentLog> result = applicationService.getAllDeploymentLogs();
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }
    
    @Operation(summary = "Get all operation history", description = "Retrieve all operation history.")
    @GetMapping("/operation-history/all")
    public ResponseEntity<ResponseWrapper<List<OperationHistory>>> getAllOperationHistory() {
        List<OperationHistory> result = applicationService.getAllOperationHistory();
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }
    
    @Operation(summary = "Get integrated application information", description = "Retrieve integrated information including status, deployment, and logs for a specific application.")
    @GetMapping("/integrated/{catalogId}")
    public ResponseEntity<ResponseWrapper<Map<String, Object>>> getIntegratedApplicationInfo(@PathVariable Long catalogId) {
        Map<String, Object> result = applicationService.getIntegratedApplicationInfo(catalogId);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }
    
}
