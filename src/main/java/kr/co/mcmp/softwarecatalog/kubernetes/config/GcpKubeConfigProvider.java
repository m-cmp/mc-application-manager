package kr.co.mcmp.softwarecatalog.kubernetes.config;

import io.fabric8.kubernetes.client.Config;
import kr.co.mcmp.ape.cbtumblebug.dto.K8sClusterDto;
import org.springframework.stereotype.Component;

@Component
public class GcpKubeConfigProvider implements KubeConfigProvider {

    @Override
    public Config buildConfig(K8sClusterDto dto) {
        Config cfg = Config.fromKubeconfig(getOriginalKubeconfigYaml(dto));
        cfg.setTrustCerts(true);
        cfg.setConnectionTimeout(30_000);
        cfg.setRequestTimeout(30_000);
        return cfg;
    }

    @Override
    public boolean supports(String providerName) {
        return "gcp".equalsIgnoreCase(providerName);
    }

    @Override
    public String getOriginalKubeconfigYaml(K8sClusterDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("K8sClusterDto cannot be null");
        }

        if (dto.getAccessInfo() == null) {
            throw new IllegalStateException("AccessInfo is null for GCP cluster: " + dto.getName());
        }

        String kubeconfig = dto.getAccessInfo().getKubeconfig();
        if (kubeconfig == null || kubeconfig.trim().isEmpty()) {
            throw new IllegalStateException("Kubeconfig is null or empty for GCP cluster: " + dto.getName());
        }

        return kubeconfig;
    }

}
