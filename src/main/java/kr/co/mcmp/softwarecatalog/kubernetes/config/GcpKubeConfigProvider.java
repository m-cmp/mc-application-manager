package kr.co.mcmp.softwarecatalog.kubernetes.config;

import io.fabric8.kubernetes.client.Config;
import kr.co.mcmp.ape.cbtumblebug.dto.K8sClusterDto;
import org.springframework.stereotype.Component;

@Component
public class GcpKubeConfigProvider implements KubeConfigProvider {

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
        
        String originalKubeconfig = dto.getAccessInfo().getKubeconfig();
        if (originalKubeconfig == null || originalKubeconfig.trim().isEmpty()) {
            throw new IllegalStateException("Kubeconfig is null or empty for GCP cluster: " + dto.getName());
        }
        
        // GCP의 경우 원본 kubeconfig를 그대로 반환 (Helm에서는 원본이 필요)
        return originalKubeconfig;
    }

    @Override
    public Config buildConfig(K8sClusterDto dto) {
        String yaml = dto.getAccessInfo().getKubeconfig();
        String patched = processGcpKubeconfig(yaml);
        Config cfg = Config.fromKubeconfig(patched);
        cfg.setTrustCerts(true);
        cfg.setConnectionTimeout(30_000);
        cfg.setRequestTimeout(30_000);
        // remove exec auth
        cfg.setImpersonateUsername(null);
        cfg.setAuthProvider(null);
        return cfg;
    }

    private String processGcpKubeconfig(String kubeconfig) {
        String[] parts = kubeconfig.split("users:");
        if (parts.length < 2) return kubeconfig;
        StringBuilder sb = new StringBuilder(parts[0])
            .append("users:\n")
            .append("- name: gke-user\n")
            .append("  user:\n")
            .append("    token: gke-token\n");
        int idx = parts[1].indexOf("current-context:");
        if (idx >= 0) {
            sb.append(parts[1].substring(idx));
        }
        return sb.toString();
    }
}
