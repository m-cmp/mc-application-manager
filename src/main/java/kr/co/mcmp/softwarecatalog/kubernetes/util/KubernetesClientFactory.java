package kr.co.mcmp.softwarecatalog.kubernetes.util;

import org.springframework.stereotype.Component;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import kr.co.mcmp.ape.cbtumblebug.api.CbtumblebugRestApi;
import kr.co.mcmp.ape.cbtumblebug.dto.K8sClusterDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;



@Component
@Slf4j
@RequiredArgsConstructor
public class KubernetesClientFactory {
    private final CbtumblebugRestApi api;

    public KubernetesClient getClient(String namespace, String clusterName) {
        try {
            K8sClusterDto dto = api.getK8sClusterByName(namespace, clusterName);
            String cspType = dto.getConnectionConfig().getProviderName().toLowerCase();
            String kubeconfig = extractKubeconfig(dto);
            
            if ("gcp".equals(cspType)) {
                kubeconfig = processGcpKubeconfig(kubeconfig);
            }
            
            Config config = Config.fromKubeconfig(kubeconfig);
            configureClient(config, cspType);
            
            return new KubernetesClientBuilder()
                .withConfig(config)
                .build();
        } catch (Exception e) {
            log.error("Kubernetes 클라이언트 생성 실패 - namespace: {}, cluster: {}", 
                namespace, clusterName, e);
            throw new RuntimeException("Kubernetes 클라이언트 생성 중 오류 발생", e);
        }
    }

    private String processGcpKubeconfig(String kubeconfig) {
        String[] sections = kubeconfig.split("users:");
        if (sections.length < 2) {
            return kubeconfig;
        }

        // users 섹션 이전 부분 유지
        StringBuilder processedConfig = new StringBuilder(sections[0]);
        processedConfig.append("users:\n");
        processedConfig.append("- name: gke-user\n");
        processedConfig.append("  user:\n");
        processedConfig.append("    token: gke-token\n");

        // current-context 이후 부분 찾아서 추가
        String remaining = sections[1];
        int contextIndex = remaining.indexOf("current-context:");
        if (contextIndex >= 0) {
            processedConfig.append(remaining.substring(contextIndex));
        }

        return processedConfig.toString();
    }

    private void configureClient(Config config, String cspType) {
        config.setTrustCerts(true);
        config.setConnectionTimeout(30000);
        config.setRequestTimeout(30000);
        // config.setWebsocketTimeout(30000);
        
        if ("gcp".equals(cspType)) {
            config.setImpersonateUsername(null);
            config.setAuthProvider(null);
        }
    }

    private String extractKubeconfig(K8sClusterDto clusterDto) {
        if (clusterDto == null || clusterDto.getCspViewK8sClusterDetail() == null 
            || clusterDto.getCspViewK8sClusterDetail().getAccessInfo() == null) {
            throw new IllegalStateException("클러스터 정보가 올바르지 않습니다");
        }
        return clusterDto.getCspViewK8sClusterDetail().getAccessInfo().getKubeconfig();
    }

    public void releaseClient(KubernetesClient client) {
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                log.warn("클라이언트 해제 중 오류 발생", e);
            }
        }
    }
}
