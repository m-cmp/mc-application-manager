package kr.co.mcmp.softwarecatalog.helm;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.co.mcmp.softwarecatalog.CatalogService;
import kr.co.mcmp.softwarecatalog.SoftwareCatalogDTO;
import kr.co.mcmp.softwarecatalog.application.dto.HelmChartRegistrationRequest;
import kr.co.mcmp.softwarecatalog.application.repository.HelmChartRepository;
import kr.co.mcmp.softwarecatalog.application.service.ArtifactHubIntegrationService;
import kr.co.mcmp.softwarecatalog.application.service.HelmChartIntegrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@Log4j2
@RequiredArgsConstructor
public class ApplicationHelmChartService {

    private final CatalogService catalogService;
    private final HelmChartRepository helmChartRepository;
    private final HelmChartIntegrationService helmChartIntegrationService;
    private final ArtifactHubIntegrationService artifactHubIntegrationService;

    @Transactional
    public Map<String, Object> registerHelmChart(HelmChartRegistrationRequest request, String username) {
        log.info("Registering Helm Chart: {}:{}", request.getChartName(), request.getChartVersion());

        Map<String, Object> result = helmChartIntegrationService.registerHelmChart(request, username);

        // 카탈로그도 함께 생성
        if ((Boolean) result.get("success")) {
            try {
                // SoftwareCatalogDTO catalogDTO = createCatalogFromHelmChart(request);
                // SoftwareCatalogDTO savedCatalog = catalogService.createCatalog(catalogDTO, username);
                // result.put("catalog", savedCatalog);
            } catch (Exception e) {
                log.warn("Failed to create catalog for Helm Chart: {}", request.getChartName(), e);
            }
        }

        return result;
    }

    private SoftwareCatalogDTO createCatalogFromHelmChart(HelmChartRegistrationRequest request) {
        return SoftwareCatalogDTO.builder()
                .name(request.getChartName())
                .description(request.getDescription())
                .version(request.getChartVersion())
                .category(request.getCategory())
                .license(request.getLicense())
                .homepage(request.getHomepage())
                .repositoryUrl(request.getRepositoryUrl())
                .documentationUrl(request.getDocumentationUrl())
                .sourceType("ARTIFACTHUB")
                .packageName(request.getChartName())
                .build();
    }

    public Map<String, Object> searchHelmCharts(String query, int page, int pageSize) {
        return helmChartIntegrationService.searchHelmCharts(query, page, pageSize);
    }

    public Map<String, Object> getHelmChartDetails(String packageId) {
        return helmChartIntegrationService.getHelmChartDetails(packageId);
    }

    public List<String> getHelmChartVersions(String packageId) {
        return helmChartIntegrationService.getHelmChartVersions(packageId);
    }
}