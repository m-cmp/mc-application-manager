package kr.co.mcmp.softwarecatalog.kubernetes.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class KubernetesIngressTlsWarningsTest {
    private final KubernetesClient client = mock(KubernetesClient.class, RETURNS_DEEP_STUBS);

    @SuppressWarnings("unchecked")
    static Resource<Secret> stubSecret(KubernetesClient client, String namespace, String name) {
        MixedOperation<Secret, SecretList, Resource<Secret>> secrets = mock(MixedOperation.class);
        NonNamespaceOperation<Secret, SecretList, Resource<Secret>> scoped = mock(NonNamespaceOperation.class);
        Resource<Secret> selected = mock(Resource.class);
        when(client.secrets()).thenReturn(secrets);
        when(secrets.inNamespace(namespace)).thenReturn(scoped);
        when(scoped.withName(name)).thenReturn(selected);
        return selected;
    }

    @Test void missingSecretAndReadFailureOnlyWarnWithoutExposingServerContents() {
        var config = HelmIngressValuesTest.config();
        config.setIngressTlsEnabled(true); config.setIngressTlsSecret("external-cert");
        var selected = stubSecret(client, "default", "external-cert");
        when(selected.get()).thenReturn(null);
        assertThat(KubernetesIngressTlsWarnings.inspect(client, "default", config))
                .singleElement().asString().contains("does not currently exist", "may continue");
        when(selected.get()).thenThrow(new IllegalStateException("sensitive server contents"));
        assertThat(KubernetesIngressTlsWarnings.inspect(client, "default", config))
                .singleElement().asString().contains("read permission").doesNotContain("sensitive");
        verify(client.secrets(), never()).inNamespace("project-a");
    }

    @ParameterizedTest @ValueSource(strings = {"missing-key", "empty-cert", "wrong-type", "valid"})
    void inspectsSecretShapeWithoutReturningCertificateOrPrivateKey(String scenario) {
        var config = HelmIngressValuesTest.config();
        config.setIngressTlsEnabled(true); config.setIngressTlsSecret("external-cert");
        Map<String, String> data = switch (scenario) {
            case "missing-key" -> Map.of("tls.crt", "private-test-cert");
            case "empty-cert" -> Map.of("tls.crt", "", "tls.key", "private-test-key");
            default -> Map.of("tls.crt", "private-test-cert", "tls.key", "private-test-key");
        };
        when(stubSecret(client, "default", "external-cert").get()).thenReturn(new SecretBuilder()
                .withType(scenario.equals("wrong-type") ? "Opaque" : "kubernetes.io/tls").withData(data).build());
        var warnings = KubernetesIngressTlsWarnings.inspect(client, "default", config);
        if (scenario.equals("valid")) assertThat(warnings).isEmpty();
        else assertThat(warnings).singleElement().asString().contains("not a complete").doesNotContain("private-test");
    }

    @Test void httpAndDisabledIngressNeverReadSecrets() {
        var config = HelmIngressValuesTest.config();
        assertThat(KubernetesIngressTlsWarnings.inspect(client, "default", config)).isEmpty();
        config.setIngressEnabled(false); config.setIngressTlsEnabled(true);
        assertThat(KubernetesIngressTlsWarnings.inspect(client, "default", config)).isEmpty();
        verifyNoInteractions(client);
    }
}
