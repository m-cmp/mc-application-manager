package kr.co.mcmp.softwarecatalog.kubernetes.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.IngressList;
import io.fabric8.kubernetes.api.model.networking.v1.IngressListBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.AnyNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import kr.co.mcmp.softwarecatalog.application.dto.DeploymentConfigDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class KubernetesIngressRouteValidatorTest {
    @SuppressWarnings("unchecked")
    static AnyNamespaceOperation<Ingress, IngressList, Resource<Ingress>> stubIngressList(KubernetesClient client) {
        AnyNamespaceOperation<Ingress, IngressList, Resource<Ingress>> routes = mock(AnyNamespaceOperation.class);
        when(client.network().v1().ingresses().inAnyNamespace()).thenReturn(routes);
        return routes;
    }

    static Ingress ingress(String namespace, String name, String clazz, String host, String path) {
        return new IngressBuilder().withNewMetadata().withNamespace(namespace).withName(name).endMetadata()
                .withNewSpec().withIngressClassName(clazz)
                .addNewRule().withHost(host).withNewHttp().addNewPath().withPath(path).withPathType("Prefix")
                .endPath().endHttp().endRule().endSpec().build();
    }

    private static DeploymentConfigDTO config(String host, String path) {
        DeploymentConfigDTO config = HelmIngressValuesTest.config();
        config.setIngressHost(host);
        config.setIngressPath(path);
        return config;
    }

    static Stream<Arguments> collisions() {
        return Stream.of(
                Arguments.of("app.example.com", "/", "app.example.com", "/"),
                Arguments.of("APP.EXAMPLE.COM", "/app", "app.example.com", "/app"),
                Arguments.of("app.example.com", "/app/", "app.example.com", "/app"),
                Arguments.of("app.example.com", "/app", "app.example.com", "/app/"),
                Arguments.of("*.example.com", "/", "app.example.com", "/"),
                Arguments.of("app.example.com", "/", "*.example.com", "/"),
                Arguments.of("*.example.com", "/", "*.example.com", "/"),
                Arguments.of(null, "/", "app.example.com", "/"),
                Arguments.of("", "/", "app.example.com", "/"),
                Arguments.of("app.example.com", "", "app.example.com", "/"));
    }

    @ParameterizedTest
    @MethodSource("collisions")
    void rejectsReservedHostAndPathAcrossNamespaces(String existingHost, String existingPath,
                                                   String requestedHost, String requestedPath) {
        Ingress existing = ingress("other-namespace", "existing-app", "nginx", existingHost, existingPath);
        assertThatIllegalArgumentException().isThrownBy(() -> KubernetesIngressRouteValidator.assertAvailable(
                List.of(existing), config(requestedHost, requestedPath)))
                .withMessageContaining("Host/Path conflict").withMessageContaining("other-namespace/existing-app");
    }

    static Stream<Arguments> distinctRoutes() {
        return Stream.of(
                Arguments.of("app.example.com", "/", "other.example.com", "/"),
                Arguments.of("app.example.com", "/", "app.example.com", "/grafana"),
                Arguments.of("app.example.com", "/grafana", "app.example.com", "/prometheus"),
                Arguments.of("app.example.com", "/app", "app.example.com", "/app/child"),
                Arguments.of("app.example.com", "/App", "app.example.com", "/app"),
                Arguments.of("*.example.com", "/", "example.com", "/"),
                Arguments.of("*.example.com", "/", "a.b.example.com", "/"),
                Arguments.of("*.example.com", "/", "*.other.com", "/"),
                Arguments.of("*.example.com", "/", "*.sub.example.com", "/"));
    }

    @ParameterizedTest
    @MethodSource("distinctRoutes")
    void permitsDifferentRoutesIncludingIntentionalSubpaths(String existingHost, String existingPath,
                                                          String requestedHost, String requestedPath) {
        assertThatCode(() -> KubernetesIngressRouteValidator.assertAvailable(
                List.of(ingress("default", "existing-app", "nginx", existingHost, existingPath)),
                config(requestedHost, requestedPath))).doesNotThrowAnyException();
    }

    @Test
    void ignoresADifferentExplicitClassAndPrefersSpecOverLegacyAnnotation() {
        Ingress existing = ingress("default", "existing-app", "internal-controller", "app.example.com", "/");
        existing.getMetadata().setAnnotations(Map.of("kubernetes.io/ingress.class", "nginx"));
        assertThatCode(() -> KubernetesIngressRouteValidator.assertAvailable(List.of(existing), config("app.example.com", "/")))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"nginx"})
    void checksLegacyAnnotationsAndConservativelyChecksClasslessRoutes(String annotation) {
        Ingress existing = ingress("default", "legacy-app", null, "app.example.com", "/");
        if (annotation != null) existing.getMetadata().setAnnotations(Map.of("kubernetes.io/ingress.class", annotation));
        assertThatIllegalArgumentException().isThrownBy(() -> KubernetesIngressRouteValidator.assertAvailable(
                List.of(existing), config("app.example.com", "/")));
    }

    @Test
    void ignoresAnotherLegacyClass() {
        Ingress existing = ingress("default", "legacy-app", null, "app.example.com", "/");
        existing.getMetadata().setAnnotations(Map.of("kubernetes.io/ingress.class", "another-controller"));
        assertThatCode(() -> KubernetesIngressRouteValidator.assertAvailable(List.of(existing), config("app.example.com", "/")))
                .doesNotThrowAnyException();
    }

    @Test
    void checksAllRulesAndDoesNotExemptDeletingOrSameNamedApplications() {
        Ingress existing = ingress("default", "existing-app", "nginx", "other.example.com", "/");
        existing.getMetadata().setDeletionTimestamp("2026-09-08T00:00:00Z");
        existing.getSpec().getRules().add(ingress("default", "second", "nginx", "app.example.com", "/")
                .getSpec().getRules().get(0));
        // Path-type differences do not permit reusing the same Host/Path reservation in AM.
        existing.getSpec().getRules().get(1).getHttp().getPaths().get(0).setPathType("Exact");
        assertThatIllegalArgumentException().isThrownBy(() -> KubernetesIngressRouteValidator.assertAvailable(
                List.of(existing), config("app.example.com", "/")));
    }

    @Test
    void allowsAnEmptyClusterAndIgnoresResourcesWithoutHttpRules() {
        assertThatCode(() -> KubernetesIngressRouteValidator.assertAvailable(List.of(), config("app.example.com", "/")))
                .doesNotThrowAnyException();
        assertThatCode(() -> KubernetesIngressRouteValidator.assertAvailable(List.of(new Ingress()), config("app.example.com", "/")))
                .doesNotThrowAnyException();
    }

    @Test
    void queriesOnlyTheSuppliedClusterButAllItsNamespaces() {
        KubernetesClient client = mock(KubernetesClient.class, RETURNS_DEEP_STUBS);
        var routes = stubIngressList(client);
        when(routes.list()).thenReturn(new IngressListBuilder()
                .withItems(ingress("another-project", "existing-app", "nginx", "app.example.com", "/")).build());
        assertThatIllegalArgumentException().isThrownBy(() -> KubernetesIngressRouteValidator.assertAvailable(
                client, config("app.example.com", "/"))).withMessageContaining("another-project/existing-app");
        verify(routes).list();
    }

    @Test
    void disabledIngressDoesNotQueryTheCluster() {
        KubernetesClient client = mock(KubernetesClient.class);
        DeploymentConfigDTO config = config("app.example.com", "/");
        config.setIngressEnabled(false);
        KubernetesIngressRouteValidator.assertAvailable(client, config);
        verifyNoInteractions(client);
    }

    @Test
    void deniedReadsFailClosedAndPreserveTheCause() {
        KubernetesClient client = mock(KubernetesClient.class, RETURNS_DEEP_STUBS);
        var routes = stubIngressList(client);
        KubernetesClientException forbidden = new KubernetesClientException("Forbidden");
        when(routes.list()).thenThrow(forbidden);
        assertThatThrownBy(() -> KubernetesIngressRouteValidator.assertAvailable(client, config("app.example.com", "/")))
                .isInstanceOf(IllegalStateException.class).hasCause(forbidden)
                .hasMessageContaining("cluster-wide Ingress list permission");
    }
}
