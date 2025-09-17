package kr.co.mcmp.softwarecatalog.kubernetes.config;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import kr.co.mcmp.ape.cbtumblebug.api.CbtumblebugRestApi;
import kr.co.mcmp.ape.cbtumblebug.dto.K8sClusterDto;
import kr.co.mcmp.softwarecatalog.kubernetes.config.KubeConfigProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class KubernetesClientFactory {
    private final CbtumblebugRestApi api;
    private final KubeConfigProviderFactory providerFactory;

    public KubernetesClient getClient(String namespace, String clusterName) {
        try {
            K8sClusterDto dto = api.getK8sClusterByName(namespace, clusterName);
            String providerName = dto.getConnectionConfig().getProviderName();
            KubeConfigProvider provider = providerFactory.getProvider(providerName);
            return new KubernetesClientBuilder()
                    .withConfig(provider.buildConfig(dto))
                    .build();
        } catch (Exception e) {
            log.error("KubernetesClient 생성 실패 - namespace: {}, cluster: {}", namespace, clusterName, e);
            throw new RuntimeException(e);
        }
    }
}
