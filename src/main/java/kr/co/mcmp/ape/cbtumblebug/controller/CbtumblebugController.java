package kr.co.mcmp.ape.cbtumblebug.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.co.mcmp.ape.cbtumblebug.dto.K8sClusterDto;
import kr.co.mcmp.ape.cbtumblebug.dto.MciDto;
import kr.co.mcmp.ape.cbtumblebug.dto.NamespaceDto;
import kr.co.mcmp.ape.cbtumblebug.service.CbtumblebugService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestParam;


@Tag(name="tumblebug", description = "tumblebug API 조회")
@RestController
@RequestMapping("/cbtumblebug")
@RequiredArgsConstructor
public class CbtumblebugController {

    private final CbtumblebugService cbtumblebugService;

    @GetMapping("/ns")
    @Operation(summary = "모든 네임스페이스 조회", description = "시스템에 등록된 모든 네임스페이스를 조회합니다.")
    public List<NamespaceDto> getAllNamespaces() {
        return cbtumblebugService.getAllNamespaces();
    }

    @GetMapping("/ns/{nsId}/mci")
    @Operation(summary = "특정 네임스페이스의 MCIS 조회", description = "지정된 네임스페이스에 속한 모든 MCIS를 조회합니다.")
    public List<MciDto> getMicsByNamespace(@Parameter(description = "네임스페이스 ID", required = true)
            @PathVariable String nsId) {
        return cbtumblebugService.getMcisByNamespace(nsId);
    }
    
    // @GetMapping("/k8sCluster/info")
    // @Operation(summary = "k8sCluster 정보 조회", description = "등록된 모든 K8s Cluster의 정보를 조회합니다.")
    // public String getAllK8sClusterInfo() {
    //     return cbtumblebugService.getK8sClusterInfo();
    // }
    
    @GetMapping("/ns/{nsId}/k8scluster")
    public List<K8sClusterDto> getK8sCluster(@PathVariable String nsId) {
        return cbtumblebugService.getAllK8sClusters(nsId);
    }

    @GetMapping("/ns/{nsId}/k8scluster/{clusterName}")
    public K8sClusterDto getK8sClusterByName(@PathVariable String nsId, @PathVariable String clusterName) {
        return cbtumblebugService.getK8sClusterByName(nsId, clusterName);
    }

}
