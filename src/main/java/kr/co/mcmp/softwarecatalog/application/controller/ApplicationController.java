package kr.co.mcmp.softwarecatalog.application.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.co.mcmp.response.ResponseWrapper;
import kr.co.mcmp.softwarecatalog.application.dto.ApplicationOperationRequest;
import kr.co.mcmp.softwarecatalog.application.dto.ApplicationStatusDto;
import kr.co.mcmp.softwarecatalog.application.dto.DeploymentHistoryDTO;
import kr.co.mcmp.softwarecatalog.application.dto.DeploymentLogDTO;
import kr.co.mcmp.softwarecatalog.application.dto.IntegratedApplicationInfoDTO;
import kr.co.mcmp.softwarecatalog.application.dto.K8sStorageClassDTO;
import kr.co.mcmp.softwarecatalog.application.dto.K8sIngressCheckRequest;
import kr.co.mcmp.softwarecatalog.application.dto.K8sIngressCheckResult;
import kr.co.mcmp.softwarecatalog.application.dto.ObjectStorageSmokeTestRequest;
import kr.co.mcmp.softwarecatalog.application.dto.ObjectStorageSmokeTestResponse;
import kr.co.mcmp.softwarecatalog.application.dto.RegisteredObjectStorageDTO;
import kr.co.mcmp.softwarecatalog.application.model.DeploymentHistory;
import kr.co.mcmp.softwarecatalog.application.service.ApplicationService;
import kr.co.mcmp.softwarecatalog.application.service.ApplicationOrchestrationService;
import kr.co.mcmp.softwarecatalog.application.service.ObjectStorageSmokeTestService;
import kr.co.mcmp.softwarecatalog.application.service.ObjectStorageRegistryService;
import kr.co.mcmp.softwarecatalog.application.dto.DeploymentRequest;
import kr.co.mcmp.softwarecatalog.application.dto.DeploymentRequestDTO;
import kr.co.mcmp.softwarecatalog.application.constants.DeploymentType;
import kr.co.mcmp.softwarecatalog.kubernetes.service.KubernetesStorageClassService;
import kr.co.mcmp.softwarecatalog.kubernetes.service.KubernetesIngressPreflightService;
import kr.co.mcmp.softwarecatalog.kubernetes.service.NhnStorageClassService;
import kr.co.mcmp.security.project.ProjectScopeAuthorizationService;
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
    private final ObjectStorageSmokeTestService objectStorageSmokeTestService;
    private final ObjectStorageRegistryService objectStorageRegistryService;
    private final KubernetesStorageClassService kubernetesStorageClassService;
    private final NhnStorageClassService nhnStorageClassService;
    private final ProjectScopeAuthorizationService projectScopeAuthorizationService;
    private final KubernetesIngressPreflightService kubernetesIngressPreflightService;

    @Operation(summary = "Deploy application to VM", description = "Deploy an application to a specific VM.")
    @PostMapping("/vm/deploy")
    public ResponseEntity<ResponseWrapper<DeploymentHistoryDTO>> deployVmApplication(
            @Parameter(description = "Deployment request for VM", required = true) @RequestBody DeploymentRequestDTO requestDTO,
            HttpServletRequest httpRequest) {

        projectScopeAuthorizationService.authorizeNamespace(httpRequest, requestDTO.getNamespace());

        // VM 배포 타입 설정
        requestDTO.setDeploymentType(DeploymentType.VM);
        
        DeploymentRequest request = requestDTO.toDeploymentRequest();
        DeploymentHistory result = applicationOrchestrationService.deployApplication(request);
        return ResponseEntity.ok(new ResponseWrapper<>(new DeploymentHistoryDTO(result)));
    }

    @Operation(summary = "Deploy application to K8s cluster", description = "Deploy an application to a specific K8s cluster.")
    @PostMapping("/k8s/deploy")
    public ResponseEntity<ResponseWrapper<DeploymentHistoryDTO>> deployK8sApplication(
            @Parameter(description = "Deployment request for K8s", required = true) @RequestBody DeploymentRequestDTO requestDTO,
            HttpServletRequest httpRequest) {

        projectScopeAuthorizationService.authorizeNamespace(httpRequest, requestDTO.getNamespace());

        // K8s 배포 타입 설정
        requestDTO.setDeploymentType(DeploymentType.K8S);
        
        DeploymentRequest request = requestDTO.toDeploymentRequest();
        DeploymentHistory result = applicationOrchestrationService.deployApplication(request);
        return ResponseEntity.ok(new ResponseWrapper<>(new DeploymentHistoryDTO(result)));
    }

    @Operation(summary = "Check VM resources", description = "Check if there are sufficient resources to deploy an application to the VM.")
    @GetMapping("/vm/check")
    public ResponseEntity<ResponseWrapper<Boolean>> checkVmSpec(
            @Parameter(description = "Namespace for resource check", required = true, example = "default") @RequestParam String namespace,
            @Parameter(description = "MCIS (Multi-Cloud Infrastructure Service) ID", required = true, example = "mci-001") @RequestParam String mciId,
            @Parameter(description = "Virtual Machine ID", required = true, example = "vm-001") @RequestParam String vmId,
            @Parameter(description = "Catalog ID of the application to check", required = true, example = "123") @RequestParam Long catalogId,
            HttpServletRequest httpRequest) {
        projectScopeAuthorizationService.authorizeNamespace(httpRequest, namespace);
        boolean result = applicationOrchestrationService.checkSpecForVm(namespace, mciId, vmId, catalogId);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }

    @Operation(summary = "Check K8s cluster resources", description = "Check if there are sufficient resources to deploy an application to the K8s cluster.")
    @GetMapping("/k8s/check")
    public ResponseEntity<ResponseWrapper<Boolean>> checkK8sSpec(
            @Parameter(description = "CB-Tumblebug namespace for resource check", required = true, example = "default") @RequestParam String namespace,
            @Parameter(description = "Kubernetes cluster name", required = true, example = "cluster-001") @RequestParam String clusterName,
            @Parameter(description = "Catalog ID of the application to check", required = true, example = "123") @RequestParam Long catalogId,
            HttpServletRequest httpRequest) {
        projectScopeAuthorizationService.authorizeNamespace(httpRequest, namespace);
        boolean result = applicationOrchestrationService.checkSpecForK8s(namespace, clusterName, catalogId);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }

    @Operation(summary = "Check K8s Ingress before deployment", description = "Validate current form values and Host/Path conflicts. TLS readiness issues are warnings, not resource-spec overrides.")
    @PostMapping("/k8s/ingress/check")
    public ResponseEntity<ResponseWrapper<K8sIngressCheckResult>> checkK8sIngress(
            @Valid @RequestBody K8sIngressCheckRequest request, HttpServletRequest httpRequest) {
        projectScopeAuthorizationService.authorizeNamespace(httpRequest, request.getNamespace());
        return ResponseEntity.ok(new ResponseWrapper<>(kubernetesIngressPreflightService.check(request)));
    }

    @GetMapping("/k8s/ingress/tls-settings")
    @Operation(summary = "Discover IBM managed HTTPS domains", description = "Read-only domain hints. No certificate data or private keys are returned.")
    public ResponseEntity<ResponseWrapper<kr.co.mcmp.softwarecatalog.application.dto.K8sIngressTlsSettings>> ingressTlsSettings(
            @RequestParam String namespace, @RequestParam String clusterName, HttpServletRequest httpRequest) {
        projectScopeAuthorizationService.authorizeNamespace(httpRequest, namespace);
        return ResponseEntity.ok(new ResponseWrapper<>(kubernetesIngressPreflightService.tlsSettings(namespace, clusterName)));
    }

    @Operation(summary = "Check S3-compatible Object Storage", description = "Smoke check Object Storage settings for applications that declare object-storage capability.")
    @PostMapping("/k8s/object-storage/smoke-check")
    public ResponseEntity<ResponseWrapper<ObjectStorageSmokeTestResponse>> checkObjectStorage(
            @Parameter(description = "Object Storage smoke check request", required = true)
            @RequestBody ObjectStorageSmokeTestRequest request,
            HttpServletRequest httpRequest) {
        projectScopeAuthorizationService.authorizeNamespace(httpRequest, request.getNamespace());
        ObjectStorageSmokeTestResponse result = objectStorageSmokeTestService.runSmokeTest(request);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }

    @Operation(
            summary = "Check registered Object Storage for a VM application",
            description = "Verify listing and presigned URL support through Tumblebug before deploying JupyterLab.")
    @PostMapping("/vm/object-storage/smoke-check")
    public ResponseEntity<ResponseWrapper<ObjectStorageSmokeTestResponse>> checkVmObjectStorage(
            @Parameter(description = "Object Storage smoke check request", required = true)
            @RequestBody ObjectStorageSmokeTestRequest request,
            HttpServletRequest httpRequest) {
        projectScopeAuthorizationService.authorizeNamespace(httpRequest, request.getNamespace());
        ObjectStorageSmokeTestResponse result = objectStorageSmokeTestService.runSmokeTest(request);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }

    @Operation(
            summary = "List registered Object Storage resources",
            description = "List Object Storage resources registered in Tumblebug for the selected namespace.")
    @GetMapping("/object-storages")
    public ResponseEntity<ResponseWrapper<List<RegisteredObjectStorageDTO>>> getObjectStorages(
            @RequestParam String namespace,
            HttpServletRequest httpRequest) {
        projectScopeAuthorizationService.authorizeNamespace(httpRequest, namespace);
        return ResponseEntity.ok(new ResponseWrapper<>(objectStorageRegistryService.list(namespace)));
    }

    @Operation(summary = "List K8s StorageClasses", description = "Retrieve StorageClasses from the selected K8s cluster.")
    @GetMapping("/k8s/storage-classes")
    public ResponseEntity<ResponseWrapper<List<K8sStorageClassDTO>>> getK8sStorageClasses(
            @Parameter(description = "Namespace used to locate the K8s cluster", required = true) @RequestParam String namespace,
            @Parameter(description = "Kubernetes cluster name", required = true) @RequestParam String clusterName,
            HttpServletRequest httpRequest) {
        projectScopeAuthorizationService.authorizeNamespace(httpRequest, namespace);
        List<K8sStorageClassDTO> result = kubernetesStorageClassService.getStorageClasses(namespace, clusterName);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }

    @GetMapping("/k8s/storage-classes/nhn-capability")
    public ResponseEntity<?> nhnStorageCapability(@RequestParam String namespace, @RequestParam String clusterName, HttpServletRequest httpRequest) {
        projectScopeAuthorizationService.authorizeNamespace(httpRequest, namespace);
        return ResponseEntity.ok(new ResponseWrapper<>(nhnStorageClassService.capability(namespace, clusterName)));
    }

    @PostMapping("/k8s/storage-classes/nhn")
    public ResponseEntity<?> createNhnStorageClass(@RequestParam String namespace, @RequestParam String clusterName,
            @RequestBody NhnStorageClassService.CreateRequest body, HttpServletRequest httpRequest) {
        projectScopeAuthorizationService.authorizeNamespace(httpRequest, namespace);
        return ResponseEntity.ok(new ResponseWrapper<>(nhnStorageClassService.create(namespace, clusterName, body)));
    }

    @Operation(summary = "Get deployment history", description = "Retrieve deployment history for a specific catalog ID.")
    @GetMapping("/history")
    public ResponseEntity<ResponseWrapper<List<DeploymentHistoryDTO>>> getDeploymentHistories(
            @Parameter(description = "Catalog ID to get deployment history for", required = true, example = "123") @RequestParam Long catalogId, 
            @Parameter(description = "Username filter (optional)", example = "admin") @RequestParam(required = false) String username,
            HttpServletRequest httpRequest) {
        String namespace = projectScopeAuthorizationService.getAuthorizedNamespace(httpRequest);
        List<DeploymentHistoryDTO> histories = applicationOrchestrationService.getDeploymentHistories(catalogId, username).stream()
                .filter(history -> namespace.isBlank() || namespace.equals(history.getNamespace()))
                .map(DeploymentHistoryDTO::new)
                .toList();
        return ResponseEntity.ok(new ResponseWrapper<>(histories));
    }

    @Operation(summary = "Get deployment logs", description = "Retrieve logs for a specific deployment.")
    @GetMapping("/logs")
    public ResponseEntity<ResponseWrapper<List<DeploymentLogDTO>>> getDeploymentLogs(
            @Parameter(description = "Deployment ID to get logs for", required = true, example = "456") @RequestParam Long deploymentId, 
            @Parameter(description = "Username filter (optional)", example = "admin") @RequestParam(required = false) String username,
            HttpServletRequest httpRequest) {
        projectScopeAuthorizationService.authorizeDeployment(httpRequest, deploymentId);
        List<DeploymentLogDTO> logs = applicationOrchestrationService.getDeploymentLogs(deploymentId, username).stream()
                .map(DeploymentLogDTO::new)
                .toList();
        return ResponseEntity.ok(new ResponseWrapper<>(logs));
    }

    @Operation(summary = "Get application status", description = "Retrieve application status for a specific catalog ID.")
    @GetMapping("/status")
    public ResponseEntity<ResponseWrapper<ApplicationStatusDto>> getLatestApplicationStatus(
            @Parameter(description = "Username filter (optional)", example = "admin") @RequestParam(required = false) String username,
            HttpServletRequest httpRequest) {
        String namespace = projectScopeAuthorizationService.getAuthorizedNamespace(httpRequest);
        ApplicationStatusDto status = namespace.isBlank()
                ? applicationOrchestrationService.getLatestApplicationStatus(username)
                : applicationOrchestrationService.getLatestApplicationStatus(username, namespace);
        return ResponseEntity.ok(new ResponseWrapper<>(status));
    }
    
    @Operation(summary = "Get application groups", description = "Retrieve application groups.")
    @GetMapping("/groups")
    public ResponseEntity<ResponseWrapper<List<ApplicationStatusDto>>> getApplicationGroups(
            @RequestParam(required = false) String namespace,
            HttpServletRequest httpRequest) {
        String scopedNamespace = namespace == null || namespace.isBlank()
                ? projectScopeAuthorizationService.getAuthorizedNamespace(httpRequest)
                : projectScopeAuthorizationService.authorizeNamespace(httpRequest, namespace);
        List<ApplicationStatusDto> list = scopedNamespace.isBlank()
                ? applicationOrchestrationService.getApplicationGroups()
                : applicationOrchestrationService.getApplicationGroups(scopedNamespace);
        return ResponseEntity.ok(new ResponseWrapper<>(list));
    }

    @Operation(summary = "Perform application operation", description = "Perform application operations on VM or K8s.")
    @PostMapping("/action")
    public ResponseEntity<ResponseWrapper<Map<String, Object>>> performApplicationOperation(
            @Parameter(description = "Application operation request", required = true) @RequestBody @Valid ApplicationOperationRequest request,
            HttpServletRequest httpRequest) throws Exception {
        projectScopeAuthorizationService.authorizeApplicationStatus(httpRequest, request.getApplicationStatusId());
        Map<String, Object> result = applicationOrchestrationService.performOperation(
            request.getOperation(), 
            request.getApplicationStatusId(), 
            request.getReason(), 
            request.getDetailReason(),
            request.getUsername()
        );
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
    
    @Operation(summary = "Get integrated application information by deployment ID", description = "Retrieve integrated information including status, deployment, and logs for a specific deployment.")
    @GetMapping("/integrated/deployment/{deploymentId}")
    public ResponseEntity<ResponseWrapper<IntegratedApplicationInfoDTO>> getIntegratedApplicationInfo(
            @Parameter(description = "Deployment ID to get integrated information for", required = true, example = "1") @PathVariable Long deploymentId,
            HttpServletRequest httpRequest) {
        projectScopeAuthorizationService.authorizeDeployment(httpRequest, deploymentId);
        try {
            IntegratedApplicationInfoDTO result = applicationService.getIntegratedApplicationInfoByDeploymentIdAsDTO(deploymentId);
            return ResponseEntity.ok(new ResponseWrapper<>(result));
        } catch (Exception e) {
            log.error("Failed to get integrated application info for deployment: {}", deploymentId, e);
            return ResponseEntity.badRequest().body(new ResponseWrapper<>(null, "Failed to get integrated application info: " + e.getMessage()));
        }
    }

    
}
