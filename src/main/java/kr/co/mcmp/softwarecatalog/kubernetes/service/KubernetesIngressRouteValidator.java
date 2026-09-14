package kr.co.mcmp.softwarecatalog.kubernetes.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressList;
import io.fabric8.kubernetes.client.KubernetesClient;
import kr.co.mcmp.softwarecatalog.application.dto.DeploymentConfigDTO;

/** Sequential preflight check; not a reservation or a concurrent-deployment lock. */
final class KubernetesIngressRouteValidator {
    private static final String LEGACY_CLASS_ANNOTATION = "kubernetes.io/ingress.class";

    private KubernetesIngressRouteValidator() {}

    static void assertAvailable(KubernetesClient client, DeploymentConfigDTO config) {
        assertAvailable(client, config, false);
    }

    static void assertAvailable(KubernetesClient client, DeploymentConfigDTO config, boolean dedicatedHost) {
        if (!config.isIngressEnabled()) return;

        IngressList existing;
        try {
            // An HTTP route is shared by a controller across namespaces, not by the TB project.
            existing = Objects.requireNonNull(client.network().v1().ingresses().inAnyNamespace().list(),
                    "The Kubernetes API returned no Ingress list");
        } catch (RuntimeException e) {
            // A failed list (for example RBAC 403) must not be mistaken for an empty cluster.
            throw new LookupException("Cannot check existing Ingress Host/Path routes; deployment stopped. "
                    + "Check connectivity and cluster-wide Ingress list permission.", e);
        }
        if (dedicatedHost && existing.getItems() != null) {
            // Native Jupyter already reserves the entire hostname, regardless of class/path.
            boolean taken = existing.getItems().stream().filter(Objects::nonNull)
                    .filter(i -> i.getSpec() != null && i.getSpec().getRules() != null)
                    .flatMap(i -> i.getSpec().getRules().stream()).filter(Objects::nonNull)
                    .anyMatch(rule -> normalizeHost(config.getIngressHost()).equals(normalizeHost(rule.getHost())));
            if (taken) throw new ConflictException("Ingress hostname is already in use. Jupyter requires a dedicated hostname.");
        }
        assertAvailable(existing.getItems(), config);
    }

    static void assertAvailable(List<Ingress> existing, DeploymentConfigDTO config) {
        if (!config.isIngressEnabled() || existing == null) return;
        String requestedHost = normalizeHost(config.getIngressHost());
        String requestedPath = normalizePath(config.getIngressPath());
        for (Ingress ingress : existing) {
            if (ingress == null || ingress.getSpec() == null || ingress.getSpec().getRules() == null) continue;
            String existingClass = ingress.getSpec().getIngressClassName();
            if (existingClass == null || existingClass.isBlank()) {
                Map<String, String> annotations = ingress.getMetadata() == null ? null : ingress.getMetadata().getAnnotations();
                existingClass = annotations == null ? null : annotations.get(LEGACY_CLASS_ANNOTATION);
            }
            // Classless legacy routes may be served by the controller; do not assume they are unused.
            if (existingClass != null && !existingClass.isBlank()
                    && !existingClass.equals(config.getIngressClass())) continue;

            for (var rule : ingress.getSpec().getRules()) {
                if (rule == null || rule.getHttp() == null || rule.getHttp().getPaths() == null
                        || !hostsOverlap(requestedHost, normalizeHost(rule.getHost()))) continue;
                for (var path : rule.getHttp().getPaths()) {
                    if (path != null && requestedPath.equals(normalizePath(path.getPath()))) {
                        String owner = ingress.getMetadata() == null ? "unknown"
                                : ingress.getMetadata().getNamespace() + "/" + ingress.getMetadata().getName();
                        throw new ConflictException("Ingress Host/Path conflict: " + requestedHost + requestedPath
                                + " (class " + config.getIngressClass() + ") is already covered by " + owner
                                + ". Choose a different Host or Path, or remove the existing Ingress first.");
                    }
                }
            }
        }
    }

    private static String normalizeHost(String host) {
        return host == null ? "" : host.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizePath(String path) {
        if (path == null || path.isEmpty()) return "/";
        // AM uses Prefix paths: /app and /app/ reserve the same route. Preserve case.
        int end = path.length();
        while (end > 1 && path.charAt(end - 1) == '/') end--;
        return path.substring(0, end);
    }

    private static boolean hostsOverlap(String first, String second) {
        if (first.isEmpty() || second.isEmpty() || first.equals(second)) return true;
        return wildcardMatches(first, second) || wildcardMatches(second, first);
    }

    private static boolean wildcardMatches(String wildcard, String host) {
        if (!wildcard.startsWith("*.") || host.startsWith("*.")) return false;
        String suffix = wildcard.substring(1);
        if (!host.endsWith(suffix)) return false;
        String label = host.substring(0, host.length() - suffix.length());
        return !label.isEmpty() && !label.contains(".");
    }

    static final class ConflictException extends IllegalArgumentException {
        ConflictException(String message) { super(message); }
    }

    static final class LookupException extends IllegalStateException {
        LookupException(String message, Throwable cause) { super(message, cause); }
    }
}
