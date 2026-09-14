package kr.co.mcmp.softwarecatalog.kubernetes.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class HelmIngressPolicyMergeTest {
    private static final String CIDR = "203.0.113.8/32";

    static Stream<Arguments> chartsAndTlsModes() {
        return Stream.concat(HelmIngressValuesTest.charts(), Stream.of(
                Arguments.of("new-app", "https://example.com/charts", "1.0.0", "ingress"),
                Arguments.of("rclone", "https://example.com/mirror", "1.0.0", "ingress"),
                Arguments.of("prometheus", "https://example.com/mirror", "1.0.0", "ingress")))
                .flatMap(chart -> Stream.of(false, true).map(tls -> Arguments.of(
                        chart.get()[0], chart.get()[1], chart.get()[2], chart.get()[3], tls)));
    }

    @ParameterizedTest
    @MethodSource("chartsAndTlsModes")
    void addsCidrWithoutOverwritingChartSpecificHostsAndTls(
            String name, String url, String version, String root, boolean tls) {
        var chart = HelmIngressValuesTest.chart(name, url, version);
        var config = HelmIngressValuesTest.config();
        config.setIngressTlsEnabled(tls);
        config.setIngressTlsSecret("selected-cert");
        var file = HelmIngressValues.from(chart, config);
        var expected = new LinkedHashMap<>(HelmIngressValuesTest.ingress(file, root));
        var flat = new LinkedHashMap<String, String>();
        flat.put("ingress.enabled", "true");
        flat.put("service.type", "ClusterIP");

        K8sIngressPolicy.configureValues(name, flat, file, config, CIDR);

        var actual = new LinkedHashMap<>(HelmIngressValuesTest.ingress(file, root));
        Object annotations = actual.remove("annotations");
        assertThat(actual).containsExactlyEntriesOf(expected);
        assertThat(annotations).isEqualTo(Map.of(
                K8sIngressAccessService.CIDR_ANNOTATION, CIDR,
                "kubernetes.io/ingress.class", "nginx"));
        assertThat(flat).containsExactlyEntriesOf(Map.of("service.type", "ClusterIP"));
    }

    @Test
    void disabledIngressDoesNotAddAnExternalAccessRule() {
        var config = HelmIngressValuesTest.config();
        config.setIngressEnabled(false);
        var file = HelmIngressValues.from(HelmIngressValuesTest.chart(
                "new-app", "https://example.com/charts", "1.0.0"), config);

        K8sIngressPolicy.configureValues("new-app", new LinkedHashMap<>(), file, config, null);

        assertThat(file).isEqualTo(Map.of("ingress", Map.of("enabled", false)));
    }

    @Test
    void keepsTheRemoteLokiGatewayRouteWhenTheGenericAdapterIsPresent() {
        var config = HelmIngressValuesTest.config();
        var file = HelmIngressValues.from(HelmIngressValuesTest.chart(
                "loki", "https://grafana.github.io/helm-charts", "6.0.0"), config);
        K8sIngressPolicy.configureValues("loki", new LinkedHashMap<>(), file, config, CIDR);

        var gateway = HelmIngressValuesTest.ingress(file, "gateway.ingress");
        assertThat(gateway).containsEntry("enabled", true).containsEntry("className", "nginx");
        assertThat(((Map<?, ?>) gateway.get("annotations")).get(K8sIngressAccessService.CIDR_ANNOTATION))
                .isEqualTo(CIDR);
    }
}
