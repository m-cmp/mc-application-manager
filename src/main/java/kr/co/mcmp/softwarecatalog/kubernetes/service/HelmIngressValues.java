package kr.co.mcmp.softwarecatalog.kubernetes.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.Yaml;

import kr.co.mcmp.softwarecatalog.application.dto.DeploymentConfigDTO;
import kr.co.mcmp.softwarecatalog.application.model.HelmChart;

/**
 * Chart-specific adapters for AM's single HTTP route, with a helm-create style fallback.
 * Repository aliases are not identities.
 * Render-tested versions are documented in doc/helm-ingress-mapping.md.
 * Use typed YAML values, not --set, to preserve lists, booleans and user-provided strings.
 */
final class HelmIngressValues {
    private static final String DNS_LABEL = "[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?";
    private static final Pattern DNS_NAME = Pattern.compile(DNS_LABEL + "(?:\\." + DNS_LABEL + ")*");

    private HelmIngressValues() {}

    static Map<String, Object> from(HelmChart chart, DeploymentConfigDTO config) {
        Profile profile = Profile.find(chart);
        validate(chart, config);

        Map<String, Object> ingress = new LinkedHashMap<>();
        ingress.put("enabled", config.isIngressEnabled());
        if (config.isIngressEnabled()) {
            String host = config.getIngressHost();
            if (config.isTlsEnabled() && config.getIngressTlsSecret() == null) {
                throw new IllegalStateException("Resolve the TLS Secret using the actual Helm release name before building values");
            }
            ingress.put((profile == Profile.GENERIC || profile == Profile.CLOUDPIRATES_NGINX) ? "className" : "ingressClassName", config.getIngressClass());
            List<Map<String, Object>> tls = config.isTlsEnabled()
                    ? List.of(Map.of("secretName", config.getIngressTlsSecret(), "hosts", List.of(host)))
                    : List.of();

            switch (profile) {
                case GRAFANA, PROMETHEUS -> {
                    ingress.put("hosts", List.of(host));
                    ingress.put("path", config.getIngressPath());
                    ingress.put("pathType", "Prefix");
                    ingress.put("tls", tls);
                }
                case RCLONE, CLOUDPIRATES_NGINX, GENERIC -> {
                    ingress.put("hosts", List.of(Map.of("host", host, "paths", List.of(
                            Map.of("path", config.getIngressPath(), "pathType", "Prefix")))));
                    ingress.put("tls", tls);
                }
                case BITNAMI_NGINX -> {
                    ingress.put("hostname", host);
                    ingress.put("path", config.getIngressPath());
                    ingress.put("pathType", "Prefix");
                    // Bitnami's tls=true uses <hostname>-tls. extraTls honors the selected Secret.
                    ingress.put("tls", false);
                    ingress.put("selfSigned", false);
                    ingress.put("extraTls", tls);
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        switch (profile) {
            case GRAFANA, BITNAMI_NGINX, CLOUDPIRATES_NGINX, GENERIC -> result.put("ingress", ingress);
            case PROMETHEUS -> result.put("server", Map.of("ingress", ingress));
            case RCLONE -> result.put("ingress", Map.of("main", ingress));
        }
        if (profile == Profile.CLOUDPIRATES_NGINX) {
            // Non-root nginx must not rely on a provider allowing privileged container ports.
            result.put("containerPorts", List.of(Map.of("name", "http", "containerPort", 8080, "protocol", "TCP")));
            result.put("serverConfig", """
                    server {
                        listen 8080;
                        server_name _;
                        root /usr/share/nginx/html;
                        index index.html index.htm;
                        location / { try_files $uri $uri/ =404; }
                    }
                    """);
            // This chart uses a port array; the generic service.port value is ignored upstream.
            result.put("service", Map.of("ports", List.of(Map.of(
                    "name", "http", "port", config.getServicePort(), "targetPort", "http", "protocol", "TCP"))));
        }
        return result;
    }

    /** Read-only validation also used by Spec Check, before a release name exists. */
    static void validate(HelmChart chart, DeploymentConfigDTO config) {
        BuiltInHelmPolicy.validate(chart, config);
        if (!config.isIngressEnabled()) return;
        String host = config.getIngressHost();
        String dnsHost = host != null && host.startsWith("*.") ? host.substring(2) : host;
        if (!isDnsName(dnsHost) || host.length() > 253 || host.matches("[0-9]+(?:\\.[0-9]+){3}")) {
            throw new IllegalArgumentException("Ingress Host must be a DNS name, optionally starting with '*.'");
        }
        String path = config.getIngressPath();
        if (path == null || !path.startsWith("/") || path.chars().anyMatch(Character::isWhitespace)
                || path.contains("?") || path.contains("#")) {
            throw new IllegalArgumentException("Ingress Path must start with '/' and contain no whitespace, query or fragment");
        }
        if (!isDnsName(config.getIngressClass())) {
            throw new IllegalArgumentException("Ingress Class must be a non-empty DNS name");
        }
        if (config.isTlsEnabled() && config.getIngressTlsSecret() != null
                && !isDnsName(config.getIngressTlsSecret())) {
            throw new IllegalArgumentException("TLS Secret name must be a valid DNS name, or omitted to use <release-name>-tls");
        }
        // These upstream templates emit some scalars without YAML quotes.
        Profile profile = Profile.find(chart);
        if (profile == Profile.PROMETHEUS) {
            if (host.startsWith("*.")) {
                throw new IllegalArgumentException("Prometheus Ingress does not support wildcard Host in the verified chart template; use an explicit DNS name");
            }
            requirePlainYamlString(host, "Host");
        }
        if (profile == Profile.GRAFANA || profile == Profile.PROMETHEUS || profile == Profile.CLOUDPIRATES_NGINX || profile == Profile.GENERIC) {
            requirePlainYamlString(config.getIngressClass(), "Class");
        }
    }

    /** Preserve the original null-only fallback without modifying the request/catalog config. */
    static DeploymentConfigDTO resolveTlsConfig(DeploymentConfigDTO config, String releaseName) {
        if (!config.isIngressEnabled() || !config.isTlsEnabled() || config.getIngressTlsSecret() != null) return config;
        if (!isDnsName(releaseName) || !isDnsName(releaseName + "-tls")) {
            throw new IllegalArgumentException("A valid Helm release name is required to select the default TLS Secret");
        }
        return config.toBuilder().ingressTlsSecret(releaseName + "-tls").build();
    }

    private static boolean isDnsName(String value) {
        return value != null && value.length() <= 253 && DNS_NAME.matcher(value).matches();
    }

    private static void requirePlainYamlString(String value, String field) {
        if (!(new Yaml().load(value) instanceof String)) {
            throw new IllegalArgumentException("Ingress " + field + " must not be a YAML boolean or number in this chart template");
        }
    }

    private enum Profile {
        GRAFANA("grafana", "https://grafana.github.io/helm-charts"),
        PROMETHEUS("prometheus", "https://prometheus-community.github.io/helm-charts"),
        RCLONE("rclone", "https://jacobcolvin.com/helm-charts"),
        BITNAMI_NGINX("nginx", "https://charts.bitnami.com/bitnami"),
        CLOUDPIRATES_NGINX("nginx", "https://cloudpirates-io.github.io/helm-charts"),
        // Fallback only: a repository/chart with a different schema still needs an adapter.
        GENERIC(null, null);

        private final String chartName;
        private final String repositoryUrl;

        Profile(String chartName, String repositoryUrl) {
            this.chartName = chartName;
            this.repositoryUrl = repositoryUrl;
        }

        static Profile find(HelmChart chart) {
            String url = chart.getChartRepositoryUrl();
            if (url == null) {
                return GENERIC;
            }
            url = url.trim().replaceAll("/+$", "");
            for (Profile profile : values()) {
                if (profile != GENERIC && profile.chartName.equals(chart.getChartName()) && profile.repositoryUrl.equals(url)) {
                    return profile;
                }
            }
            return GENERIC;
        }
    }
}
