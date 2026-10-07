package kr.co.mcmp.softwarecatalog.kubernetes.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import kr.co.mcmp.softwarecatalog.SoftwareCatalog;
import kr.co.mcmp.softwarecatalog.application.constants.*;
import kr.co.mcmp.softwarecatalog.application.dto.DeploymentRequest;
import kr.co.mcmp.softwarecatalog.application.model.*;
import kr.co.mcmp.softwarecatalog.application.repository.DeploymentHistoryRepository;
import kr.co.mcmp.softwarecatalog.application.service.ObjectStorageAccessGrantService;
import kr.co.mcmp.softwarecatalog.application.service.tunnel.K8sObjectStorageTunnelService;
import kr.co.mcmp.softwarecatalog.kubernetes.config.*;

class K8sJupyterNamespaceTest {
    private final KubernetesServer server = new KubernetesServer(false, true);
    private final KubernetesClientFactory clients = mock(KubernetesClientFactory.class);
    private final DeploymentHistoryRepository histories = mock(DeploymentHistoryRepository.class);
    private final ObjectStorageAccessGrantService grants = mock(ObjectStorageAccessGrantService.class);
    private final K8sObjectStorageTunnelService tunnels = mock(K8sObjectStorageTunnelService.class);
    private final K8sIngressAccessService access = mock(K8sIngressAccessService.class);
    private final HelmChartService helm = mock(HelmChartService.class);
    private final SoftwareCatalog catalog = SoftwareCatalog.builder().id(11L).packageInfo(
            PackageInfo.builder().packageName("quay.io/jupyter/scipy-notebook").packageVersion("test").build()).build();
    private final K8sJupyterService service = new K8sJupyterService(clients, helm, grants, access, histories, null,
            new ObjectMapper(), tunnels, IbmIngressAutomationTestSupport.legacy());
    private final String name = "mcmp-jupyter-41";
    @BeforeEach void setup() {
        server.before();
        org.springframework.test.util.ReflectionTestUtils.setField(service, "readyTimeout", 5L);
        when(clients.getClient("my-project", "cluster-a")).thenAnswer(call ->
                new KubernetesClientBuilder().withConfig(server.getClient().getConfiguration()).build());
        when(histories.saveAndFlush(any())).thenAnswer(call -> {
            DeploymentHistory history = call.getArgument(0); history.setId(41L); return history;
        });
        server.getClient().namespaces().resource(new NamespaceBuilder().withNewMetadata().withName("default").endMetadata().build()).create();
    }
    @AfterEach void cleanup() { server.after(); }

