package kr.co.mcmp.softwarecatalog.kubernetes.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressList;
import io.fabric8.kubernetes.api.model.networking.v1.IngressListBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.AnyNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import kr.co.mcmp.softwarecatalog.SoftwareCatalog;
import kr.co.mcmp.softwarecatalog.application.dto.DeploymentRequest;
import kr.co.mcmp.softwarecatalog.application.repository.ApplicationStatusRepository;
import kr.co.mcmp.softwarecatalog.application.repository.DeploymentHistoryRepository;
import kr.co.mcmp.softwarecatalog.kubernetes.config.KubernetesClientFactory;
import kr.co.mcmp.softwarecatalog.service.SoftwareSourceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class KubernetesDeployServiceIngressConflictTest {
    private final KubernetesClient client = mock(KubernetesClient.class, RETURNS_DEEP_STUBS);
    private final AnyNamespaceOperation<Ingress, IngressList, Resource<Ingress>> routes =
            KubernetesIngressRouteValidatorTest.stubIngressList(client);
    private final KubernetesClientFactory factory = mock(KubernetesClientFactory.class);
    private final HelmChartService helm = mock(HelmChartService.class);
    private final K8sIngressAccessService access = mock(K8sIngressAccessService.class);
    private final ApplicationStatusRepository statuses = mock(ApplicationStatusRepository.class);
    private final DeploymentHistoryRepository histories = mock(DeploymentHistoryRepository.class);
    private final SoftwareSourceService sources = mock(SoftwareSourceService.class);
    private final KubernetesDeployService service = new KubernetesDeployService(
            factory, null, helm, null, statuses, histories, sources, null, access, IbmIngressAutomationTestSupport.legacy());
    private final SoftwareCatalog catalog = SoftwareCatalog.builder().id(7L)
            .ingressEnabled(true).ingressHost("app.example.com").ingressPath("/app").build();

    KubernetesDeployServiceIngressConflictTest() {
        when(sources.getArtifactHubSource(7L)).thenReturn(Optional.of(HelmIngressValuesTest.chart(
                "grafana", "https://grafana.github.io/helm-charts", "7.3.0")));
        when(factory.getClient("project-a", "cluster-a")).thenReturn(client);
        clearInvocations(client);
    }

    private DeploymentRequest request() {
        return DeploymentRequest.builder().namespace("project-a").clusterName("cluster-a")
                .servicePortCidr("203.0.113.8/32").build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/app", "/app/"})
    void duplicateCatalogRouteStopsBeforeStatusUpdatesInstallAndPortOpening(String existingPath) {
        when(routes.list()).thenReturn(new IngressListBuilder()
                .withItems(KubernetesIngressRouteValidatorTest.ingress(
                        "other-namespace", "old-app", "nginx", "app.example.com", existingPath)).build());

        assertThatThrownBy(() -> service.deployApplication("project-a", "cluster-a", catalog, "user", request()))
                .hasMessageContaining("Host/Path conflict").hasMessageContaining("other-namespace/old-app")
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .rootCause().hasMessageContaining("Host/Path conflict").hasMessageContaining("other-namespace/old-app");
        verifyNoInteractions(helm, statuses, histories);
        verify(access).resolveTarget(any(), same(catalog));
        verifyNoMoreInteractions(access);
        verify(client).close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/prometheus", "/app/child"})
    void differentRequestPathOverridesCatalogAndContinuesToNormalDeployment(String requestedPath) {
        Ingress existing = KubernetesIngressRouteValidatorTest.ingress(
                "default", "old-app", "nginx", "app.example.com", "/app");
        when(routes.list())
                .thenReturn(new IngressListBuilder().withItems(existing).build());
        DeploymentRequest request = request();
        request.setIngressPath(requestedPath);
        IllegalStateException boundary = stopAtMetricsBoundary();

        assertThatThrownBy(() -> service.deployApplication("project-a", "cluster-a", catalog, "user", request))
                .hasCause(boundary);
        var order = inOrder(routes, helm);
        order.verify(routes).list();
        order.verify(helm).ensureMetricsServer(client, "project-a", "cluster-a");
        verifyNoInteractions(histories);
        verify(access).resolveTarget(any(), same(catalog));
        verifyNoMoreInteractions(access);
    }

    @Test
    void disabledIngressSkipsTheCheckAndPreservesNormalDeployment() {
        DeploymentRequest request = request();
        request.setIngressEnabled(false);
        request.setServicePortCidr(null);
        IllegalStateException boundary = stopAtMetricsBoundary();
        assertThatThrownBy(() -> service.deployApplication("project-a", "cluster-a", catalog, "user", request))
                .hasCause(boundary);
        verify(client, never()).network();
        verifyNoInteractions(access, histories);
    }

    @Test
    void readFailureStopsBeforeAnyInstallationOrFirewallChange() {
        KubernetesClientException forbidden = new KubernetesClientException("Forbidden");
        when(routes.list()).thenThrow(forbidden);
        assertThatThrownBy(() -> service.deployApplication("project-a", "cluster-a", catalog, "user", request()))
                .hasMessageContaining("cluster-wide Ingress list permission")
                .hasRootCause(forbidden);
        verifyNoInteractions(helm, statuses, histories);
        verify(access).resolveTarget(any(), same(catalog));
        verifyNoMoreInteractions(access);
    }

    private IllegalStateException stopAtMetricsBoundary() {
        when(statuses.findLatestByNamespaceAndClusterNameAndCatalogId("project-a", "cluster-a", 7L))
                .thenReturn(Optional.empty());
        IllegalStateException boundary = new IllegalStateException("test-metrics-boundary");
        doThrow(boundary).when(helm).ensureMetricsServer(client, "project-a", "cluster-a");
        return boundary;
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void ibmDeploymentRechecksPolicyAndNeverInstallsNodePortController(boolean proxyReady) {
        var ibm = IbmIngressSupportTest.readyClient(IbmIngressSupport.PUBLIC_CLASS,proxyReady,"10.150.0.0/24","proxy-protocol",false);
        IbmIngressTlsResolverTest.configureDefault(ibm);
        var ibmRoutes = KubernetesIngressRouteValidatorTest.stubIngressList(ibm);
        when(ibmRoutes.list()).thenReturn(new IngressListBuilder().build());
        when(factory.getClient("project-a","cluster-a")).thenReturn(ibm);
        doAnswer(call -> { ((DeploymentRequest)call.getArgument(0)).setIngressClass(IbmIngressSupport.PUBLIC_CLASS); return null; })
                .when(access).resolveTarget(any(),same(catalog));
        var boundary = new IllegalStateException("test-application-helm-boundary");
        doThrow(boundary).when(helm).deployHelmChart(eq(ibm),eq("project-a"),same(catalog),any(),eq("cluster-a"),any());
        var request = request(); request.setIngressHost(IbmIngressTlsResolverTest.HOST); request.setIngressTlsEnabled(true);
        var result = assertThatThrownBy(() -> service.deployApplication("project-a","cluster-a",catalog,"user",request));
        if (proxyReady) {
            result.hasCause(boundary);
            verify(helm).ensureMetricsServer(ibm,"project-a","cluster-a");
            verify(helm,never()).ensureIngressController(any(),any(),any());
            assertThat(request.getIngressTlsEnabled()).isTrue();
            assertThat(request.getIngressTlsSecret()).isEqualTo(IbmIngressTlsResolverTest.SECRET);
        } else {
            result.rootCause().hasMessageContaining("source IP preservation is disabled");
            verifyNoInteractions(helm);
        }
        verify(access).resolveTarget(any(),same(catalog));
        verifyNoMoreInteractions(access);
        verifyNoInteractions(histories);
    }
}
