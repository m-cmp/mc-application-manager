package kr.co.mcmp.softwarecatalog.kubernetes.service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.yaml.snakeyaml.Yaml;
import kr.co.mcmp.softwarecatalog.application.dto.DeploymentConfigDTO;
import kr.co.mcmp.softwarecatalog.application.dto.DeploymentRequest;
import static org.assertj.core.api.Assertions.*;

/** Renders the bundled packages using the same adapters and value precedence as AM's install path. */
class BuiltInHelmPipelineTest {
    private static final String CIDR = "203.0.113.8/32";

    @BeforeAll static void requireHelm() {
        try {
            Assumptions.assumeTrue(new ProcessBuilder("helm", "version", "--short").start().waitFor() == 0,
                    "Helm CLI required");
        } catch (Exception e) { Assumptions.abort("Helm CLI not installed"); }
    }

    static Stream<Arguments> services() {
        return BuiltInHelmCharts.APPS.stream().flatMap(app -> Stream.of(
                Arguments.of(app, app.port(), "standard", "10Gi"),
                Arguments.of(app, 18080, "fast.csi-v2", "20Gi")));
    }

    @ParameterizedTest(name = "{0}: service port {1}, storage {2}/{3}")
    @MethodSource("services")
    void preservesStorageSecurityAndContainerPortsWithAmOverrides(BuiltInHelmCharts.App app,
            int port, String storageClass, String capacity) throws Exception {
        var request = DeploymentRequest.builder().additionalConfig(Map.of(
                "storageClass", storageClass, "storageSize", capacity)).build();
        var rendered = render(app, config(port, false, "nginx", false, "/"), request);
        Map<String, Object> service = map(rendered.ofKind("Service").get("spec"));
        assertThat(service).containsEntry("type", "ClusterIP");
        assertThat(map(first(service.get("ports")))).containsEntry("port", port).containsEntry("targetPort", "app");
        Map<String, Object> pod = map(map(map(rendered.ofKind("Deployment").get("spec")).get("template")).get("spec"));
        assertThat(pod).containsEntry("automountServiceAccountToken", false);
        assertThat(map(pod.get("securityContext"))).containsEntry("runAsNonRoot", true);
        var container = map(first(pod.get("containers")));
        assertThat(map(first(container.get("ports")))).containsEntry("name", "app")
                .containsEntry("containerPort", app.chart().equals("apache") ? 8080 : app.port());
        assertThat(map(map(container.get("resources")).get("requests"))).containsEntry("memory", "256Mi");
        assertThat(rendered.kinds()).doesNotContain("Ingress", "HorizontalPodAutoscaler");
        if (app.persistent()) {
            var pvc = rendered.ofKind("PersistentVolumeClaim");
            var spec = map(pvc.get("spec"));
            assertThat(spec).containsEntry("storageClassName", storageClass)
                    .containsEntry("accessModes", List.of("ReadWriteOnce"));
            assertThat(map(map(spec.get("resources")).get("requests"))).containsEntry("storage", capacity);
            assertThat(map(map(pvc.get("metadata")).get("annotations")))
                    .containsEntry("helm.sh/resource-policy", "keep");
            assertThat(rendered.kinds()).contains("Secret");
        } else {
            assertThat(rendered.kinds()).doesNotContain("PersistentVolumeClaim", "Secret");
        }
    }

    static Stream<Arguments> routes() {
        return BuiltInHelmCharts.APPS.stream().filter(app -> !app.persistent()).flatMap(app ->
                Stream.of("nginx", "public-iks-k8s-nginx").flatMap(clazz ->
                        Stream.of(false, true).flatMap(tls -> Stream.of("/", "/demo").map(path ->
                                Arguments.of(app, clazz, tls, path)))));
    }

