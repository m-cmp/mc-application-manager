package kr.co.mcmp.api.manifest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.co.mcmp.api.manifest.k8s.*;
import kr.co.mcmp.response.ResponseWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@Tag(name = "YamlManifestController - Yaml Generator")
@RequestMapping("/manifest/v1/generator")
@RestController
@RequiredArgsConstructor
public class YamlManifestController {

    private final YamlManifestService manifestService;

    @Operation(summary = "Generate Deployments Yaml")
    @PostMapping("/yaml/deployments")
    public ResponseEntity<ResponseWrapper<String>> generateYamlDeployments(
            @Valid @RequestBody K8SDeploymentDto deployments) {
        String yaml = manifestService.generateYamlDeployments(deployments);
        return ResponseEntity.ok(new ResponseWrapper<>(yaml));
    }

    @Operation(summary = "Generate Service Yaml")
    @PostMapping("/yaml/service")
    public ResponseEntity<ResponseWrapper<String>> generateYamlService(
            @Valid @RequestBody K8SServiceDto service) {
        String yaml = manifestService.generateYamlService(service);
        return ResponseEntity.ok(new ResponseWrapper<>(yaml));
    }

    @Operation(summary = "Generate ConfigMap Yaml")
    @PostMapping("/yaml/configmap")
    public ResponseEntity<ResponseWrapper<String>> generateYamlConfigMap(
            @Valid @RequestBody K8SConfigMapDto configMap) {
        String yaml = manifestService.generateYamlConfigMap(configMap);
        return ResponseEntity.ok(new ResponseWrapper<>(yaml));
    }

    @Operation(summary = "Generate Pod Yaml")
    @PostMapping("/yaml/pod")
    public ResponseEntity<ResponseWrapper<String>> generateYamlPod(
            @Valid @RequestBody K8SPodDto pod) {
        String yaml = manifestService.generateYamlPod(pod);
        return ResponseEntity.ok(new ResponseWrapper<>(yaml));
    }

    @Operation(summary = "Generate HPA Yaml")
    @PostMapping("/yaml/hpa")
    public ResponseEntity<ResponseWrapper<String>> generateYamlHpa(
            @Valid @RequestBody K8SHpaDto hpa) {
        String yaml = manifestService.generateYamlHpa(hpa);
        return ResponseEntity.ok(new ResponseWrapper<>(yaml));
    }
}
