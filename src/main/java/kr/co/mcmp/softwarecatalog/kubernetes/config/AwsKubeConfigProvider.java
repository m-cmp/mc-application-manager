package kr.co.mcmp.softwarecatalog.kubernetes.config;

import io.fabric8.kubernetes.client.Config;
import kr.co.mcmp.ape.cbtumblebug.dto.K8sClusterDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class AwsKubeConfigProvider implements KubeConfigProvider {

    @Value("${spider.url}")
    private String spiderUrl;

    @Value("${spider.port}")
    private String spiderPort;

    @Value("${spider.checkhost}")
    private List<String> spiderChekdHosts;


    @Override
    public Config buildConfig(K8sClusterDto dto) {
        String yaml = dto.getAccessInfo().getKubeconfig();
        Config cfg = Config.fromKubeconfig(KubeConfigProviderFactory.replaceUrlHostByPort(yaml, spiderPort, spiderChekdHosts, spiderUrl));
        cfg.setTrustCerts(true);
        cfg.setConnectionTimeout(30_000);
        cfg.setRequestTimeout(30_000);
        return cfg;
    }

    @Override
    public boolean supports(String providerName) {
        return "aws".equalsIgnoreCase(providerName);
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

        return KubeConfigProviderFactory.replaceUrlHostByPort(kubeconfig, spiderPort, spiderChekdHosts, spiderUrl);
    }

}
