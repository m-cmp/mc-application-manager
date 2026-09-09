package kr.co.mcmp.softwarecatalog.kubernetes.service;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.util.Optional;

import kr.co.mcmp.ape.cbtumblebug.api.CbtumblebugRestApi;
import kr.co.mcmp.softwarecatalog.SoftwareCatalog;
import kr.co.mcmp.softwarecatalog.application.dto.DeploymentRequest;
import kr.co.mcmp.softwarecatalog.application.model.HelmChart;
import kr.co.mcmp.softwarecatalog.kubernetes.config.KubeconfigResolver;
import kr.co.mcmp.softwarecatalog.kubernetes.config.KubernetesClientFactory;
import kr.co.mcmp.softwarecatalog.service.SoftwareSourceService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class HelmIngressPreflightTest {
    @ParameterizedTest
    @CsvSource({"true,unknown", "false,unknown", "true,known", "false,known"})
    void rejectsBeforeConnectingOrInstallingClusterComponents(boolean withRequest, String scenario) {
        KubernetesClientFactory factory = mock(KubernetesClientFactory.class);
        HelmChartService helmService = mock(HelmChartService.class);
        SoftwareSourceService sources = mock(SoftwareSourceService.class);
        KubernetesDeployService service = new KubernetesDeployService(
                factory, null, helmService, null, null, null, sources, null, null);
        SoftwareCatalog catalog = SoftwareCatalog.builder().id(1L).ingressEnabled(true)
                .ingressHost("bad_host").build();
        HelmChart chart = HelmIngressValuesTest.chart("grafana", scenario.equals("unknown")
                ? "https://unverified.example/charts" : "https://grafana.github.io/helm-charts", "7.3.0");
        when(sources.getArtifactHubSource(1L)).thenReturn(Optional.of(chart));

        assertThatIllegalArgumentException().isThrownBy(() -> service.deployApplication(
                "project-a", "cluster-a", catalog, "user", withRequest ? new DeploymentRequest() : null))
                .withMessageContaining("Host");
        verifyNoInteractions(factory, helmService);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void directHelmEntryPointsAlsoRejectBeforeExternalActions(boolean withRequest) {
        CbtumblebugRestApi api = mock(CbtumblebugRestApi.class);
        KubeconfigResolver resolver = mock(KubeconfigResolver.class);
        HelmChartService service = new HelmChartService(api, resolver, null, null, null, null);
        SoftwareCatalog catalog = SoftwareCatalog.builder().ingressEnabled(true).ingressHost("bad_host").build();
        HelmChart chart = HelmIngressValuesTest.chart("unverified", "https://example.com/charts", "1.0.0");
        assertThatIllegalArgumentException().isThrownBy(() -> {
            if (withRequest) service.deployHelmChartWithRequest(null, "project-a", catalog, chart,
                    "cluster-a", new DeploymentRequest());
            else service.deployHelmChart(null, "project-a", catalog, chart, "cluster-a");
        }).withMessageContaining("Host");
        verifyNoInteractions(api, resolver);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void validUnknownChartsRespectCidrBeforeReachingTheClusterBoundary(boolean withRequest) {
        KubernetesClientFactory factory = mock(KubernetesClientFactory.class);
        HelmChartService helmService = mock(HelmChartService.class);
        SoftwareSourceService sources = mock(SoftwareSourceService.class);
        KubernetesDeployService service = new KubernetesDeployService(
                factory, null, helmService, null, null, null, sources, null, null);
        SoftwareCatalog catalog = SoftwareCatalog.builder().id(1L).ingressEnabled(true)
                .ingressHost("app.example.com").build();
        when(sources.getArtifactHubSource(1L)).thenReturn(Optional.of(HelmIngressValuesTest.chart(
                "new-app", "https://example.com/charts", "1.0.0")));
        // Stop at the first external boundary: never connect to an actual cluster in this test.
        IllegalStateException boundary = new IllegalStateException("test-cluster-boundary");
        if (withRequest) {
            DeploymentRequest request = DeploymentRequest.builder().servicePortCidr("203.0.113.8/32").build();
            when(factory.getClient("project-a", "cluster-a")).thenThrow(boundary);
            assertThatThrownBy(() -> service.deployApplication("project-a", "cluster-a", catalog,
                    "user", request)).hasCause(boundary);
            verify(factory).getClient("project-a", "cluster-a");
        } else {
            // The remote policy now requires an explicit source CIDR before
            // any externally accessible application can contact the cluster.
            assertThatThrownBy(() -> service.deployApplication("project-a", "cluster-a", catalog,
                    "user", null)).hasMessageContaining("CIDR");
            verifyNoInteractions(factory);
        }
        verifyNoInteractions(helmService);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void bothDirectHelmEntryPointsAcceptValidUnknownCharts(boolean withRequest) {
        CbtumblebugRestApi api = mock(CbtumblebugRestApi.class);
        KubeconfigResolver resolver = mock(KubeconfigResolver.class);
        HelmChartService service = new HelmChartService(api, resolver, null, null, null, null);
        SoftwareCatalog catalog = SoftwareCatalog.builder().ingressEnabled(true)
                .ingressHost("app.example.com").build();
        HelmChart chart = HelmIngressValuesTest.chart("new-app", "https://example.com/charts", "1.0.0");
        IllegalStateException boundary = new IllegalStateException("test-cluster-boundary");
        when(api.getK8sClusterByName("project-a", "cluster-a")).thenThrow(boundary);
        assertThatThrownBy(() -> {
            if (withRequest) service.deployHelmChartWithRequest(null, "project-a", catalog, chart,
                    "cluster-a", new DeploymentRequest());
            else service.deployHelmChart(null, "project-a", catalog, chart, "cluster-a");
        }).hasCause(boundary);
        verify(api).getK8sClusterByName("project-a", "cluster-a");
        verifyNoInteractions(resolver);
    }
}
