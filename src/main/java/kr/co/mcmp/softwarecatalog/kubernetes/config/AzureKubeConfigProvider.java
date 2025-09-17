package kr.co.mcmp.softwarecatalog.kubernetes.config;

import org.springframework.stereotype.Component;

import io.fabric8.kubernetes.client.Config;
import kr.co.mcmp.ape.cbtumblebug.dto.K8sClusterDto;

@Component
public class AzureKubeConfigProvider implements KubeConfigProvider {
    @Override
    public Config buildConfig(K8sClusterDto dto) {
        String yaml = dto.getAccessInfo().getKubeconfig();
        Config cfg = Config.fromKubeconfig(yaml);
        cfg.setTrustCerts(true);
        cfg.setConnectionTimeout(30_000);
        cfg.setRequestTimeout(30_000);
        return cfg;
    }

    @Override
    public boolean supports(String providerName) {
        return "azure".equalsIgnoreCase(providerName);
    }
}
