package kr.co.mcmp.catalog.application.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;
import kr.co.mcmp.catalog.application.dto.K8sApplicationHistoryDTO;
import kr.co.mcmp.catalog.application.dto.VmApplicationHistoryDTO;
import kr.co.mcmp.catalog.application.service.ApplicationService;

@RestController
@RequestMapping("/applications")
@Tag(name="Installed application ", description="Vm, k8s 환경 설치된 Application 목록을 조회 합니다.")
public class ApplicationController {

    @Autowired
    private ApplicationService applicationService;

     /**
     * 특정 VM의 설치된 애플리케이션 기록 조회
     * @param namespace
     * @param mciName
     * @param vmName
     * @return uninstall 상태가 아닌 애플리케이션 기록 목록
     */
    @GetMapping("/vm")
    public ResponseEntity<List<VmApplicationHistoryDTO>> getActiveVmApplications(
            @RequestParam String namespace,
            @RequestParam String mciName,
            @RequestParam String vmName) {
        List<VmApplicationHistoryDTO> histories = applicationService.getActiveVmApplicationHistory(namespace, mciName, vmName);
        return ResponseEntity.ok(histories);
    }

    /**
     * 특정 K8s 클러스터의 설치된 애플리케이션 기록 조회
     * @param namespace
     * @param clusterName
     * @return uninstall 상태가 아닌 애플리케이션 기록 목록
     */
    @GetMapping("/k8s")
    public ResponseEntity<List<K8sApplicationHistoryDTO>> getActiveK8sApplications(
            @RequestParam String namespace,
            @RequestParam String clusterName) {
        List<K8sApplicationHistoryDTO> histories = applicationService.getActiveK8sApplicationHistory(namespace, clusterName);
        return ResponseEntity.ok(histories);
    }

    @GetMapping("/vm/check/application")
    public boolean canInstallApplicationForVM(@RequestParam String namespace,
    @RequestParam String mciName,
    @RequestParam String vmName,
    @RequestParam Integer catalogId) {
        return applicationService.canInstallApplicationOnVm(namespace, mciName, vmName, catalogId);
    }

    @GetMapping("/k8s/check/application")
    public boolean canInstallApplicationForK8s(@RequestParam String namespace,
    @RequestParam String clusterName,
    @RequestParam Integer catalogId) {
        return applicationService.canInstallApplicationOnK8s(namespace, clusterName, catalogId);
    }
    
    
}
