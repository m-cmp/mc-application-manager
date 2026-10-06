package kr.co.mcmp.softwarecatalog.kubernetes.service;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Server configuration; never accept credential paths or endpoints from an application request. */
@Component
@ConfigurationProperties(prefix = "app.nhn-cinder-addon")
@Getter @Setter
public class NhnCinderAddonProperties {
    private boolean enabled;
    private String openBaoUrl;
    private String openBaoTokenFile;
    private int readyTimeoutSeconds = 900;
    private int pollSeconds = 5;
    private List<Binding> bindings = new ArrayList<>();

    @Getter @Setter
    public static class Binding {
        private String namespace;
        private String connectionName;
        private String secretPath;
        private String region;
    }

    Binding binding(String namespace, String connectionName) {
        var matches = bindings.stream().filter(p -> namespace.equals(p.namespace)
                && connectionName != null && connectionName.equals(p.connectionName)).toList();
        if (!enabled || matches.size() != 1)
            throw new StorageOperationException(503, "NHN_ADDON_NOT_CONFIGURED",
                    "NHN add-on installation is not configured for this project and cloud connection.");
        var binding = matches.get(0);
        if (binding.secretPath == null || !binding.secretPath.matches("secret/data/(?:csp/nhn|users/[a-z0-9][a-z0-9-]{0,63}/csp/nhn)")
                || binding.region == null || !binding.region.matches("(?i)(?:kr[123]|jp1)"))
            throw new StorageOperationException(503, "NHN_ADDON_NOT_CONFIGURED", "Configure the NHN credential binding and region on AM.");
        return binding;
    }

    void validateTiming() {
        if (readyTimeoutSeconds < 1 || readyTimeoutSeconds > 3600 || pollSeconds < 1 || pollSeconds > 60)
            throw new StorageOperationException(503, "NHN_ADDON_NOT_CONFIGURED", "Configure an NHN readiness timeout of 1–3600 seconds and a polling interval of 1–60 seconds.");
    }
}
