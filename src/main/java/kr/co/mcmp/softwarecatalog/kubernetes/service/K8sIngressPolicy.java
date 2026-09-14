package kr.co.mcmp.softwarecatalog.kubernetes.service;

import java.util.*;
import org.yaml.snakeyaml.Yaml;
import kr.co.mcmp.softwarecatalog.application.dto.DeploymentConfigDTO;
import kr.co.mcmp.softwarecatalog.application.dto.DeploymentRequest;

/** Common policy for application Ingresses, independent of Object Storage. */
final class K8sIngressPolicy {
    private K8sIngressPolicy() {}

    static String validate(DeploymentRequest request, DeploymentConfigDTO config) {
        if (!config.isIngressEnabled()) {
            if (Boolean.TRUE.equals(request.getOpenServicePort()) || (request.getServicePortCidr() != null && !request.getServicePortCidr().isBlank()))
                throw new IllegalArgumentException("Enable Ingress to configure external CIDR access.");
            return null;
        }
        if (!"nginx".equals(config.getIngressClass()) && !IbmIngressSupport.managed(config.getIngressClass()))
            throw new IllegalArgumentException("CIDR access requires nginx or an IBM managed NGINX ingress class.");
        if (config.getIngressHost() == null || config.getIngressHost().isBlank() || "localhost".equals(config.getIngressHost()))
            throw new IllegalArgumentException("Enter an Ingress hostname for external access.");
        return K8sIngressAccessService.validateCidr(request.getServicePortCidr());
    }

    @SuppressWarnings("unchecked")
    static void configureValues(String chart, Map<String,String> values, Map<String,Object> file,
                                DeploymentConfigDTO config, String cidr) {
        if (cidr == null) return;
        String root = switch (chart.toLowerCase(Locale.ROOT)) {
            case "rclone" -> "ingress.main";
            case "prometheus" -> "server.ingress";
            case "loki" -> "gateway.ingress";
            default -> "ingress";
        };
        // Preserve the repository-aware adapter's schema, including generic
        // structured hosts and Bitnami's boolean tls plus extraTls. Loki keeps
        // the gateway mapping already supported by the deployment policy.
        if (!"loki".equalsIgnoreCase(chart)) {
            for (String candidate : List.of("ingress.main", "server.ingress", "ingress")) {
                Map<?, ?> mapped = file;
                for (String key : candidate.split("\\.")) mapped = map(mapped.get(key));
                if (mapped.containsKey("enabled")) {
                    root = candidate;
                    break;
                }
            }
        }
        // Replace generic settings with the chart's native values shape.
        String ingressRoot = root;
        values.keySet().removeIf(k -> k.startsWith("ingress.") || k.startsWith(ingressRoot + "."));
        Map<String,Object> ingress = file;
        for (String key : root.split("\\.")) {
            Map<String,Object> child = new LinkedHashMap<>((Map<String,Object>) map(ingress.get(key)));
            ingress.put(key, child);
            ingress = child;
        }
        boolean chartMapped = ingress.containsKey("enabled");
        Map<String,Object> annotations = new LinkedHashMap<>((Map<String,Object>) map(ingress.get("annotations")));
        annotations.put(K8sIngressAccessService.CIDR_ANNOTATION, cidr);
        annotations.put("kubernetes.io/ingress.class", config.getIngressClass());
        if (IbmIngressSupport.managed(config.getIngressClass()) && !config.isTlsEnabled()) {
            // Per application only: leave the shared controller and existing HTTPS routes unchanged.
            annotations.put("nginx.ingress.kubernetes.io/ssl-redirect", "false");
            annotations.put("nginx.ingress.kubernetes.io/force-ssl-redirect", "false");
        }
        ingress.put("annotations", annotations);
        if (chartMapped) return;
        ingress.put("enabled", true);
        ingress.put("ingressClassName", config.getIngressClass());
        ingress.put("className", config.getIngressClass());
        ingress.put("path", config.getIngressPath());
        ingress.put("pathType", "Prefix");
        ingress.put("hostname", config.getIngressHost());
        boolean structuredHosts = "rclone".equalsIgnoreCase(chart) || "loki".equalsIgnoreCase(chart);
        ingress.put("hosts", structuredHosts
                ? List.of(Map.of("host", config.getIngressHost(), "paths", List.of(Map.of("path", config.getIngressPath(), "pathType", "Prefix"))))
                : List.of(config.getIngressHost()));
        if (config.isTlsEnabled()) {
            if (config.getIngressTlsSecret() == null || config.getIngressTlsSecret().isBlank())
                throw new IllegalArgumentException("Specify a TLS Secret for the application Ingress.");
            ingress.put("tls", List.of(Map.of("secretName", config.getIngressTlsSecret(), "hosts", List.of(config.getIngressHost()))));
        }
    }

    static void verifyManifest(String manifest, String cidr) {
        verifyManifest(manifest, cidr, null);
    }

    static void verifyManifest(String manifest, String cidr, String expectedHost) {
        verifyManifest(manifest, cidr, expectedHost, "nginx");
    }

    static void verifyManifest(String manifest, String cidr, String expectedHost, String expectedClass) {
        if (cidr == null) return;
        int routes = 0;
        boolean hostFound = expectedHost == null;
        for (Object doc : new Yaml().loadAll(manifest)) {
            if (!(doc instanceof Map<?,?> resource)) continue;
            Map<?,?> spec = map(resource.get("spec"));
            if ("Ingress".equals(resource.get("kind"))) {
                routes++;
                if (spec.get("rules") instanceof List<?> rules)
                    for (Object rule : rules) if (Objects.equals(expectedHost, map(rule).get("host"))) hostFound = true;
                Map<?,?> annotations = map(map(resource.get("metadata")).get("annotations"));
                Object clazz = spec.get("ingressClassName");
                if (clazz == null) clazz = annotations.get("kubernetes.io/ingress.class");
                if (!Objects.equals(expectedClass, clazz)
                        || (annotations.containsKey("kubernetes.io/ingress.class") && !Objects.equals(expectedClass, annotations.get("kubernetes.io/ingress.class")))
                        || !cidr.equals(annotations.get(K8sIngressAccessService.CIDR_ANNOTATION)))
                    throw new IllegalArgumentException("Helm chart did not render the required nginx Ingress CIDR restriction.");
            }
            if ("Service".equals(resource.get("kind")) && Set.of("LoadBalancer", "NodePort").contains(Objects.toString(spec.get("type"), "")))
                throw new IllegalArgumentException("Application Services must use ClusterIP to avoid bypassing Ingress CIDR restrictions.");
        }
        if (routes == 0) throw new IllegalArgumentException("Helm chart did not render an Ingress; add a compatible Ingress values mapping before exposing it.");
        if (!hostFound) throw new IllegalArgumentException("Helm chart did not render the requested Ingress hostname.");
    }

    private static Map<?,?> map(Object value) { return value instanceof Map<?,?> m ? m : Map.of(); }
}
