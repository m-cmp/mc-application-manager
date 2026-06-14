package kr.co.mcmp.softwarecatalog.kubernetes.config;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import io.fabric8.kubernetes.client.Config;
import kr.co.mcmp.ape.cbtumblebug.api.CbtumblebugRestApi;
import kr.co.mcmp.ape.cbtumblebug.dto.K8sClusterDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class KubeconfigResolver {

    private final CbtumblebugRestApi cbtumblebugRestApi;
    private final KubeConfigProviderFactory providerFactory;

    public Config buildConfig(String namespace, String clusterName) {
        String kubeconfigYaml = getKubeconfigYaml(namespace, clusterName);
        Config config = Config.fromKubeconfig(kubeconfigYaml);
        config.setTrustCerts(true);
        config.setConnectionTimeout(30_000);
        config.setRequestTimeout(30_000);
        return config;
    }

    public String getKubeconfigYaml(String namespace, String clusterName) {
        K8sClusterDto clusterDto = cbtumblebugRestApi.getK8sClusterByName(namespace, clusterName);
        if (clusterDto == null) {
            throw new IllegalStateException("K8s cluster not found: " + clusterName);
        }

        String providerName = getProviderName(clusterDto);
        KubeConfigProvider provider = providerFactory.getProvider(providerName);

        if (!usesTumblebugNativeAuth(providerName)) {
            return provider.getOriginalKubeconfigYaml(clusterDto);
        }

        String kubeconfigYaml = cbtumblebugRestApi.getK8sClusterKubeconfig(namespace, clusterName);
        String token = cbtumblebugRestApi.getK8sClusterToken(namespace, clusterName);
        return injectToken(kubeconfigYaml, token);
    }

    private String getProviderName(K8sClusterDto clusterDto) {
        if (clusterDto.getConnectionConfig() == null
                || StringUtils.isBlank(clusterDto.getConnectionConfig().getProviderName())) {
            throw new IllegalStateException("ProviderName is empty for K8s cluster: " + clusterDto.getName());
        }
        return clusterDto.getConnectionConfig().getProviderName();
    }

    private boolean usesTumblebugNativeAuth(String providerName) {
        return StringUtils.equalsIgnoreCase(providerName, "aws")
                || StringUtils.equalsIgnoreCase(providerName, "gcp");
    }

    @SuppressWarnings("unchecked")
    private String injectToken(String kubeconfigYaml, String token) {
        if (StringUtils.isBlank(token)) {
            return kubeconfigYaml;
        }

        Yaml yaml = new Yaml();
        Object loaded = yaml.load(kubeconfigYaml);
        if (!(loaded instanceof Map<?, ?> rootMap)) {
            return kubeconfigYaml;
        }

        Map<String, Object> root = (Map<String, Object>) rootMap;
        Object usersObject = root.get("users");
        if (!(usersObject instanceof List<?> users)) {
            return kubeconfigYaml;
        }

        boolean updated = false;
        for (Object userObject : users) {
            if (!(userObject instanceof Map<?, ?> userEntryMap)) {
                continue;
            }

            Map<String, Object> userEntry = (Map<String, Object>) userEntryMap;
            Object userConfigObject = userEntry.get("user");
            if (!(userConfigObject instanceof Map<?, ?> userConfigMap)) {
                continue;
            }

            Map<String, Object> userConfig = (Map<String, Object>) userConfigMap;
            userConfig.remove("exec");
            userConfig.put("token", token);
            updated = true;
        }

        if (!updated) {
            log.warn("No kubeconfig users were updated with Tumblebug token");
            return kubeconfigYaml;
        }

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        return new Yaml(options).dump(root);
    }
}
