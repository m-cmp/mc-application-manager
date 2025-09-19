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

    @Override
    public String getOriginalKubeconfigYaml(K8sClusterDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("K8sClusterDto cannot be null");
        }
        
        if (dto.getAccessInfo() == null) {
            throw new IllegalStateException("AccessInfo is null for Azure cluster: " + dto.getName());
        }
        
        String kubeconfig = dto.getAccessInfo().getKubeconfig();
        if (kubeconfig == null || kubeconfig.trim().isEmpty()) {
            throw new IllegalStateException("Kubeconfig is null or empty for Azure cluster: " + dto.getName());
        }
        
        return kubeconfig;
    }
}
