package kr.co.mcmp.softwarecatalog.helm;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.co.mcmp.softwarecatalog.application.dto.HelmChartRegistrationRequest;
import kr.co.mcmp.softwarecatalog.application.service.HelmChartIntegrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class ApplicationHelmChartService {

    private final HelmChartIntegrationService helmChartIntegrationService;

    @Transactional
    public Map<String, Object> registerHelmChart(HelmChartRegistrationRequest request, String username) {
        log.info("Registering Helm Chart: {}:{}", request.getChartName(), request.getChartVersion());
        return helmChartIntegrationService.registerHelmChart(request, username);
    }

    public Map<String, Object> searchHelmCharts(String query, int page, int pageSize) {
        return helmChartIntegrationService.searchHelmCharts(query, page, pageSize);
    }


    public List<String> getHelmChartVersions(String packageId) {
        return helmChartIntegrationService.getHelmChartVersions(packageId);
    }
}