    @Test void nonDefaultProjectInstallsAllResourcesInDefaultAndKeepsProjectForAuthorization() {
        var client = server.getClient();
        client.storage().v1().storageClasses().resource(new io.fabric8.kubernetes.api.model.storage.StorageClassBuilder()
                .withNewMetadata().withName("standard").endMetadata().withProvisioner(NhnStorageClassService.DRIVER).build()).create();
        var request = DeploymentRequest.builder().namespace("my-project").clusterName("cluster-a")
                .ingressEnabled(true).ingressHost("jupyter.example.test").ingressPath("/").ingressClass("nginx")
                .servicePortCidr("203.0.113.8/32").minReplicas(1).additionalConfig(Map.of("storageClass", "standard",
                        "objectStorage", Map.of("enabled", true, "jupyterToken", "test-login-token"))).build();
        when(grants.issue(eq(41L), eq("k8s:cluster-a"), eq("my-project"), any()))
                .thenReturn(new ObjectStorageAccessGrantService.IssuedAccess("test-grant", List.of(), null));
        when(tunnels.credentials("default", name)).thenReturn(new SecretBuilder().withNewMetadata()
                .withNamespace("default").withName(name + "-ssh").addToLabels(K8sJupyterService.OWNER, name).endMetadata()
                .withStringData(Map.of("client-key", "test-key")).build());
        server.expect().get().withPath("/apis/apps/v1/namespaces/default/deployments/" + name).andReturn(200,
                new DeploymentBuilder().withNewMetadata().withName(name).withNamespace("default").endMetadata()
                .withNewSpec().withReplicas(1).endSpec().withNewStatus().withReplicas(1).withReadyReplicas(1)
                .withAvailableReplicas(1).withUpdatedReplicas(1).endStatus().build()).always();
        var history = service.deploy(request, catalog);
        assertThat(history.getStatus()).isEqualTo("SUCCESS");
        assertThat(history.getNamespace()).isEqualTo("my-project");
        assertThat(client.namespaces().withName("my-project").get()).isNull();
        assertThat(client.persistentVolumeClaims().inNamespace("default").withName(name).get()).isNotNull();
        assertThat(client.services().inNamespace("default").withName(name).get()).isNotNull();
        assertThat(client.network().v1().ingresses().inNamespace("default").withName(name).get()).isNotNull();
        assertThat(client.secrets().inNamespace("default").list().getItems()).hasSize(2);
        assertThat(client.configMaps().inNamespace("default").list().getItems()).hasSize(2);
        verify(tunnels).register(eq(41L), eq("my-project"), eq("cluster-a"),
                argThat(d -> "default".equals(d.getMetadata().getNamespace())),
                argThat(s -> "default".equals(s.getMetadata().getNamespace())));
        verify(grants).resolveSelections(eq("my-project"), any());
        verify(helm).ensureIngressController(any(), eq("my-project"), eq("cluster-a"));
        verify(clients, never()).getClient(eq("default"), anyString());
    }

    @ParameterizedTest @ValueSource(strings = {"default", "my-project"})
    void stopAndUninstallFindBothCurrentAndLegacyWorkloadsAndRetainNotebook(String workloadNamespace) {
        var history = DeploymentHistory.builder().id(41L).namespace("my-project").clusterName("cluster-a")
                .releaseName(name).catalog(catalog).status("SUCCESS").build();
        when(histories.findTopByCatalogIdAndClusterNameAndNamespaceAndActionTypeAndReleaseNameStartingWithOrderByExecutedAtDesc(
                11L, "cluster-a", "my-project", ActionType.INSTALL, "mcmp-jupyter-")).thenReturn(history);
        var client = server.getClient();
        var deployment = new DeploymentBuilder().withNewMetadata().withName(name).withNamespace(workloadNamespace)
                .addToLabels(K8sJupyterService.OWNER, name).endMetadata().withNewSpec().withReplicas(1).endSpec().build();
        client.apps().deployments().resource(deployment).create();
        client.persistentVolumeClaims().resource(new PersistentVolumeClaimBuilder().withNewMetadata().withName(name)
                .withNamespace(workloadNamespace).addToLabels(K8sJupyterService.OWNER, name).endMetadata().build()).create();
        client.services().resource(new ServiceBuilder().withNewMetadata().withName(name).withNamespace(workloadNamespace)
                .addToLabels(K8sJupyterService.OWNER, name).endMetadata().build()).create();
        assertThat(service.scale("my-project", "cluster-a", 11L, 0, false)).containsEntry(name, 1);
        assertThat(client.apps().deployments().inNamespace(workloadNamespace).withName(name).get().getSpec().getReplicas()).isZero();
        service.uninstall("my-project", "cluster-a", 11L);
        assertThat(client.apps().deployments().inNamespace(workloadNamespace).withName(name).get()).isNull();
        assertThat(client.services().inNamespace(workloadNamespace).withName(name).get()).isNull();
        assertThat(client.persistentVolumeClaims().inNamespace(workloadNamespace).withName(name).get()).isNotNull();
        assertThat(history.getStatus()).isEqualTo("UNINSTALLED");
        assertThat(KubernetesNamespaces.jupyterWorkloadNamespace(client, "my-project", name)).isEqualTo(workloadNamespace);
        verify(clients, never()).getClient(eq("default"), anyString());
    }
}
