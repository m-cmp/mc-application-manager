package kr.co.mcmp.ape.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.co.mcmp.ape.dto.reqDto.JenkinsJobDto;
import kr.co.mcmp.ape.dto.resDto.ApeLogResDto;
import kr.co.mcmp.ape.service.AppProvEngineService;
import kr.co.mcmp.response.ResponseCode;
import kr.co.mcmp.response.ResponseWrapper;
import lombok.RequiredArgsConstructor;




@Tag(name="APE(Application provisioning Engine)", description = "Application install and uninstall for VM or K8s environment")
@RestController
@RequestMapping("/ape")
@RequiredArgsConstructor
/**
 * @deprecated Ape not used
 */
@Deprecated
public class AppProvEngineController {

    private final AppProvEngineService appProvEngineService;

    @Operation(summary = "Get APE logs")
    @GetMapping("/log/{jobName}")
    public ResponseWrapper<List<ApeLogResDto>> getApeLog(@PathVariable String jobName) {
        return new ResponseWrapper<>(appProvEngineService.getApeLog(jobName));
    }

    @PostMapping("/vm/install")
    @Operation(summary = "Install VM Application", description = "Trigger Jenkins job to install application on VM.")
    public ResponseWrapper<String> triggerVmInstall(@RequestBody JenkinsJobDto.VmApplicationInstall jobDto) {
        return triggerJenkinsJob(jobDto);
    }

    @PostMapping("/vm/uninstall")
    @Operation(summary = "Uninstall VM Application", description = "Trigger Jenkins job to uninstall application from VM.")
    public ResponseWrapper<String> triggerVmUninstall(@RequestBody JenkinsJobDto.VmApplicationUninstall jobDto) {
        return triggerJenkinsJob(jobDto);
    }

    @PostMapping("/helm/install")
    @Operation(summary = "Install Kubernetes Helm Chart", description = "Trigger Jenkins job to install Helm chart on Kubernetes.")
    public ResponseWrapper<String> triggerHelmInstall(@RequestBody JenkinsJobDto.HelmChartInstall jobDto) {
        return triggerJenkinsJob(jobDto);
    }

    @PostMapping("/helm/uninstall")
    @Operation(summary = "Uninstall Kubernetes Helm Release", description = "Trigger Jenkins job to uninstall Helm release from Kubernetes.")
    public ResponseWrapper<String> triggerHelmUninstall(@RequestBody JenkinsJobDto.HelmChartUninstall jobDto) {
        return triggerJenkinsJob(jobDto);
    }

    private ResponseWrapper<String> triggerJenkinsJob(JenkinsJobDto jobDto) {
        try {
            String jobId = appProvEngineService.triggerJenkinsJob(jobDto);
            return new ResponseWrapper<>(ResponseCode.OK, "Jenkins job triggered. Job ID: " + jobId);
        } catch (Exception e) {
            return new ResponseWrapper(ResponseCode.UNKNOWN_ERROR, "Failed to trigger job: " + e.getMessage());
        }
    }

    // @GetMapping("/job/status/{jobId}")
    // public ResponseWrapper<String> getJobStatus(@PathVariable String jobId) {
    //     try {
    //         String status = appProvEngineService.getJobStatus(jobId);
    //         return new ResponseWrapper<>(status);
    //     } catch (Exception e) {
    //         return new ResponseWrapper<>("Failed to get job status: " + e.getMessage());
    //     }
    // }

    
}
    
