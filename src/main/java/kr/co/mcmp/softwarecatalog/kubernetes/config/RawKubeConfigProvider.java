package kr.co.mcmp.softwarecatalog.kubernetes.config;

import java.util.Arrays;

import io.fabric8.kubernetes.client.Config;
import kr.co.mcmp.ape.cbtumblebug.dto.K8sClusterDto;

abstract class RawKubeConfigProvider implements KubeConfigProvider {
    private final String displayName;

    protected RawKubeConfigProvider(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public Config buildConfig(K8sClusterDto dto) {
        Config cfg = Config.fromKubeconfig(getOriginalKubeconfigYaml(dto));
        cfg.setTrustCerts(true);
        cfg.setConnectionTimeout(30_000);
        cfg.setRequestTimeout(30_000);
        return cfg;
    }

    @Override
    public String getOriginalKubeconfigYaml(K8sClusterDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("K8sClusterDto cannot be null");
        }

        if (dto.getAccessInfo() == null) {
            throw new IllegalStateException("AccessInfo is null for " + displayName + " cluster: " + dto.getName());
        }

        String kubeconfig = dto.getAccessInfo().getKubeconfig();
        if (kubeconfig == null || kubeconfig.trim().isEmpty()) {
            throw new IllegalStateException("Kubeconfig is null or empty for " + displayName + " cluster: " + dto.getName());
        }

        return normalizeKubeconfig(kubeconfig);
    }

    protected String normalizeKubeconfig(String kubeconfig) {
        return kubeconfig;
    }

    protected boolean matches(String providerName, String... supportedProviderNames) {
        if (providerName == null) {
            return false;
        }

        return Arrays.stream(supportedProviderNames)
                .anyMatch(providerName::equalsIgnoreCase);
    }
}
