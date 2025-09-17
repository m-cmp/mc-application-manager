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
