package kr.co.mcmp.softwarecatalog.kubernetes.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.fabric8.kubernetes.api.model.authorization.v1.SelfSubjectAccessReviewBuilder;
import io.fabric8.kubernetes.api.model.storage.CSIDriverBuilder;
import io.fabric8.kubernetes.api.model.storage.CSINodeBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import kr.co.mcmp.ape.cbtumblebug.api.CbtumblebugRestApi;
import kr.co.mcmp.ape.cbtumblebug.dto.K8sClusterDto;
import kr.co.mcmp.softwarecatalog.kubernetes.config.KubernetesClientFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NhnStorageClassServiceTest {
    private final KubernetesServer server = new KubernetesServer(false, true);
    private KubernetesClient client;

    @BeforeEach void start() { server.before(); client = server.getClient(); }
    @AfterEach void stop() { server.after(); }

    @Test void blocksCreationWhenCinderDriverIsMissing() {
        var result = NhnStorageClassService.inspect(client);
        assertThat(result.supported()).isTrue();
        assertThat(result.driverReady()).isFalse();
        assertThat(result.canCreate()).isFalse();
        assertThat(result.message()).contains("cinder-csi-plugin");
    }

    @Test void reportsCreatePermissionOnlyAfterDriverIsRegistered() {
        client.storage().v1().csiDrivers().resource(new CSIDriverBuilder()
                .withNewMetadata().withName(NhnStorageClassService.DRIVER).endMetadata()
                .withNewSpec().withAttachRequired(true).endSpec().build()).create();
        client.storage().v1().csiNodes().resource(new CSINodeBuilder()
                .withNewMetadata().withName("worker-1").endMetadata().withNewSpec()
                .addNewDriver().withName(NhnStorageClassService.DRIVER).withNodeID("worker-1").endDriver()
                .endSpec().build()).create();
        server.expect().post().withPath("/apis/authorization.k8s.io/v1/selfsubjectaccessreviews")
                .andReturn(201, new SelfSubjectAccessReviewBuilder()
                        .withNewStatus().withAllowed(true).endStatus().build()).once();

        var result = NhnStorageClassService.inspect(client);
        assertThat(result.supported()).isTrue();
        assertThat(result.driverReady()).isTrue();
        assertThat(result.canCreate()).isTrue();
    }

    @Test void waitsUntilCinderIsRegisteredOnEveryWorker() {
        client.storage().v1().csiDrivers().resource(new CSIDriverBuilder()
                .withNewMetadata().withName(NhnStorageClassService.DRIVER).endMetadata()
                .withNewSpec().withAttachRequired(true).endSpec().build()).create();
        client.storage().v1().csiNodes().resource(new CSINodeBuilder()
                .withNewMetadata().withName("worker-1").endMetadata().withNewSpec()
                .addNewDriver().withName(NhnStorageClassService.DRIVER).withNodeID("worker-1").endDriver()
                .endSpec().build()).create();
        client.storage().v1().csiNodes().resource(new CSINodeBuilder()
                .withNewMetadata().withName("worker-2").endMetadata().withNewSpec().endSpec().build()).create();

        var result = NhnStorageClassService.inspect(client);

        assertThat(result.driverReady()).isFalse();
        assertThat(result.canCreate()).isFalse();
        assertThat(result.message()).contains("registering");
    }

    @Test void rejectsNonNhnClusterUsingTumblebugProviderMetadata() {
        var clients = mock(KubernetesClientFactory.class);
        var tumblebug = mock(CbtumblebugRestApi.class);
        var cluster = new K8sClusterDto();
        cluster.setConnectionName("nhn-looking-connection-name");
        var config = new K8sClusterDto.ConnectionConfig();
        config.setProviderName("aws");
        cluster.setConnectionConfig(config);
        when(tumblebug.getK8sClusterByName("default", "cluster-a")).thenReturn(cluster);

        var result = new NhnStorageClassService(clients, tumblebug).capability("default", "cluster-a");

        assertThat(result.supported()).isFalse();
        assertThat(result.driverReady()).isFalse();
        assertThat(result.canCreate()).isFalse();
        verifyNoInteractions(clients);
    }
}
