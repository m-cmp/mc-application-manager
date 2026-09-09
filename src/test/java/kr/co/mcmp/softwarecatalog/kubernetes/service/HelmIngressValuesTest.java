package kr.co.mcmp.softwarecatalog.kubernetes.service;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import kr.co.mcmp.softwarecatalog.SoftwareCatalog;
import kr.co.mcmp.softwarecatalog.application.dto.DeploymentConfigDTO;
import kr.co.mcmp.softwarecatalog.application.dto.DeploymentRequest;
import kr.co.mcmp.softwarecatalog.application.model.HelmChart;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.yaml.snakeyaml.Yaml;

class HelmIngressValuesTest {
    static Stream<Arguments> charts() {
        return Stream.of(
                Arguments.of("grafana", "https://grafana.github.io/helm-charts", "7.3.0", "ingress"),
                Arguments.of("prometheus", "https://prometheus-community.github.io/helm-charts", "25.8.0", "server.ingress"),
                Arguments.of("rclone", "https://jacobcolvin.com/helm-charts", "1.0.1", "ingress.main"),
                Arguments.of("nginx", "https://charts.bitnami.com/bitnami", "21.1.23", "ingress"));
    }

    static HelmChart chart(String name, String url, String version) {
        return HelmChart.builder().chartName(name).chartRepositoryUrl(url).chartVersion(version)
                .repositoryName("user-chosen-alias").build();
    }

