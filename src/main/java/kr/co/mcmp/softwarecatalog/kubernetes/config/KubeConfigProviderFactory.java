package kr.co.mcmp.softwarecatalog.kubernetes.config;

import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class KubeConfigProviderFactory {
    private final List<KubeConfigProvider> providers;

    public KubeConfigProviderFactory(List<KubeConfigProvider> providers) {
        this.providers = providers;
    }

    public KubeConfigProvider getProvider(String providerName) {
        return providers.stream()
                .filter(p -> p.supports(providerName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported CSP: " + providerName));
    }
}