package kr.co.mcmp.softwarecatalog.kubernetes.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import kr.co.mcmp.softwarecatalog.application.dto.DeploymentConfigDTO;

/** Advisory only: certificates may be provisioned asynchronously, outside AM. */
final class KubernetesIngressTlsWarnings {
    private KubernetesIngressTlsWarnings() {}

    static List<String> inspect(KubernetesClient client, String workloadNamespace, DeploymentConfigDTO config) {
        if (!config.isIngressEnabled() || !config.isTlsEnabled()) return List.of();
        String name = config.getIngressTlsSecret();
        if (name == null) {
            return List.of("TLS Secret was not specified. Deployment will reference <release-name>-tls in namespace "
                    + workloadNamespace + ". AM does not create certificates; provision that Secret externally.");
        }
        Secret secret;
        try {
            // Read only this Secret, never list Secrets or return their contents to the browser/logs.
            secret = client.secrets().inNamespace(workloadNamespace).withName(name).get();
        } catch (RuntimeException e) {
            return List.of("Cannot verify TLS Secret '" + name + "' in namespace " + workloadNamespace
                    + ". Check Secret read permission and connectivity. Deployment may continue; HTTPS is not verified.");
        }
        if (secret == null) {
            return List.of("TLS Secret '" + name + "' does not currently exist in namespace " + workloadNamespace
                    + ". Deployment may continue, but provision the certificate externally before using HTTPS.");
        }
        Map<String, String> data = secret.getData();
        if (!"kubernetes.io/tls".equals(secret.getType()) || data == null
                || Objects.toString(data.get("tls.crt"), "").isBlank() || Objects.toString(data.get("tls.key"), "").isBlank()) {
            return List.of("TLS Secret '" + name + "' in namespace " + workloadNamespace
                    + " is not a complete kubernetes.io/tls Secret (tls.crt and tls.key). Verify it before using HTTPS.");
        }
        // Existence/shape do not prove certificate validity, hostname coverage or external reachability.
        return List.of();
    }
}
