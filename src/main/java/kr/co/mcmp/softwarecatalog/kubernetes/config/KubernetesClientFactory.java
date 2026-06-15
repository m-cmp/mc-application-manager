package kr.co.mcmp.softwarecatalog.kubernetes.config;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class KubernetesClientFactory {
    private final KubeconfigResolver kubeconfigResolver;

    public KubernetesClient getClient(String namespace, String clusterName) {
        try {
            KubernetesClient client = new KubernetesClientBuilder()
                    .withConfig(kubeconfigResolver.buildConfig(namespace, clusterName))
                    .build();
            
            
            return client;
        } catch (Exception e) {
            log.error("KubernetesClient 생성 실패 - namespace: {}, cluster: {}", namespace, clusterName, e);
            throw new RuntimeException(e);
        }
    }
    
}
