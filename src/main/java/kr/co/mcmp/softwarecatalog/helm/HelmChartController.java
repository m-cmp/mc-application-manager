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
import lombok.extern.log4j.Log4j2;

@Tag(name = "Helm Chart Integration", description = "ArtifactHub Helm Chart 검색, 등록 및 관리")
@RestController
@Log4j2
@RequestMapping("/catalog/helm")
@RequiredArgsConstructor
public class HelmChartController {

    private final ApplicationHelmChartService helmChartService;

    @Operation(summary = "ArtifactHub Helm Chart 검색", description = "ArtifactHub에서 Helm Chart를 검색합니다.")
    @GetMapping("/search")
    public ResponseEntity<ResponseWrapper<Map<String, Object>>> searchHelmCharts(
            @RequestParam String query,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        Map<String, Object> result = helmChartService.searchHelmCharts(query, page, pageSize);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }

    @Operation(summary = "ArtifactHub Helm Chart 상세 정보 조회", description = "ArtifactHub에서 특정 Helm Chart의 상세 정보를 조회합니다.")
    @GetMapping("/chart/{packageId}")
    public ResponseEntity<ResponseWrapper<Map<String, Object>>> getHelmChartDetails(
            @PathVariable String packageId) {
        Map<String, Object> result = helmChartService.getHelmChartDetails(packageId);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }

    @Operation(summary = "ArtifactHub Helm Chart 버전 목록 조회", description = "ArtifactHub에서 특정 Helm Chart의 버전 목록을 조회합니다.")
    @GetMapping("/chart/{packageId}/versions")
    public ResponseEntity<ResponseWrapper<List<String>>> getHelmChartVersions(
            @PathVariable String packageId) {
        List<String> result = helmChartService.getHelmChartVersions(packageId);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }

    @Operation(summary = "Helm Chart를 HELM_CHART 테이블에 등록", description = "ArtifactHub Helm Chart를 HELM_CHART 테이블에 등록합니다.")
    @PostMapping("/register")
    public ResponseEntity<ResponseWrapper<Map<String, Object>>> registerHelmChart(
            @RequestBody HelmChartRegistrationRequest request,
            @RequestParam(required = false) String username) {
        Map<String, Object> result = helmChartService.registerHelmChart(request, username);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }
}