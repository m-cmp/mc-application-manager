package kr.co.mcmp.softwarecatalog.kubernetes.service;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.yaml.snakeyaml.Yaml;
import static org.assertj.core.api.Assertions.*;

class BuiltInHelmRenderTest {
    @BeforeAll static void requireHelm() {
        try { Assumptions.assumeTrue(new ProcessBuilder("helm", "version", "--short").start().waitFor() == 0, "Helm CLI required"); }
        catch (Exception e) { Assumptions.abort("Helm CLI not installed"); }
    }
    @Test void rendersAllFiveWithReadinessNonRootInternalServicesAndPersistentSecrets() throws Exception {
        for (var app : BuiltInHelmCharts.APPS) {
            Path path = BuiltInHelmCharts.packageChart(app);
            try {
                run(true, "lint", path.toString(), "--strict");
                String output = run(true, "template", "smoke-" + app.chart(), path.toString(), "--namespace", "default");
                List<Map<String, Object>> docs = new ArrayList<>();
                for (Object obj : new Yaml().loadAll(output)) if (obj instanceof Map) docs.add((Map<String, Object>)obj);
                Map<String, Object> deploy = ofKind(docs, "Deployment");
                Map<String, Object> pod = map(map(map(deploy.get("spec")).get("template")).get("spec"));
                assertThat(map(pod.get("securityContext"))).containsEntry("runAsNonRoot", true);
                var container = map(((List<?>)pod.get("containers")).get(0));
                assertThat(container).containsKeys("readinessProbe", "livenessProbe");
                assertThat(map(ofKind(docs, "Service").get("spec"))).containsEntry("type", "ClusterIP");
                if (app.persistent()) {
                    assertThat(ofKind(docs, "PersistentVolumeClaim")).isNotNull();
                    assertThat(map(ofKind(docs, "Secret").get("data"))).containsKeys("password", "root-password");
                    run(false, "template", "bad", path.toString(), "--set", "replicaCount=2");
                    run(false, "template", "bad", path.toString(), "--set", "autoscaling.enabled=true");
                    run(false, "template", "bad", path.toString(), "--set", "ingress.enabled=true");
                    run(false, "template", "bad", path.toString(), "--set", "persistence.enabled=false");
                } else {
                    for (String clazz : List.of("nginx", "public-iks-k8s-nginx")) {
                        String ingress = run(true, "template", "web", path.toString(), "--set", "ingress.enabled=true",
                                "--set", "ingress.className=" + clazz, "--set", "ingress.hosts[0].host=app.example.com",
                                "--set", "ingress.hosts[0].paths[0].path=/", "--set", "ingress.hosts[0].paths[0].pathType=Prefix",
                                "--set", "ingress.tls[0].secretName=test-tls", "--set", "ingress.tls[0].hosts[0]=app.example.com");
                        assertThat(ingress).contains("kind: Ingress", clazz, "test-tls", "app.example.com");
                    }
                    assertThat(run(true, "template", "web", path.toString(), "--set", "autoscaling.enabled=true"))
                            .contains("kind: HorizontalPodAutoscaler");
                }
                run(false, "template", "bad", path.toString(), "--set", "service.type=LoadBalancer");
            } finally { Files.deleteIfExists(path); }
        }
    }
    private Map<String, Object> ofKind(List<Map<String, Object>> docs, String kind) {
        return docs.stream().filter(d -> kind.equals(d.get("kind"))).findFirst().orElseThrow();
    }
    private static Map<String, Object> map(Object value) { return (Map<String, Object>)value; }
    private String run(boolean success, String... args) throws Exception {
        List<String> cmd = new ArrayList<>(List.of("helm")); cmd.addAll(List.of(args));
        var process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor() == 0).as("%s\n%s", cmd, output).isEqualTo(success);
        return output;
    }
}
