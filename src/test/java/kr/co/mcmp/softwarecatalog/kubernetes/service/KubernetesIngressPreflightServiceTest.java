package kr.co.mcmp.softwarecatalog.kubernetes.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.stream.Stream;
import java.util.function.Consumer;

import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressList;
import io.fabric8.kubernetes.api.model.networking.v1.IngressListBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.AnyNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import kr.co.mcmp.softwarecatalog.CatalogRepository;
import kr.co.mcmp.softwarecatalog.SoftwareCatalog;
import kr.co.mcmp.softwarecatalog.application.dto.K8sIngressCheckRequest;
import kr.co.mcmp.softwarecatalog.application.model.PackageInfo;
import kr.co.mcmp.softwarecatalog.kubernetes.config.KubernetesClientFactory;
import kr.co.mcmp.softwarecatalog.service.SoftwareSourceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class KubernetesIngressPreflightServiceTest {
    private final CatalogRepository catalogs = mock(CatalogRepository.class);
    private final SoftwareSourceService sources = mock(SoftwareSourceService.class);
    private final KubernetesClientFactory clients = mock(KubernetesClientFactory.class);
    private final KubernetesClient client = mock(KubernetesClient.class, RETURNS_DEEP_STUBS);
    private final AnyNamespaceOperation<Ingress, IngressList, Resource<Ingress>> routes =
            KubernetesIngressRouteValidatorTest.stubIngressList(client);
    private final KubernetesIngressPreflightService service = new KubernetesIngressPreflightService(catalogs, sources, clients);
    private final SoftwareCatalog catalog = SoftwareCatalog.builder().id(7L).ingressEnabled(true)
            .ingressHost("catalog.example.com").ingressPath("/").ingressClass("nginx").build();

    KubernetesIngressPreflightServiceTest() {
        when(catalogs.findById(7L)).thenReturn(Optional.of(catalog));
        when(sources.getArtifactHubSource(7L)).thenReturn(Optional.of(HelmIngressValuesTest.chart(
                "grafana", "https://grafana.github.io/helm-charts", "7.3.0")));
        when(clients.getClient("project-a", "cluster-a")).thenReturn(client);
        when(routes.list()).thenReturn(new IngressListBuilder().build());
        clearInvocations(client);
    }

    private K8sIngressCheckRequest request() {
        var r = new K8sIngressCheckRequest();
        r.setNamespace("project-a"); r.setClusterName("cluster-a"); r.setCatalogId(7L);
        r.setIngressHost("https://APP.Example.com:30880/ignored"); r.setIngressPath("/app");
        return r;
    }

    @ParameterizedTest
    @ValueSource(strings = {"/app", "/app/"})
    void checksCurrentFormNotCatalogAcrossAllWorkloadNamespaces(String path) {
        when(routes.list()).thenReturn(new IngressListBuilder().withItems(KubernetesIngressRouteValidatorTest.ingress(
                "other-workload", "existing", "nginx", "app.example.com", path)).build());
        var result = service.check(request());
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).singleElement().asString().contains("Host/Path conflict", "other-workload/existing");
        assertThat(result.warnings()).isEmpty();
        verify(client).close();
        verify(catalogs, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/other", "/app/child", "/App"})
    void differentPathsRemainAvailable(String path) {
        when(routes.list()).thenReturn(new IngressListBuilder().withItems(KubernetesIngressRouteValidatorTest.ingress(
                "default", "existing", "nginx", "app.example.com", path)).build());
        var result = service.check(request());
        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
        verify(client, never()).secrets();
    }

    @Test void disabledIngressDoesNotNeedClusterAccessEvenWithStaleInvalidFields() {
        var r = request(); r.setIngressEnabled(false); r.setIngressHost("bad_host"); r.setIngressTlsEnabled(true);
        assertThat(service.check(r).valid()).isTrue();
        verifyNoInteractions(clients, sources);
    }

    static Stream<Consumer<K8sIngressCheckRequest>> invalidInputs() {
        return Stream.of(r -> r.setIngressHost("bad_host"), r -> r.setIngressPath("not/a/path"),
                r -> r.setIngressClass(""), r -> {r.setIngressTlsEnabled(true); r.setIngressTlsSecret("bad/name");},
                r -> {r.setIngressTlsEnabled(true); r.setIngressTlsSecret("");});
    }

    @ParameterizedTest @MethodSource("invalidInputs")
    void invalidInputBlocksBeforeClusterAccess(Consumer<K8sIngressCheckRequest> change) {
        var r = request(); change.accept(r);
        var result = service.check(r);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).isNotEmpty();
        verifyNoInteractions(clients);
    }

    @Test void missingTlsSecretIsAnAdvisoryNotADeploymentBlock() {
        var r = request(); r.setIngressTlsEnabled(true);
        var result = service.check(r);
        assertThat(result.valid()).isTrue();
        assertThat(result.warnings()).singleElement().asString().contains("<release-name>-tls", "namespace default");
        verify(client, never()).secrets();
    }

    @Test void catalogSecretIsUsedWhenRequestOmitsItAndHttpOverrideSkipsIt() {
        catalog.setIngressTlsEnabled(true); catalog.setIngressTlsSecret("catalog-cert");
        var secret = KubernetesIngressTlsWarningsTest.stubSecret(client, "default", "catalog-cert");
        when(secret.get()).thenReturn(null);
        var result = service.check(request());
        assertThat(result.valid()).isTrue();
        assertThat(result.warnings()).singleElement().asString().contains("catalog-cert", "namespace default");
        var r = request(); r.setIngressTlsEnabled(false);
        assertThat(service.check(r).warnings()).isEmpty();
        verify(secret, times(1)).get();
    }

    @Test void ingressListPermissionFailureBlocksButDoesNotLeakResponseDetails() {
        when(routes.list()).thenThrow(new IllegalStateException("sensitive API content"));
        var result = service.check(request());
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).singleElement().asString().contains("Ingress list permission").doesNotContain("sensitive");
    }

    @Test void missingListOrClusterAccessDoesNotCountAsAnEmptyCluster() {
        when(routes.list()).thenReturn(null);
        assertThat(service.check(request()).valid()).isFalse();
        when(clients.getClient("project-a", "cluster-a")).thenThrow(new IllegalStateException("private credentials"));
        var result = service.check(request());
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).singleElement().asString().doesNotContain("private credentials");
    }

    @Test void nativeJupyterRetainsItsStricterDedicatedHostAndHttpPolicyWithoutAChart() {
        catalog.setPackageInfo(PackageInfo.builder().packageName("jupyterlab").build());
        when(routes.list()).thenReturn(new IngressListBuilder().withItems(KubernetesIngressRouteValidatorTest.ingress(
                "default", "existing", "other-class", "app.example.com", "/unrelated")).build());
        var r = request(); r.setIngressPath("/");
        assertThat(service.check(r).errors()).singleElement().asString().contains("dedicated hostname");
        r.setIngressTlsEnabled(true);
        assertThat(service.check(r).errors()).singleElement().asString().contains("HTTP NodePort 30880");
        verifyNoInteractions(sources);
    }
}
