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

@Tag(name="tumblebug", description = "Tumblebug API queries")
@RestController
@RequestMapping("/cbtumblebug")
@RequiredArgsConstructor
public class CbtumblebugController {

    private final CbtumblebugService cbtumblebugService;

    @GetMapping("/ns")
    @Operation(summary = "Retrieve all namespaces", description = "Fetches all registered namespaces.")
    public List<NamespaceDto> getAllNamespaces() {
        return cbtumblebugService.getAllNamespaces();
    }

    @GetMapping("/ns/{nsId}/mci")
    @Operation(summary = "Retrieve MCIS for a specific namespace", description = "Fetches all MCIS belonging to the specified namespace.")
    public List<MciDto> getMicsByNamespace(@Parameter(description = "Namespace ID", required = true)
            @PathVariable String nsId) {
        return cbtumblebugService.getMcisByNamespace(nsId);
    }
    
    @GetMapping("/ns/{nsId}/mci/{mciId}")
    @Operation(summary = "Retrieve a specific MCI", description = "Fetches the specified MCI.")
    public MciDto getMicByMciId(@PathVariable String nsId, @PathVariable String mciId) {
        return cbtumblebugService.getMciByMciId(nsId, mciId);
    }
    
    @GetMapping("/ns/{nsId}/k8scluster")
    @Operation(summary = "Retrieve k8sclusters for a specific namespace", description = "Fetches all k8sclusters belonging to the specified namespace.")
    public List<K8sClusterDto> getK8sCluster(@PathVariable String nsId) {
        return cbtumblebugService.getAllK8sClusters(nsId);
    }

    @GetMapping("/ns/{nsId}/k8scluster/{clusterName}")
    @Operation(summary = "Retrieve a specific k8scluster", description = "Fetches the specified k8scluster.")
    public K8sClusterDto getK8sClusterByName(@PathVariable String nsId, @PathVariable String clusterName) {
        return cbtumblebugService.getK8sClusterByName(nsId, clusterName);
    }

}