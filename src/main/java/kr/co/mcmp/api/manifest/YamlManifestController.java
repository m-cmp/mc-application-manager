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

@Tag(name = "YamlManifestController - Yaml Generator 관련")
@RequestMapping("/manifest/v1/generator")
@RestController
@RequiredArgsConstructor
public class YamlManifestController {

    private final YamlManifestService manifestService;

    @Operation(summary = "Deployments Yaml 생성")
    @PostMapping("/yaml/deployments")
    public ResponseEntity<ResponseWrapper<String>> generateYamlDeployments(
            @RequestBody K8SDeploymentDto deployments) {
        String yaml = manifestService.generateYamlDeployments(deployments);
        return ResponseEntity.ok(new ResponseWrapper<>(yaml));
    }

    @Operation(summary = "Service Yaml 생성")
    @PostMapping("/yaml/service")
    public ResponseEntity<ResponseWrapper<String>> generateYamlService(
            @RequestBody K8SServiceDto service) {
        String yaml = manifestService.generateYamlService(service);
        return ResponseEntity.ok(new ResponseWrapper<>(yaml));
    }

    @Operation(summary = "ConfigMap Yaml 생성")
    @PostMapping("/yaml/configmap")
    public ResponseEntity<ResponseWrapper<String>> generateYamlConfigMap(
            @RequestBody K8SConfigMapDto configMap) {
        String yaml = manifestService.generateYamlConfigMap(configMap);
        return ResponseEntity.ok(new ResponseWrapper<>(yaml));
    }

    @Operation(summary = "Pod Yaml 생성")
    @PostMapping("/yaml/pod")
    public ResponseEntity<ResponseWrapper<String>> generateYamlPod(
            @RequestBody K8SPodDto pod) {
        String yaml = manifestService.generateYamlPod(pod);
        return ResponseEntity.ok(new ResponseWrapper<>(yaml));
    }

    @Operation(summary = "Hpa Yaml 생성")
    @PostMapping("/yaml/hpa")
    public ResponseEntity<ResponseWrapper<String>> generateYamlHpa(
            @RequestBody K8SHpaDto hpa) {
        String yaml = manifestService.generateYamlHpa(hpa);
        return ResponseEntity.ok(new ResponseWrapper<>(yaml));
    }
}
