package kr.co.mcmp.softwarecatalog.helm;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.co.mcmp.response.ResponseWrapper;
import kr.co.mcmp.softwarecatalog.application.dto.HelmChartRegistrationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Tag(name = "Helm Chart Integration", description = "ArtifactHub Helm Chart search, registration and management")
@RestController
@Slf4j
@RequestMapping("/catalog/helm")
@RequiredArgsConstructor
public class HelmChartController {

    private final ApplicationHelmChartService helmChartService;

    @Operation(summary = "Search ArtifactHub Helm Charts", description = "Search Helm Charts from ArtifactHub.")
    @GetMapping("/search")
    public ResponseEntity<ResponseWrapper<Map<String, Object>>> searchHelmCharts(
            @RequestParam String query,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        Map<String, Object> result = helmChartService.searchHelmCharts(query, page, pageSize);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }


    @Operation(summary = "Get ArtifactHub Helm Chart versions", description = "Retrieve version list for a specific Helm Chart from ArtifactHub.")
    @GetMapping("/chart/{packageId}/versions")
    public ResponseEntity<ResponseWrapper<List<String>>> getHelmChartVersions(
            @PathVariable String packageId) {
        List<String> result = helmChartService.getHelmChartVersions(packageId);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }

    @Operation(summary = "Register Helm Chart to HELM_CHART table", description = "Register ArtifactHub Helm Chart to HELM_CHART table.")
    @PostMapping("/register")
    public ResponseEntity<ResponseWrapper<Map<String, Object>>> registerHelmChart(
            @RequestBody HelmChartRegistrationRequest request,
            @RequestParam(required = false) String username) {
        Map<String, Object> result = helmChartService.registerHelmChart(request, username);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }

    @Operation(summary = "Register Helm Chart directly from search results", description = "Register Helm Chart directly using searched package ID.")
    @PostMapping("/register/{packageId}")
    public ResponseEntity<ResponseWrapper<Map<String, Object>>> registerHelmChartFromSearch(
            @PathVariable String packageId,
            @RequestParam String chartName,
            @RequestParam String chartVersion,
            @RequestParam(defaultValue = "web") String category,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String license,
            @RequestParam(required = false) String homepage,
            @RequestParam(required = false) String repositoryUrl,
            @RequestParam(required = false) String documentationUrl,
            @RequestParam(required = false) String imageRepository) {

        // Create HelmChartRegistrationRequest object
        HelmChartRegistrationRequest request = HelmChartRegistrationRequest.builder()
                .packageId(packageId)
                .name(chartName)
                .version(chartVersion)
                .category(category)
                .description(description != null ? description : "")
                .license(license != null ? license : "Apache-2.0")
                .homepage(homepage != null ? homepage : "")
                .repository(
                        HelmChartRegistrationRequest.Repository.builder()
                                .url(repositoryUrl != null ? repositoryUrl : "")
                                .build()
                )
                .documentationUrl(documentationUrl != null ? documentationUrl : "")
                .imageRepository(imageRepository != null ? imageRepository : "")
                .build();

        Map<String, Object> result = helmChartService.registerHelmChart(request, "admin");
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }
}