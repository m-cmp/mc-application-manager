package kr.co.mcmp.softwarecatalog.kubernetes.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.PreDestroy;

import org.springframework.stereotype.Component;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import kr.co.mcmp.ape.cbtumblebug.api.CbtumblebugRestApi;
import kr.co.mcmp.ape.cbtumblebug.dto.K8sClusterDto;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class KubernetesClientFactory {
    private final Map<String, KubernetesClient> clientCache = new ConcurrentHashMap<>();
    private final CbtumblebugRestApi api;

    public KubernetesClientFactory(CbtumblebugRestApi api) {
        this.api = api;
    }

    public synchronized KubernetesClient getClient(String namespace, String clusterName) {
        String cacheKey = namespace + ":" + clusterName;
        return clientCache.computeIfAbsent(cacheKey, k -> createKubernetesClient(namespace, clusterName));
    }

    public synchronized void releaseClient(String namespace, String clusterName) {
        String cacheKey = namespace + ":" + clusterName;
        KubernetesClient client = clientCache.remove(cacheKey);
        if (client != null) {
            client.close();
        }
    }

    private KubernetesClient createKubernetesClient(String namespace, String clusterName) {
        try {
            K8sClusterDto dto = api.getK8sClusterByName(namespace, clusterName);
            String kubeconfig = extractKubeconfig(dto);
            Config config = createKubernetesConfig(kubeconfig);
            return new KubernetesClientBuilder().withConfig(config).build();
        } catch (Exception e) {
            log.error("Kubernetes 클라이언트 생성 실패", e);
            throw new RuntimeException("Kubernetes 클라이언트 생성 중 오류 발생", e);
        }
    }
    
    private String extractKubeconfig(K8sClusterDto clusterDto) {
        return clusterDto.getCspViewK8sClusterDetail().getAccessInfo().getKubeconfig();
    }

    private Config createKubernetesConfig(String kubeconfig) throws IOException {
        Path tempConfigFile = createTempKubeconfigFile(kubeconfig);
        try {
            return Config.fromKubeconfig(readKubeconfigContent(tempConfigFile));
        } finally {
            deleteTempFile(tempConfigFile);
        }
    }

    private Path createTempKubeconfigFile(String kubeconfig) throws IOException {
        Path tempFile = Files.createTempFile("kubeconfig", ".yaml");
        Files.write(tempFile, kubeconfig.getBytes(StandardCharsets.UTF_8));
        return tempFile;
    }

    private String readKubeconfigContent(Path configFile) throws IOException {
        return new String(Files.readAllBytes(configFile));
    }

    private void deleteTempFile(Path tempFile) {
        try {
            Files.deleteIfExists(tempFile);
        } catch (IOException e) {
            log.warn("임시 파일 삭제 실패: {}", tempFile, e);
        }
    }

    public void closeClient(String clusterId) {
        Optional.ofNullable(clientCache.remove(clusterId))
                .ifPresent(KubernetesClient::close);
    }

    @PreDestroy
    public void closeAllClients() {
        clientCache.values().forEach(KubernetesClient::close);
        clientCache.clear();
    }
}