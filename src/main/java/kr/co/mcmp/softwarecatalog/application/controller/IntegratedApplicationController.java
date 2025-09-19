package kr.co.mcmp.softwarecatalog.application.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import kr.co.mcmp.response.ResponseWrapper;
import kr.co.mcmp.softwarecatalog.application.service.ApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 통합 애플리케이션 정보 관련 API 컨트롤러
 */
@RestController
@RequestMapping("/api/applications/integrated")
@RequiredArgsConstructor
@Slf4j
public class IntegratedApplicationController {

    private final ApplicationService applicationService;

    @Operation(summary = "Get integrated application information by catalog ID", description = "Retrieve integrated information including status, deployment, and logs for a specific application by catalog ID.")
    @GetMapping("/catalog/{catalogId}")
    public ResponseEntity<ResponseWrapper<Map<String, Object>>> getIntegratedApplicationInfo(
            @Parameter(description = "Catalog ID to get integrated information for", required = true, example = "123") @PathVariable Long catalogId) {
        Map<String, Object> result = applicationService.getIntegratedApplicationInfo(catalogId);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }
    
    @Operation(summary = "Get integrated application information by deployment ID", description = "Retrieve integrated information including status, deployment, and logs for a specific deployment.")
    @GetMapping("/deployment/{deploymentId}")
    public ResponseEntity<ResponseWrapper<Map<String, Object>>> getIntegratedApplicationInfoByDeploymentId(
            @Parameter(description = "Deployment ID to get integrated information for", required = true, example = "1") @PathVariable Long deploymentId) {
        Map<String, Object> result = applicationService.getIntegratedApplicationInfoByDeploymentId(deploymentId);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }

    @Operation(summary = "Update Ingress configuration", description = "Update Ingress settings for a specific software catalog.")
    @PutMapping("/catalog/{catalogId}/ingress")
    public ResponseEntity<ResponseWrapper<String>> updateIngressConfiguration(
            @Parameter(description = "Catalog ID to update Ingress for", required = true, example = "1") @PathVariable Long catalogId,
            @Parameter(description = "Ingress configuration", required = true) @RequestBody Map<String, Object> ingressConfig) {
        String result = applicationService.updateIngressConfiguration(catalogId, ingressConfig);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }
}
