package kr.co.mcmp.ape.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.co.mcmp.ape.cbtumblebug.dto.MciDto;
import kr.co.mcmp.ape.cbtumblebug.dto.NamespaceDto;
import kr.co.mcmp.ape.dto.reqDto.JenkinsJobDto;
import kr.co.mcmp.ape.dto.resDto.ApeLogResDto;
import kr.co.mcmp.ape.service.AppProvEngineService;
import kr.co.mcmp.response.ResponseWrapper;
import lombok.RequiredArgsConstructor;



@Tag(name="APE", description = "APE 관리")
@RestController
@RequestMapping("/ape")
@RequiredArgsConstructor
public class AppProvEngineController {

    private final AppProvEngineService appProvEngineService;

    @Operation(summary = "APE 로그 조회")
    @GetMapping("/log/{jobName}")
    public ResponseWrapper<List<ApeLogResDto>> getApeLog(@PathVariable String jobName) {
        return new ResponseWrapper<>(appProvEngineService.getApeLog(jobName));
    }

    @PostMapping("/vm/install")
    @Operation(summary = "Install VM Application", description = "VM에 애플리케이션을 설치하기 위해 Jenkins 작업을 트리거합니다.")
    public ResponseWrapper<String> triggerVmInstall(@RequestBody JenkinsJobDto.VmApplicationInstall jobDto) {
        return triggerJenkinsJob(jobDto);
    }

    @PostMapping("/vm/uninstall")
    @Operation(summary = "Uninstall VM Application", description = "VM에서 애플리케이션을 제거하기 위해 Jenkins 작업을 트리거합니다.")
    public ResponseWrapper<String> triggerVmUninstall(@RequestBody JenkinsJobDto.VmApplicationUninstall jobDto) {
        return triggerJenkinsJob(jobDto);
    }

    @PostMapping("/helm/install")
    @Operation(summary = "Install Kubernetes Helm Chart", description = "Kubernetes에 Helm 차트를 설치하기 위해 Jenkins 작업을 트리거합니다.")
    public ResponseWrapper<String> triggerHelmInstall(@RequestBody JenkinsJobDto.KubernetesHelmInstall jobDto) {
        return triggerJenkinsJob(jobDto);
    }

    @PostMapping("/helm/uninstall")
    @Operation(summary = "Uninstall Kubernetes Helm Release", description = "Kubernetes에서 Helm 릴리스를 제거하기 위해 Jenkins 작업을 트리거합니다.")
    public ResponseWrapper<String> triggerHelmUninstall(@RequestBody JenkinsJobDto.KubernetesHelmUninstall jobDto) {
        return triggerJenkinsJob(jobDto);
    }

    private ResponseWrapper<String> triggerJenkinsJob(JenkinsJobDto jobDto) {
        try {
            String jobId = appProvEngineService.triggerJenkinsJob(jobDto);
            return new ResponseWrapper<>("Jenkins job triggered. Job ID: " + jobId);
        } catch (Exception e) {
            return new ResponseWrapper("Failed to trigger job: " + e.getMessage());
        }
    }

    @GetMapping("/job/status/{jobId}")
    public ResponseWrapper<String> getJobStatus(@PathVariable String jobId) {
        try {
            String status = appProvEngineService.getJobStatus(jobId);
            return new ResponseWrapper<>(status);
        } catch (Exception e) {
            return new ResponseWrapper<>("Failed to get job status: " + e.getMessage());
        }
    }

    @GetMapping("/ns")
    @Operation(summary = "모든 네임스페이스 조회", description = "시스템에 등록된 모든 네임스페이스를 조회합니다.")
    public List<NamespaceDto> getAllNamespaces() {
        // return appProvEngineService.getAllNamespaces();
        return null;
    }

    @GetMapping("/ns/{nsId}/mcis")
    @Operation(summary = "특정 네임스페이스의 MCIS 조회", description = "지정된 네임스페이스에 속한 모든 MCIS를 조회합니다.")
    public List<MciDto> getMCISByNamespace(@Parameter(description = "네임스페이스 ID", required = true)
            @PathVariable String nsId) {
        // return appProvEngineService.getMcisByNamespace(nsId);
        return null;
    }
    
}
    
