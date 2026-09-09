package kr.co.mcmp.softwarecatalog.kubernetes.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class HelmChartServiceImageDefaultsTest {

    @ParameterizedTest
    @MethodSource("legacyImageOverrides")
    void excludesLegacyImageOverridesFromHelmInstallArguments(String key, String value) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("service.port", "8080");
        values.put(key, value);

        List<String> arguments = HelmChartService.buildHelmSetArguments(values);

        assertThat(arguments)
                .containsExactly("--set", "service.port=8080")
                .doesNotContain(key + "=" + value);
    }

    private static Stream<Arguments> legacyImageOverrides() {
        return Stream.of(
                Arguments.of("global.security.allowInsecureImages", "true"),
                Arguments.of("global.imageRegistry", "docker.io"),
                Arguments.of("image.registry", "docker.io"),
                Arguments.of("image.repository", "10.0.0.5:5000/docker-hosted/jenkins:2.452"),
                Arguments.of("image.tag", "latest"),
                Arguments.of("image.pullPolicy", "IfNotPresent"));
    }

    @Test
    void preservesNonImageDeploymentValues() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("replicaCount", "2");
        values.put("service.type", "ClusterIP");
        values.put("resources.requests.memory", "512Mi");
        values.put("persistence.enabled", "false");

        assertThat(HelmChartService.buildHelmSetArguments(values))
                .containsExactly(
                        "--set", "replicaCount=2",
                        "--set", "service.type=ClusterIP",
                        "--set", "resources.requests.memory=512Mi",
                        "--set", "persistence.enabled=false");
    }

    @Test
    void handlesAnEmptyValuesMapWithoutAddingImageArguments() {
        assertThat(HelmChartService.buildHelmSetArguments(Map.of())).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("validKubernetesNamespaces")
    void preservesAnExplicitKubernetesNamespace(String namespace) {
        assertThat(HelmChartService.requireHelmNamespace(namespace)).isEqualTo(namespace);
    }

    private static Stream<String> validKubernetesNamespaces() {
        return Stream.of("default", "application-team-a", "ingress-system");
    }

    @ParameterizedTest
    @MethodSource("blankKubernetesNamespaces")
    void rejectsABlankKubernetesNamespace(String namespace) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> HelmChartService.requireHelmNamespace(namespace))
                .withMessageContaining("cannot be blank");
    }

    private static Stream<String> blankKubernetesNamespaces() {
        return Stream.of((String) null, "", " ", "\t");
    }
}
