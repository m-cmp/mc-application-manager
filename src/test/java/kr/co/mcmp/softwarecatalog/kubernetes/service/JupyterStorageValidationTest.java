package kr.co.mcmp.softwarecatalog.kubernetes.service;

import static org.assertj.core.api.Assertions.*;

import java.util.Map;
import io.fabric8.kubernetes.api.model.storage.StorageClassBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JupyterStorageValidationTest {
    private final KubernetesServer server = new KubernetesServer(false, true);
    private KubernetesClient client;

    @BeforeEach void start() { server.before(); client = server.getClient(); }
    @AfterEach void stop() { server.after(); }

    @Test void distinguishesMissingClassFromApiFailureAndRequiresDynamicProvisioning() {
        assertThatThrownBy(() -> JupyterStorageValidation.validate(client,
                Map.of("storageClass", "missing", "storageSize", "10Gi")))
                .isInstanceOf(StorageOperationException.class)
                .hasMessageContaining("no longer exists");

        client.storage().v1().storageClasses().resource(new StorageClassBuilder()
                .withNewMetadata().withName("local").endMetadata()
                .withProvisioner("kubernetes.io/no-provisioner").build()).create();
        assertThatThrownBy(() -> JupyterStorageValidation.validate(client,
                Map.of("storageClass", "local", "storageSize", "10Gi")))
                .hasMessageContaining("dynamic provisioning");
    }

    @Test void enforcesKnownAlibabaMinimumAndAcceptsNhnTenGi() {
        client.storage().v1().storageClasses().resource(new StorageClassBuilder()
                .withNewMetadata().withName("alibaba").endMetadata()
                .withProvisioner("diskplugin.csi.alibabacloud.com")
                .withParameters(Map.of("type", "cloud_efficiency")).build()).create();
        assertThatThrownBy(() -> JupyterStorageValidation.validate(client,
                Map.of("storageClass", "alibaba", "storageSize", "10Gi")))
                .hasMessageContaining("at least 20Gi");
        assertThatCode(() -> JupyterStorageValidation.validate(client,
                Map.of("storageClass", "alibaba", "storageSize", "20Gi"))).doesNotThrowAnyException();

        client.storage().v1().storageClasses().resource(new StorageClassBuilder()
                .withNewMetadata().withName("nhn-hdd").endMetadata()
                .withProvisioner("cinder.csi.openstack.org")
                .withParameters(Map.of("type", "General HDD")).build()).create();
        assertThatCode(() -> JupyterStorageValidation.validate(client,
                Map.of("storageClass", "nhn-hdd", "storageSize", "10Gi"))).doesNotThrowAnyException();
    }
}
