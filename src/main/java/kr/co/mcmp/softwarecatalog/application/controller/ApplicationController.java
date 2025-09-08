package kr.co.mcmp.softwarecatalog.application.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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
import kr.co.mcmp.softwarecatalog.application.service.ApplicationService;
import kr.co.mcmp.softwarecatalog.application.service.ApplicationOrchestrationService;
import kr.co.mcmp.softwarecatalog.application.dto.DeploymentRequest;
import kr.co.mcmp.softwarecatalog.SoftwareCatalogDTO;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/applications")
@Tag(name="Installed application ", description = "VM 및 K8s 환경의 애플리케이션 관리 API")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;
    private final ApplicationOrchestrationService applicationOrchestrationService;

    @Operation(summary = "VM에 애플리케이션 배포", description = "특정 VM에 애플리케이션을 배포합니다.")
    @GetMapping("/vm/deploy")
    public ResponseEntity<ResponseWrapper<DeploymentHistory>> deployVmApplication(
            @RequestParam String namespace,@RequestParam String mciId,@RequestParam String vmId,@RequestParam Long catalogId,@RequestParam Integer servicePort,@RequestParam(required = false) String username) {
        DeploymentRequest request = DeploymentRequest.forVm(namespace, mciId, vmId, catalogId, servicePort, username);
        DeploymentHistory result = applicationOrchestrationService.deployApplication(request);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }

    @Operation(summary = "K8s 클러스터에 애플리케이션 배포", description = "특정 K8s 클러스터에 애플리케이션을 배포합니다.")
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

    @Operation(summary = "VM 리소스 체크", description = "VM에 애플리케이션을 배포하기 위한 리소스가 충분한지 확인합니다.")
    @GetMapping("/vm/check")
    public ResponseEntity<ResponseWrapper<Boolean>> checkVmSpec(
            @RequestParam String namespace,
            @RequestParam String mciId,
            @RequestParam String vmId,
            @RequestParam Long catalogId) {
        boolean result = applicationOrchestrationService.checkSpecForVm(namespace, mciId, vmId, catalogId);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }

    @Operation(summary = "K8s 클러스터 리소스 체크", description = "K8s 클러스터에 애플리케이션을 배포하기 위한 리소스가 충분한지 확인합니다.")
    @GetMapping("/k8s/check")
    public ResponseEntity<ResponseWrapper<Boolean>> checkK8sSpec(
            @RequestParam String namespace,
            @RequestParam String clusterName,
            @RequestParam Long catalogId) {
        boolean result = applicationOrchestrationService.checkSpecForK8s(namespace, clusterName, catalogId);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }

    @Operation(summary = "배포 이력 조회", description = "특정 카탈로그 ID에 대한 배포 이력을 조회합니다.")
    @GetMapping("/history")
    public ResponseEntity<ResponseWrapper<List<DeploymentHistory>>> getDeploymentHistories(@RequestParam Long catalogId, @RequestParam(required = false) String username) {
        List<DeploymentHistory> histories = applicationOrchestrationService.getDeploymentHistories(catalogId, username);
        return ResponseEntity.ok(new ResponseWrapper<>(histories));
    }

    @Operation(summary = "배포 로그 조회", description = "특정 배포에 대한 로그를 조회합니다.")
    @GetMapping("/logs")
    public ResponseEntity<ResponseWrapper<List<DeploymentLog>>> getDeploymentLogs(@RequestParam Long deploymentId, @RequestParam(required = false) String username) {
        List<DeploymentLog> logs = applicationOrchestrationService.getDeploymentLogs(deploymentId, username);
        return ResponseEntity.ok(new ResponseWrapper<>(logs));
    }

    @Operation(summary = "애플리케이션 상태 조회", description = "특정 카탈로그 ID에 대한 애플리케이션 상태를 조회합니다.")
    @GetMapping("/status")
    public ResponseEntity<ResponseWrapper<ApplicationStatusDto>> getLatestApplicationStatus(@RequestParam(required = false) String username) {
        ApplicationStatusDto status = applicationOrchestrationService.getLatestApplicationStatus(username);
        return ResponseEntity.ok(new ResponseWrapper<>(status));
    }
    
    @Operation(summary = "어플리케이션 상태 조회", description = "어플리케이션 상태를 조회 합니다.")
    @GetMapping("/groups")
    public ResponseEntity<ResponseWrapper<List<ApplicationStatusDto>>> getApplicationGroups() {
        List<ApplicationStatusDto> list = applicationOrchestrationService.getApplicationGroups();
        return ResponseEntity.ok(new ResponseWrapper<>(list));
    }

    @Operation(summary = "VM 어플리케이션 동작", description = "어플리케이션 동작")
    @GetMapping("/vm/action")
    public ResponseEntity<ResponseWrapper<Map<String, Object>>> performDockerOperation(@RequestParam ActionType operation,@RequestParam Long applicationStatusId, @RequestParam String reason, @RequestParam(required = false) String username) throws Exception {
        Map<String, Object> result = applicationOrchestrationService.performOperation(operation, applicationStatusId, reason, username);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }

    @Operation(summary = "k8s 어플리케이션 동작", description = "어플리케이션 동작")
    @GetMapping("/k8s/action")
    public ResponseEntity<ResponseWrapper<Map<String, Object>>> performDockerOperationForK8s(@RequestParam ActionType operation,@RequestParam Long applicationStatusId, @RequestParam String reason, @RequestParam(required = false) String username) throws Exception {
        Map<String, Object> result = applicationOrchestrationService.performOperation(operation, applicationStatusId, reason, username);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }
    
    // ===== 애플리케이션 등록/수정 관련 엔드포인트 =====
    
    @Operation(summary = "소프트웨어 카탈로그 등록", description = "새로운 소프트웨어 카탈로그를 등록합니다.")
    @PostMapping("/catalog")
    public ResponseEntity<ResponseWrapper<SoftwareCatalogDTO>> registerSoftwareCatalog(
            @RequestBody SoftwareCatalogDTO catalog,
            @RequestParam(required = false) String username) {
        SoftwareCatalogDTO result = applicationService.registerSoftwareCatalog(catalog, username);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }
    
    @Operation(summary = "소프트웨어 카탈로그 수정", description = "기존 소프트웨어 카탈로그를 수정합니다.")
    @PutMapping("/catalog/{catalogId}")
    public ResponseEntity<ResponseWrapper<SoftwareCatalogDTO>> updateSoftwareCatalog(
            @PathVariable Long catalogId, 
            @RequestBody SoftwareCatalogDTO catalog,
            @RequestParam(required = false) String username) {
        SoftwareCatalogDTO result = applicationService.updateSoftwareCatalog(catalogId, catalog, username);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }
    
    @Operation(summary = "소프트웨어 카탈로그 삭제", description = "소프트웨어 카탈로그를 삭제합니다.")
    @DeleteMapping("/catalog/{catalogId}")
    public ResponseEntity<ResponseWrapper<Void>> deleteSoftwareCatalog(@PathVariable Long catalogId) {
        applicationService.deleteSoftwareCatalog(catalogId);
        return ResponseEntity.ok(new ResponseWrapper<>(null));
    }
    
    @Operation(summary = "소프트웨어 카탈로그 조회", description = "특정 소프트웨어 카탈로그를 조회합니다.")
    @GetMapping("/catalog/{catalogId}")
    public ResponseEntity<ResponseWrapper<SoftwareCatalogDTO>> getSoftwareCatalog(@PathVariable Long catalogId) {
        SoftwareCatalogDTO result = applicationService.getSoftwareCatalog(catalogId);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }
    
    @Operation(summary = "모든 소프트웨어 카탈로그 조회", description = "모든 소프트웨어 카탈로그를 조회합니다.")
    @GetMapping("/catalog")
    public ResponseEntity<ResponseWrapper<List<SoftwareCatalogDTO>>> getAllSoftwareCatalogs() {
        List<SoftwareCatalogDTO> result = applicationService.getAllSoftwareCatalogs();
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }
    
    // ===== 넥서스 연동 API 엔드포인트 =====
    
    @Operation(summary = "넥서스에서 애플리케이션 조회", description = "넥서스에서 특정 애플리케이션을 조회합니다.")
    @GetMapping("/nexus/application/{applicationName}")
    public ResponseEntity<ResponseWrapper<Object>> getApplicationFromNexus(@PathVariable String applicationName) {
        Object result = applicationService.getApplicationFromNexus(applicationName);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }
    
    @Operation(summary = "넥서스에서 모든 애플리케이션 조회", description = "넥서스에서 모든 애플리케이션을 조회합니다.")
    @GetMapping("/nexus/applications")
    public ResponseEntity<ResponseWrapper<List<Object>>> getAllApplicationsFromNexus() {
        List<Object> result = applicationService.getAllApplicationsFromNexus();
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }
    
    @Operation(summary = "넥서스에서 이미지 태그 조회", description = "넥서스에서 특정 이미지의 태그 목록을 조회합니다.")
    @GetMapping("/nexus/image/{imageName}/tags")
    public ResponseEntity<ResponseWrapper<List<String>>> getImageTagsFromNexus(@PathVariable String imageName) {
        List<String> result = applicationService.getImageTagsFromNexus(imageName);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }
    
    @Operation(summary = "넥서스에서 이미지 풀", description = "넥서스에서 이미지를 풀합니다.")
    @PostMapping("/nexus/image/pull")
    public ResponseEntity<ResponseWrapper<Object>> pullImageFromNexus(
            @RequestParam String imageName,
            @RequestParam String tag) {
        Object result = applicationService.pullImageFromNexus(imageName, tag);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }

    @Operation(summary = "카탈로그 ID로 이미지 풀", description = "카탈로그 ID를 통해 넥서스에서 이미지를 풀합니다.")
    @PostMapping("/nexus/image/pull/{catalogId}")
    public ResponseEntity<ResponseWrapper<Object>> pullImageByCatalogId(@PathVariable Long catalogId) {
        Object result = applicationService.pullImageByCatalogId(catalogId);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }
    
    @Operation(summary = "넥서스에 이미지 푸시", description = "넥서스에 이미지를 푸시합니다.")
    @PostMapping("/nexus/image/push")
    public ResponseEntity<ResponseWrapper<Object>> pushImageToNexus(
            @RequestParam String imageName,
            @RequestParam String tag,
            @RequestBody byte[] imageData) {
        Object result = applicationService.pushImageToNexus(imageName, tag, imageData);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }
    
    
}
