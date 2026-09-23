package kr.co.mcmp.softwarecatalog.kubernetes.service;

import java.util.*;
import org.junit.jupiter.api.Test;
import io.fabric8.kubernetes.api.model.storage.StorageClassBuilder;
import io.fabric8.kubernetes.client.server.mock.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import kr.co.mcmp.softwarecatalog.application.dto.*;
import static org.assertj.core.api.Assertions.*;

@EnableKubernetesMockClient(crud = true)
class BuiltInHelmPolicyTest {
    KubernetesClient client;
    @Test void persistentAppsRejectHttpIngressHpaAndMultipleReplicas() {
        for (String app : List.of("redis", "mariadb", "postgresql")) {
            var chart = BuiltInHelmChartsTest.chart(app);
            var config = DeploymentConfigDTO.builder().minReplicas(1).hpaEnabled(false).ingressEnabled(false).build();
            assertThatCode(() -> BuiltInHelmPolicy.validate(chart, config)).doesNotThrowAnyException();
            assertThatThrownBy(() -> BuiltInHelmPolicy.validate(chart, config.toBuilder().ingressEnabled(true).build())).hasMessageContaining("TCP");
            assertThatThrownBy(() -> BuiltInHelmPolicy.validate(chart, config.toBuilder().hpaEnabled(true).build())).hasMessageContaining("HPA");
            assertThatThrownBy(() -> BuiltInHelmPolicy.validate(chart, config.toBuilder().minReplicas(2).build())).hasMessageContaining("one persistent");
        }
    }
    @Test void storageChecksMissingClassSizeAndProviderMinimum() {
        var chart = BuiltInHelmChartsTest.chart("mariadb");
        assertThatThrownBy(() -> BuiltInHelmPolicy.validateStorage(chart, client, new DeploymentRequest())).hasMessageContaining("Select a StorageClass");
        var req = DeploymentRequest.builder().additionalConfig(Map.of("storageClass", "missing")).build();
        assertThatThrownBy(() -> BuiltInHelmPolicy.validateStorage(chart, client, req)).hasMessageContaining("existing StorageClass");
        client.storage().v1().storageClasses().resource(new StorageClassBuilder().withNewMetadata().withName("ali-ssd").endMetadata()
                .withProvisioner("diskplugin.csi.alibabacloud.com").addToParameters("type", "cloud_ssd").build()).create();
        req.setAdditionalConfig(Map.of("storageClass", "ali-ssd", "storageSize", "10Gi"));
        assertThatThrownBy(() -> BuiltInHelmPolicy.validateStorage(chart, client, req)).hasMessageContaining("20Gi");
        req.setAdditionalConfig(Map.of("storageClass", "ali-ssd", "storageSize", "20Gi"));
        assertThatCode(() -> BuiltInHelmPolicy.validateStorage(chart, client, req)).doesNotThrowAnyException();
        for (String size : List.of("0Gi", "-1Gi", "1", "1Gi,service.type=LoadBalancer", "1.5Gi", "10000Gi"))
            assertThatThrownBy(() -> BuiltInHelmPolicy.size(Map.of("storageSize", size))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void preservesPersistenceAndTypedStorageClassWhileLeavingOtherChartsAlone() {
        Map<String, String> cli = new HashMap<>(Map.of("persistence.enabled", "false"));
        Map<String, Object> yaml = new HashMap<>();
        var req = DeploymentRequest.builder().additionalConfig(Map.of("storageClass", "standard", "storageSize", "1Gi")).build();
        BuiltInHelmPolicy.configure(BuiltInHelmChartsTest.chart("redis"), req, cli, yaml);
        assertThat(cli).containsEntry("persistence.enabled", "true").containsEntry("securityContext.runAsNonRoot", "true");
        assertThat(yaml.get("persistence")).isEqualTo(Map.of("enabled", true, "storageClass", "standard", "size", "1Gi", "retain", true));
        var external = BuiltInHelmChartsTest.chart("redis"); external.setRepositoryName("external");
        cli.clear(); yaml.clear();
        BuiltInHelmPolicy.configure(external, req, cli, yaml);
        assertThat(cli).isEmpty(); assertThat(yaml).isEmpty();
    }
}