    static DeploymentConfigDTO config() {
        return DeploymentConfigDTO.builder().ingressEnabled(true).ingressHost("app.example.com")
                .ingressPath("/").ingressClass("nginx").ingressTlsEnabled(false).build();
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> ingress(Map<String, Object> values, String root) {
        Map<String, Object> result = values;
        for (String part : root.split("\\.")) {
            result = (Map<String, Object>) result.get(part);
        }
        return result;
    }

    @ParameterizedTest
    @MethodSource("charts")
    void mapsHttpAndTlsUsingTheChartSchema(String name, String url, String version, String root) {
        HelmChart chart = chart(name, url + "/", version);
        DeploymentConfigDTO config = config();
        Map<String, Object> http = ingress(HelmIngressValues.from(chart, config), root);
        assertThat(http).containsEntry("enabled", true).containsEntry("ingressClassName", "nginx");
        assertThat(http.get(name.equals("nginx") ? "extraTls" : "tls")).isEqualTo(List.of());
        config.setIngressTlsEnabled(true);
        config.setIngressTlsSecret("chosen-cert");
        Map<String, Object> tls = ingress(HelmIngressValues.from(chart, config), root);
        assertThat(tls.get(name.equals("nginx") ? "extraTls" : "tls"))
                .isEqualTo(List.of(Map.of("secretName", "chosen-cert", "hosts", List.of("app.example.com"))));
        if (name.equals("nginx")) {
            assertThat(tls).containsEntry("hostname", "app.example.com").containsEntry("tls", false)
                    .containsEntry("selfSigned", false);
        } else if (name.equals("rclone")) {
            assertThat(tls.get("hosts")).isEqualTo(List.of(Map.of("host", "app.example.com", "paths",
                    List.of(Map.of("path", "/", "pathType", "Prefix")))));
        } else {
            assertThat(tls).containsEntry("hosts", List.of("app.example.com")).containsEntry("path", "/");
        }
        // Building TLS values must not mutate a previous deployment's HTTP values.
        assertThat(http.get(name.equals("nginx") ? "extraTls" : "tls")).isEqualTo(List.of());
    }

    @ParameterizedTest
    @MethodSource("charts")
    void disabledIngressIgnoresStaleFormFields(String name, String url, String version, String root) {
        DeploymentConfigDTO config = new DeploymentConfigDTO();
        config.setIngressEnabled(false);
        config.setIngressTlsEnabled(true);
        assertThat(ingress(HelmIngressValues.from(chart(name, url, version), config), root))
                .containsExactlyEntriesOf(Map.of("enabled", false));
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.NullAndEmptySource
    @org.junit.jupiter.params.provider.ValueSource(strings = {"https://different-vendor.example/charts"})
    void unknownRepositoriesUseHelmCreateFallbackNotAnAdapterBasedOnlyOnName(String url) {
        HelmChart chart = chart("grafana", url, "7.3.0");
        Map<String, Object> ingress = ingress(HelmIngressValues.from(chart, config()), "ingress");
        assertThat(ingress).containsEntry("enabled", true).containsEntry("className", "nginx")
                .doesNotContainKeys("ingressClassName", "hostname", "path");
        assertThat(ingress.get("hosts")).isEqualTo(List.of(Map.of("host", "app.example.com", "paths",
                List.of(Map.of("path", "/", "pathType", "Prefix")))));
        assertThat(ingress.get("tls")).isEqualTo(List.of());
        DeploymentConfigDTO disabled = new DeploymentConfigDTO();
        assertThat(HelmIngressValues.from(chart, disabled)).isEqualTo(Map.of("ingress", Map.of("enabled", false)));
    }

    @Test
    void unknownChartInAKnownRepositoryAlsoUsesFallbackWithTlsAndSubpath() {
        DeploymentConfigDTO config = config();
        config.setIngressTlsEnabled(true);
        config.setIngressTlsSecret("existing-cert");
        config.setIngressPath("/app");
        config.setIngressClass("custom-ingress");
        HelmChart chart = chart("new-app", "https://grafana.github.io/helm-charts", "1.0.0");
        Map<String, Object> ingress = ingress(HelmIngressValues.from(chart, config), "ingress");
        assertThat(ingress).containsEntry("className", "custom-ingress");
        assertThat(ingress.get("tls")).isEqualTo(List.of(Map.of("secretName", "existing-cert",
                "hosts", List.of("app.example.com"))));
        assertThat(ingress.get("hosts")).isEqualTo(List.of(Map.of("host", "app.example.com", "paths",
                List.of(Map.of("path", "/app", "pathType", "Prefix")))));
    }

    @Test
    void preservesStringsThatHelmSetCouldCoerceOrSplit() {
        DeploymentConfigDTO config = config();
        config.setIngressHost("true");
        config.setIngressClass("nginx");
        config.setIngressPath("/a,b");
        Map<String, Object> values = HelmIngressValues.from(
                chart("grafana", "https://grafana.github.io/helm-charts", "7.3.0"), config);
        Map<String, Object> parsed = new Yaml().load(new Yaml().dump(values));
        assertThat(ingress(parsed, "ingress")).containsEntry("hosts", List.of("true"))
                .containsEntry("ingressClassName", "nginx").containsEntry("path", "/a,b");
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"*.example.com", "true", "123"})
    void rejectsPrometheusHostsThatItsUnquotedTemplateCannotRender(String host) {
        DeploymentConfigDTO config = config();
        config.setIngressHost(host);
        assertThatIllegalArgumentException().isThrownBy(() -> HelmIngressValues.from(
                chart("prometheus", "https://prometheus-community.github.io/helm-charts", "25.8.0"), config))
                .withMessageContaining("Host");
    }

    @Test
    void requestValuesOverrideCatalogAndLegacyUsesTheSameDefaults() {
        SoftwareCatalog catalog = SoftwareCatalog.builder().ingressEnabled(true)
                .ingressHost("https://Catalog.Example.com/path").ingressTlsEnabled(true)
                .ingressTlsSecret("catalog-cert").build();
        DeploymentRequest request = new DeploymentRequest();
        request.setIngressHost("https://Request.Example.com:30880/ignored");
        request.setIngressTlsEnabled(false);
        HelmChart chart = chart("grafana", "https://grafana.github.io/helm-charts", "7.3.0");
        assertThat(ingress(HelmIngressValues.from(chart, DeploymentConfigDTO.from(request, catalog)), "ingress"))
                .containsEntry("hosts", List.of("request.example.com")).containsEntry("tls", List.of());
        assertThat(ingress(HelmIngressValues.from(chart,
                DeploymentConfigDTO.from(new DeploymentRequest(), catalog)), "ingress"))
                .containsEntry("hosts", List.of("catalog.example.com"));
    }

    static Stream<Arguments> invalidSettings() {
        return Stream.of(
                Arguments.of((Consumer<DeploymentConfigDTO>) c -> c.setIngressHost(null), "Host"),
                Arguments.of((Consumer<DeploymentConfigDTO>) c -> c.setIngressHost(""), "Host"),
                Arguments.of((Consumer<DeploymentConfigDTO>) c -> c.setIngressHost("127.0.0.1"), "Host"),
                Arguments.of((Consumer<DeploymentConfigDTO>) c -> c.setIngressHost("bad_name.example"), "Host"),
                Arguments.of((Consumer<DeploymentConfigDTO>) c -> c.setIngressHost("**.example.com"), "Host"),
                Arguments.of((Consumer<DeploymentConfigDTO>) c -> c.setIngressPath("grafana"), "Path"),
                Arguments.of((Consumer<DeploymentConfigDTO>) c -> c.setIngressPath("/a?b=c"), "Path"),
                Arguments.of((Consumer<DeploymentConfigDTO>) c -> c.setIngressPath("/a b"), "Path"),
                Arguments.of((Consumer<DeploymentConfigDTO>) c -> c.setIngressClass(""), "Class"),
                Arguments.of((Consumer<DeploymentConfigDTO>) c -> c.setIngressClass("false"), "Class"),
                Arguments.of((Consumer<DeploymentConfigDTO>) c -> {
                    c.setIngressTlsEnabled(true); c.setIngressTlsSecret("");
                }, "Secret"),
                Arguments.of((Consumer<DeploymentConfigDTO>) c -> {
                    c.setIngressTlsEnabled(true); c.setIngressTlsSecret("invalid/secret");
                }, "Secret"));
    }

    @ParameterizedTest
    @MethodSource("invalidSettings")
    void rejectsInvalidEnabledIngress(Consumer<DeploymentConfigDTO> mutate, String field) {
        DeploymentConfigDTO config = config();
        mutate.accept(config);
        assertThatIllegalArgumentException().isThrownBy(() -> HelmIngressValues.from(
                chart("grafana", "https://grafana.github.io/helm-charts", "7.3.0"), config))
                .withMessageContaining(field);
    }

    @ParameterizedTest
    @MethodSource("charts")
    void restoresNullSecretFallbackUsingActualReleaseWithoutChangingInput(String name, String url, String version, String root) {
        var config = config();
        config.setIngressTlsEnabled(true);
        var chart = chart(name, url, version);
        assertThatCode(() -> HelmIngressValues.validate(chart, config)).doesNotThrowAnyException();
        var resolved = HelmIngressValues.resolveTlsConfig(config, "actual-release-123");
        assertThat(config.getIngressTlsSecret()).isNull();
        assertThat(resolved.getIngressTlsSecret()).isEqualTo("actual-release-123-tls");
        var values = ingress(HelmIngressValues.from(chart, resolved), root);
        assertThat(values.get(name.equals("nginx") ? "extraTls" : "tls"))
                .isEqualTo(List.of(Map.of("secretName", "actual-release-123-tls", "hosts", List.of("app.example.com"))));
    }

    @Test
    void genericAndLokiPolicyAlsoUseResolvedSecretAndExplicitSelectionIsPreserved() {
        var config = config();
        config.setIngressTlsEnabled(true);
        var resolved = HelmIngressValues.resolveTlsConfig(config, "actual-release");
        var values = HelmIngressValues.from(chart("loki", "https://unknown.example/charts", "1"), resolved);
        assertThat(ingress(values, "ingress").get("tls"))
                .isEqualTo(List.of(Map.of("secretName", "actual-release-tls", "hosts", List.of("app.example.com"))));
        K8sIngressPolicy.configureValues("loki", new java.util.HashMap<>(), values, resolved, "203.0.113.8/32");
        assertThat(ingress(values, "gateway.ingress").get("tls"))
                .isEqualTo(ingress(values, "ingress").get("tls"));
        config.setIngressTlsSecret("chosen-cert");
        assertThat(HelmIngressValues.resolveTlsConfig(config, "different-release").getIngressTlsSecret()).isEqualTo("chosen-cert");
        config.setIngressTlsEnabled(false);
        config.setIngressTlsSecret("invalid but ignored for HTTP");
        assertThatCode(() -> HelmIngressValues.from(chart("grafana", "https://grafana.github.io/helm-charts", "7.3.0"), config))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.NullAndEmptySource
    @org.junit.jupiter.params.provider.ValueSource(strings = {"invalid/name", " ", "UPPERCASE"})
    void requiresAnActualValidReleaseForTheFallback(String release) {
        var config = config();
        config.setIngressTlsEnabled(true);
        assertThatIllegalArgumentException().isThrownBy(() -> HelmIngressValues.resolveTlsConfig(config, release));
    }
}
