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
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/applications")
@Tag(name="Installed application ", description = "VM 및 K8s 환경의 애플리케이션 관리 API")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;

    @Operation(summary = "VM에 애플리케이션 배포", description = "특정 VM에 애플리케이션을 배포합니다.")
    @GetMapping("/vm/deploy")
    public ResponseEntity<ResponseWrapper<DeploymentHistory>> deployVmApplication(
            @RequestParam String namespace,@RequestParam String mciId,@RequestParam String vmId,@RequestParam Long catalogId,@RequestParam Integer servicePort,@RequestParam(required = false) String username) {
        DeploymentHistory result = applicationService.deployApplication(namespace, mciId, vmId, catalogId, servicePort, username);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }

    @Operation(summary = "K8s 클러스터에 애플리케이션 배포", description = "특정 K8s 클러스터에 애플리케이션을 배포합니다.")
    @GetMapping("/k8s/deploy")
    public ResponseEntity<ResponseWrapper<DeploymentHistory>> deployK8sApplication(
            @RequestParam String namespace,
            @RequestParam String clusterName,
            @RequestParam Long catalogId,
            @RequestParam(required = false) String username) {
        DeploymentHistory result = applicationService.deployApplicationToK8s(namespace, clusterName, catalogId, username);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }

    @Operation(summary = "VM 리소스 체크", description = "VM에 애플리케이션을 배포하기 위한 리소스가 충분한지 확인합니다.")
    @GetMapping("/vm/check")
    public ResponseEntity<ResponseWrapper<Boolean>> checkVmSpec(
            @RequestParam String namespace,
            @RequestParam String mciId,
            @RequestParam String vmId,
            @RequestParam Long catalogId) {
        boolean result = applicationService.checkSpecForVm(namespace, mciId, vmId, catalogId);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }

    @Operation(summary = "K8s 클러스터 리소스 체크", description = "K8s 클러스터에 애플리케이션을 배포하기 위한 리소스가 충분한지 확인합니다.")
    @GetMapping("/k8s/check")
    public ResponseEntity<ResponseWrapper<Boolean>> checkK8sSpec(
            @RequestParam String namespace,
            @RequestParam String clusterName,
            @RequestParam Long catalogId) {
        boolean result = applicationService.checkSpecForK8s(namespace, clusterName, catalogId);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }

    @Operation(summary = "배포 이력 조회", description = "특정 카탈로그 ID에 대한 배포 이력을 조회합니다.")
    @GetMapping("/history")
    public ResponseEntity<ResponseWrapper<List<DeploymentHistory>>> getDeploymentHistories(@RequestParam Long catalogId, @RequestParam(required = false) String username) {
        List<DeploymentHistory> histories = applicationService.getDeploymentHistories(catalogId, username);
        return ResponseEntity.ok(new ResponseWrapper<>(histories));
    }

    @Operation(summary = "배포 로그 조회", description = "특정 배포에 대한 로그를 조회합니다.")
    @GetMapping("/logs")
    public ResponseEntity<ResponseWrapper<List<DeploymentLog>>> getDeploymentLogs(@RequestParam Long deploymentId, @RequestParam(required = false) String username) {
        List<DeploymentLog> logs = applicationService.getDeploymentLogs(deploymentId, username);
        return ResponseEntity.ok(new ResponseWrapper<>(logs));
    }

    @Operation(summary = "애플리케이션 상태 조회", description = "특정 카탈로그 ID에 대한 애플리케이션 상태를 조회합니다.")
    @GetMapping("/status")
    public ResponseEntity<ResponseWrapper<ApplicationStatusDto>> getLatestApplicationStatus(@RequestParam(required = false) String username) {
        ApplicationStatusDto status = applicationService.getLatestApplicationStatus(username);
        return ResponseEntity.ok(new ResponseWrapper<>(status));
    }
    
    @Operation(summary = "VM 어플리케이션 상태 조회", description = "어플리케이션 상태를 조회 합니다.")
    @GetMapping("/groups")
    public ResponseEntity<ResponseWrapper<List<ApplicationStatusDto>>> getApplicationGroups() {
        List<ApplicationStatusDto> list = applicationService.getApplicationGroups();
        return ResponseEntity.ok(new ResponseWrapper<>(list));
    }

    @Operation(summary = "VM 어플리케이션 동작", description = "어플리케이션 동작")
    @GetMapping("/vm/action")
    public ResponseEntity<ResponseWrapper<Map<String, Object>>> performDockerOperation(@RequestParam ActionType operation,@RequestParam Long applicationStatusId, @RequestParam String reason, @RequestParam(required = false) String username) throws Exception {
        Map<String, Object> result =  applicationService.performDockerOperation(operation, applicationStatusId,reason,username);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }

    @Operation(summary = "k8s 어플리케이션 동작", description = "어플리케이션 동작")
    @GetMapping("/k8s/action")
    public ResponseEntity<ResponseWrapper<Map<String, Object>>> performDockerOperationForK8s(@RequestParam ActionType operation,@RequestParam Long applicationStatusId, @RequestParam String reason, @RequestParam(required = false) String username) throws Exception {
        Map<String, Object> result =  applicationService.performDockerOperationForK8s(operation, applicationStatusId,reason,username);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }
    
    
}