    @ParameterizedTest(name = "{0}: ingress {1}, TLS={2}, path={3}")
    @MethodSource("routes")
    void rendersTheRequestedRouteAndPassesAmCidrPolicy(BuiltInHelmCharts.App app,
            String clazz, boolean tls, String path) throws Exception {
        var rendered = render(app, config(18080, true, clazz, tls, path),
                DeploymentRequest.builder().servicePortCidr(CIDR).build());
        assertThatCode(() -> K8sIngressPolicy.verifyManifest(rendered.text(), CIDR, "app.example.com", clazz))
                .doesNotThrowAnyException();
        var ingress = rendered.ofKind("Ingress");
        var spec = map(ingress.get("spec"));
        var rule = map(first(spec.get("rules")));
        var route = map(first(map(rule.get("http")).get("paths")));
        assertThat(route).containsEntry("path", path).containsEntry("pathType", "Prefix");
        assertThat(map(map(map(route.get("backend")).get("service")).get("port"))).containsEntry("number", 18080);
        if (tls) {
            // AM's legacy release-name TLS Secret fallback still survives the CIDR adapter.
            assertThat(map(first(spec.get("tls")))).containsEntry("secretName", "pipeline-tls")
                    .containsEntry("hosts", List.of("app.example.com"));
        } else {
            assertThat(spec).doesNotContainKey("tls");
            if (IbmIngressSupport.managed(clazz)) {
                assertThat(map(map(ingress.get("metadata")).get("annotations")))
                        .containsEntry("nginx.ingress.kubernetes.io/ssl-redirect", "false")
                        .containsEntry("nginx.ingress.kubernetes.io/force-ssl-redirect", "false");
            }
        }
    }

    private static DeploymentConfigDTO config(int port, boolean ingress, String clazz, boolean tls, String path) {
        return DeploymentConfigDTO.builder().minReplicas(1).hpaEnabled(false).servicePort(port)
                .ingressEnabled(ingress).ingressHost("app.example.com").ingressPath(path)
                .ingressClass(clazz).ingressTlsEnabled(tls).build();
    }

    private static Rendered render(BuiltInHelmCharts.App app, DeploymentConfigDTO input,
            DeploymentRequest request) throws Exception {
        var chart = BuiltInHelmChartsTest.chart(app.chart());
        var config = HelmIngressValues.resolveTlsConfig(input, "pipeline");
        Map<String, Object> yaml = HelmIngressValues.from(chart, config);
        // Include the conflicting generic defaults that the production adapter must override.
        Map<String, String> cli = new LinkedHashMap<>(Map.of(
                "replicaCount", "1", "service.port", config.getServicePort().toString(),
                "service.type", "ClusterIP", "persistence.enabled", "false",
                "securityContext.runAsNonRoot", "false", "autoscaling.enabled", "false",
                "resources.requests.cpu", "0.1", "resources.requests.memory", "256Mi"));
        String cidr = K8sIngressPolicy.validate(request, config);
        BuiltInHelmPolicy.configure(chart, request, cli, yaml);
        K8sIngressPolicy.configureValues(app.chart(), cli, yaml, config, cidr);
        Path archive = BuiltInHelmCharts.packageChart(app);
        Path values = null;
        try {
            values = Files.createTempFile("builtin-pipeline-", ".yaml");
            Files.writeString(values, new Yaml().dump(yaml));
            List<String> command = new ArrayList<>(List.of("helm", "template", "pipeline", archive.toString(),
                    "--namespace", "default", "--values", values.toString()));
            command.addAll(HelmChartService.buildHelmSetArguments(cli));
            var process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String text = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(process.waitFor()).as("%s\n%s", command, text).isZero();
            List<Map<String, Object>> docs = new ArrayList<>();
            for (Object doc : new Yaml().loadAll(text)) if (doc instanceof Map) docs.add(map(doc));
            return new Rendered(text, docs);
        } finally {
            Files.deleteIfExists(archive);
            if (values != null) Files.deleteIfExists(values);
        }
    }

    private record Rendered(String text, List<Map<String, Object>> docs) {
        Map<String, Object> ofKind(String kind) {
            return docs.stream().filter(doc -> kind.equals(doc.get("kind"))).findFirst().orElseThrow();
        }
        List<Object> kinds() { return docs.stream().map(doc -> doc.get("kind")).toList(); }
    }
    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) { return (Map<String, Object>) value; }
    private static Object first(Object value) { return ((List<?>) value).get(0); }
}
