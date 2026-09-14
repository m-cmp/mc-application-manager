package kr.co.mcmp.softwarecatalog.kubernetes.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Operator configuration only. Never bind these fields from a deployment request. */
@Component
@ConfigurationProperties(prefix = "app.ibm-ingress.automation")
@Getter @Setter
public class IbmIngressAutomationProperties {
    private List<Profile> profiles = new ArrayList<>();
    private int readyTimeoutSeconds = 2400;
    private int pollSeconds = 10;
    private String certManagerVersion = "v1.20.2";
    private String helmExecutable = "helm";

    @Getter @Setter
    public static class Profile {
        private String namespace;
        private String connectionName;
        private String region;
        private String apiKeyFile;
        private boolean allowSharedLbChanges;
        private boolean installCertManager;
        private List<String> allowedDomains = new ArrayList<>();
        private String issuerTemplateFile;
        private List<String> dnsSecretFiles = new ArrayList<>();

        public boolean allowsHost(String host) {
            return validDomain(host) && allowedDomains.stream().anyMatch(domain -> validDomain(domain)
                    && (host.equals(domain) || host.endsWith("." + domain)));
        }
    }

    Profile profile(String namespace, String connectionName) {
        var matches = profiles.stream().filter(p -> namespace.equals(p.namespace)
                && connectionName != null && connectionName.equals(p.connectionName)).toList();
        if (matches.size() > 1) throw new IllegalArgumentException("Duplicate IBM automation profiles for the selected Project/connection.");
        return matches.isEmpty() ? null : matches.get(0);
    }

    static boolean validDomain(String value) {
        return value != null && value.length() <= 253 && value.contains(".") && !value.matches("[0-9.]+")
                && value.matches("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+");
    }

    static String readOperatorFile(String path, String description) {
        try {
            if (path == null || !Path.of(path).isAbsolute() || !Files.isRegularFile(Path.of(path))
                    || Files.size(Path.of(path)) > 65536) throw new IllegalArgumentException();
            return Files.readString(Path.of(path));
        } catch (Exception e) {
            // Do not expose paths, credentials, template content, or parser exceptions to API clients.
            throw new IllegalArgumentException("Configure a readable server-side " + description + " file (maximum 64 KiB).");
        }
    }
}
