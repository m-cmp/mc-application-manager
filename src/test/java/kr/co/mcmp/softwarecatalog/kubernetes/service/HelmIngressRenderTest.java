package kr.co.mcmp.softwarecatalog.kubernetes.service;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import kr.co.mcmp.softwarecatalog.application.dto.DeploymentConfigDTO;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.yaml.snakeyaml.Yaml;

/** Opt-in offline rendering against locally downloaded, version-pinned upstream charts. */
@EnabledIfEnvironmentVariable(named = "AM_HELM_TEST_CHARTS_DIR", matches = ".+")
class HelmIngressRenderTest {
    @TempDir Path temp;

    static Stream<Arguments> renderCases() {
        return Stream.concat(HelmIngressValuesTest.charts(), Stream.of(
                Arguments.of("am-generic", "https://unverified.example/charts", "0.1.0")))
                .flatMap(chart -> Stream.of("default", "team-monitoring")
                .flatMap(namespace -> Stream.of("disabled", "http", "subpath", "tls", "tls-default")
                        .flatMap(mode -> Stream.of(false, true).map(withCidr -> Arguments.of(
                                chart.get()[0], chart.get()[1], chart.get()[2], namespace, mode, withCidr)))));
    }

    @ParameterizedTest(name = "{0} {2}, namespace={3}, mode={4}, cidr={5}")
    @MethodSource("renderCases")
    @SuppressWarnings("unchecked")
    void rendersTheSelectedRouteAndARealBackendService(String name, String url, String version,
                                                     String namespace, String mode, boolean withCidr) throws Exception {
        Path chartPath = name.equals("nginx") ? Path.of("nginx")
                : Path.of(System.getenv("AM_HELM_TEST_CHARTS_DIR"), name);
        if (name.equals("am-generic")) {
            // Exercise the fallback against a real helm-create chart, not a handcrafted template.
            chartPath = temp.resolve(name);
            Path createLog = temp.resolve("helm-create.log");
            Process create = new ProcessBuilder("helm", "create", chartPath.toString())
                    .redirectErrorStream(true).redirectOutput(createLog.toFile()).start();
            boolean created = create.waitFor(30, TimeUnit.SECONDS);
            if (!created) create.destroyForcibly();
            assertThat(created).as("Helm create timeout").isTrue();
            assertThat(create.exitValue()).as(Files.readString(createLog)).isZero();
        }
        Map<String, Object> metadata = new Yaml().load(Files.readString(chartPath.resolve("Chart.yaml")));
        assertThat(metadata.get("version").toString()).isEqualTo(version);

        DeploymentConfigDTO config = HelmIngressValuesTest.config();
        config.setIngressEnabled(!mode.equals("disabled"));
        config.setIngressTlsEnabled(mode.startsWith("tls"));
        config.setIngressTlsSecret(mode.equals("tls-default") ? null : "externally-managed-cert");
        config = HelmIngressValues.resolveTlsConfig(config, "am-render");
        if (mode.equals("subpath")) {
            config.setIngressHost(name.equals("prometheus") ? "app.example.com" : "*.example.com");
            config.setIngressPath("/app");
            config.setIngressClass("custom-ingress");
        }
        if (withCidr) config.setIngressClass("nginx");
        Map<String, Object> values = HelmIngressValues.from(HelmIngressValuesTest.chart(name, url, version), config);
        HelmChartService service = new HelmChartService(null, null, null, null, null, null);
        // Use the production YAML writer and chart-specific service defaults as the deployment does.
        Map<String, String> setValues = new LinkedHashMap<>();
        setValues.put("service.type", "ClusterIP");
        setValues.put("service.port", "80");
        setValues.put("persistence.enabled", "false");
        if (name.equals("rclone")) {
            ReflectionTestUtils.invokeMethod(service, "applyRcloneGuiDefaults", setValues, 5572);
        }
        if (name.equals("prometheus")) {
            ReflectionTestUtils.invokeMethod(service, "applyPrometheusPersistenceDefaults", setValues);
        }
        String cidr = withCidr && config.isIngressEnabled() ? "203.0.113.8/32" : null;
        if (withCidr) K8sIngressPolicy.configureValues(name, setValues, values, config, cidr);
        Path valuesFile = ReflectionTestUtils.invokeMethod(service, "createTempValuesFile", values);
        assertThat(setValues.keySet()).noneMatch(key -> key.startsWith("ingress."));

        Path output = temp.resolve("rendered.yaml");
        Path errors = temp.resolve("helm-errors.log");
        List<String> command = new ArrayList<>(List.of("helm", "template", "am-render", chartPath.toString(),
                "--namespace", namespace, "--kube-version", "1.30.0", "--values", valuesFile.toString()));
        command.addAll(HelmChartService.buildHelmSetArguments(setValues));
        try {
            Process process = new ProcessBuilder(command).redirectOutput(output.toFile())
                    .redirectError(errors.toFile()).start();
            boolean completed = process.waitFor(30, TimeUnit.SECONDS);
            if (!completed) process.destroyForcibly();
            assertThat(completed).as("Helm rendering timeout").isTrue();
            assertThat(process.exitValue()).as(Files.readString(errors)).isZero();
            if (withCidr) K8sIngressPolicy.verifyManifest(Files.readString(output), cidr, config.getIngressHost());
        } finally {
            Files.deleteIfExists(valuesFile);
        }

        List<Map<String, Object>> resources = new ArrayList<>();
        for (Object document : new Yaml().loadAll(Files.readString(output))) {
            if (document instanceof Map<?, ?>) resources.add((Map<String, Object>) document);
        }
        List<Map<String, Object>> ingresses = resources.stream()
                .filter(resource -> "Ingress".equals(resource.get("kind"))).toList();
        if (mode.equals("disabled")) {
            assertThat(ingresses).isEmpty();
            return;
        }
        assertThat(ingresses).hasSize(1);
        Ingress ingress = new ObjectMapper().convertValue(ingresses.get(0), Ingress.class);
        assertThat(ingress.getApiVersion()).isEqualTo("networking.k8s.io/v1");
        // Helm supplies its release namespace when a namespaced resource omits metadata.namespace.
        assertThat(ingress.getMetadata().getNamespace()).isIn(null, namespace);
        Map<String, Object> rawSpec = (Map<String, Object>) ingresses.get(0).get("spec");
        assertThat(rawSpec.get("ingressClassName")).isInstanceOf(String.class);
        assertThat(((List<Map<String, Object>>) rawSpec.get("rules")).get(0).get("host"))
                .isInstanceOf(String.class);
        assertThat(ingress.getSpec().getIngressClassName()).isEqualTo(config.getIngressClass());
        assertThat(ingress.getSpec().getRules()).hasSize(1);
        var rule = ingress.getSpec().getRules().get(0);
        assertThat(rule.getHost()).isEqualTo(config.getIngressHost());
        assertThat(rule.getHttp().getPaths()).hasSize(1);
        var path = rule.getHttp().getPaths().get(0);
        assertThat(path.getPath()).isEqualTo(config.getIngressPath());
        assertThat(path.getPathType()).isEqualTo("Prefix");
        if (config.isTlsEnabled()) {
            assertThat(ingress.getSpec().getTls()).hasSize(1);
            assertThat(ingress.getSpec().getTls().get(0).getSecretName()).isEqualTo(config.getIngressTlsSecret());
            assertThat(ingress.getSpec().getTls().get(0).getHosts()).containsExactly(config.getIngressHost());
        } else {
            assertThat(ingress.getSpec().getTls()).isNullOrEmpty();
        }
        // The generated Ingress must point to an existing rendered Service and one of its ports.
        var backend = path.getBackend().getService();
        List<Map<String, Object>> backends = resources.stream()
                .filter(resource -> "Service".equals(resource.get("kind")))
                .filter(resource -> backend.getName().equals(((Map<?, ?>) resource.get("metadata")).get("name")))
                .toList();
        assertThat(backends).hasSize(1);
        assertThat(((Map<?, ?>) backends.get(0).get("metadata")).get("namespace")).isIn(null, namespace);
        List<Map<String, Object>> ports = (List<Map<String, Object>>)
                ((Map<?, ?>) backends.get(0).get("spec")).get("ports");
        assertThat(ports).anyMatch(port -> backend.getPort().getName() != null
                ? backend.getPort().getName().equals(port.get("name"))
                : backend.getPort().getNumber().equals(port.get("port")));
    }
}
