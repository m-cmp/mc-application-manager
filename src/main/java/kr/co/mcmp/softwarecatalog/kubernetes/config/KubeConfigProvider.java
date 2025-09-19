package kr.co.mcmp.softwarecatalog.kubernetes.config;

import io.fabric8.kubernetes.client.Config;
import kr.co.mcmp.ape.cbtumblebug.dto.K8sClusterDto;

public interface KubeConfigProvider {
    Config buildConfig(K8sClusterDto dto);
    boolean supports(String providerName);
    String getOriginalKubeconfigYaml(K8sClusterDto dto);
}
