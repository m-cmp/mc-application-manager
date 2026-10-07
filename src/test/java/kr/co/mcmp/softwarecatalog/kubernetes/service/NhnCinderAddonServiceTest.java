package kr.co.mcmp.softwarecatalog.kubernetes.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.storage.CSIDriverBuilder;
import io.fabric8.kubernetes.api.model.storage.CSINodeBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import kr.co.mcmp.ape.cbtumblebug.api.CbtumblebugRestApi;
import kr.co.mcmp.ape.cbtumblebug.dto.K8sClusterDto;
import kr.co.mcmp.softwarecatalog.kubernetes.config.KubernetesClientFactory;
import org.junit.jupiter.api.*;

class NhnCinderAddonServiceTest {
    static final String ID = "83e12bfc-877f-4d95-bb8c-dd76ce2a92cb";
    private final KubernetesServer server = new KubernetesServer(false, true);
    private final CbtumblebugRestApi tumblebug = mock(CbtumblebugRestApi.class);
    private final KubernetesClientFactory clients = mock(KubernetesClientFactory.class);
    private final NhnOpenBaoCredentials credentials = mock(NhnOpenBaoCredentials.class);
    private final NhnNksApi nks = mock(NhnNksApi.class);
    private final NhnCinderAddonProperties properties = configured();
    private final NhnCinderAddonService service = new NhnCinderAddonService(tumblebug, clients, properties, credentials, nks);
    private final ObjectMapper json = new ObjectMapper();
    private final NhnNksApi.Session session = new NhnNksApi.Session("test-token", "tenant", URI.create("https://kr1-api-kubernetes-infrastructure.nhncloudservice.com/v1/"), Instant.now().plusSeconds(3600));
    private KubernetesClient client;

    static NhnCinderAddonProperties configured() {
        var p = new NhnCinderAddonProperties(); p.setEnabled(true); p.setReadyTimeoutSeconds(1); p.setPollSeconds(1);
        var b = new NhnCinderAddonProperties.Binding(); b.setNamespace("project-a"); b.setConnectionName("nhn-kr1");
        b.setSecretPath("secret/data/csp/nhn"); b.setRegion("kr1"); p.setBindings(List.of(b)); return p;
    }
    static K8sClusterDto cluster(String provider) {
        var c = new K8sClusterDto(); c.setName("cluster-a"); c.setCspResourceId(ID); c.setConnectionName("nhn-kr1");
        var connection = new K8sClusterDto.ConnectionConfig(); connection.setProviderName(provider);
        var region = new K8sClusterDto.RegionZoneInfo(); region.setAssignedRegion("KR1"); connection.setRegionZoneInfo(region);
        c.setConnectionConfig(connection); return c;
    }
    static void registerDriver(KubernetesClient client) {
        client.storage().v1().csiDrivers().resource(new CSIDriverBuilder().withNewMetadata().withName(NhnStorageClassService.DRIVER)
                .endMetadata().withNewSpec().withAttachRequired(true).endSpec().build()).create();
        client.storage().v1().csiNodes().resource(new CSINodeBuilder().withNewMetadata().withName("worker-1").endMetadata()
                .withNewSpec().addNewDriver().withName(NhnStorageClassService.DRIVER).withNodeID("worker-1").endDriver().endSpec().build()).create();
    }
    @BeforeEach void start() throws Exception {
        server.before(); client = server.getClient();
        when(clients.getClient(anyString(), anyString())).thenAnswer(a -> new KubernetesClientBuilder().withConfig(client.getConfiguration()).build());
        when(tumblebug.getK8sClusterByName("project-a", "cluster-a")).thenReturn(cluster("nhn"));
        when(nks.login(any(), anyString())).thenReturn(session);
        when(nks.cluster(session, ID)).thenReturn(json.readTree("{\"status\":\"CREATE_COMPLETE\"}"));
        when(nks.compatibleVersion(eq(session), any())).thenReturn("v1.30.0-nks1");
        when(nks.installed(session, ID)).thenReturn(Optional.empty());
    }
    @AfterEach void stop() { server.after(); }

    @Test void rejectsOtherProvidersBeforeAnyCredentialReadOrCloudMutation() {
        when(tumblebug.getK8sClusterByName("project-a", "cluster-a")).thenReturn(cluster("aws"));
        assertThat(service.capability("project-a", "cluster-a").supported()).isFalse();
        assertThatThrownBy(() -> service.resolve("project-a", "cluster-a")).isInstanceOf(StorageOperationException.class).hasMessageContaining("only on NHN");
        verifyNoInteractions(credentials, clients); verify(nks, never()).install(any(), anyString(), anyString());
    }
    @Test void reusesManuallyInstalledReadyDriverEvenWhenAutomationIsDisabled() {
        registerDriver(client); properties.setEnabled(false);
        assertThat(service.capability("project-a", "cluster-a").driverReady()).isTrue();
        service.prepare(service.resolve("project-a", "cluster-a"), m -> {});
        verifyNoInteractions(credentials); verify(nks, never()).login(any(), anyString());
        verify(nks, never()).install(any(), anyString(), anyString());
    }
    @Test void disabledAutomationReportsConfigurationWithoutReadingSecrets() {
        properties.setEnabled(false);
        var result = service.capability("project-a", "cluster-a");
        assertThat(result.state()).isEqualTo("NOT_CONFIGURED"); assertThat(result.canInstall()).isFalse();
        assertThatThrownBy(() -> service.prepare(service.resolve("project-a", "cluster-a"), m -> {}))
                .isInstanceOf(StorageOperationException.class).hasMessageContaining("not configured");
        verifyNoInteractions(credentials); verify(nks, never()).login(any(), anyString());
        verify(nks, never()).install(any(), anyString(), anyString());
    }
    @Test void rejectsRegionMismatchBeforeReadingSecrets() {
        properties.getBindings().get(0).setRegion("kr2");
        assertThat(service.capability("project-a", "cluster-a").canInstall()).isFalse();
        verifyNoInteractions(credentials);
    }
    @Test void nativeInstalledButMissingWorkerRegistrationTimesOutWithoutReinstalling() throws Exception {
        when(nks.installed(session, ID)).thenReturn(Optional.of(json.readTree("{\"status\":\"CREATE_COMPLETE\",\"version\":\"v1.30.0\"}")));
        assertThatThrownBy(() -> service.prepare(service.resolve("project-a", "cluster-a"), m -> {}))
                .isInstanceOf(StorageOperationException.class).hasMessageContaining("timed out");
        verify(nks, never()).install(any(), anyString(), anyString());
    }
    @Test void failedExistingAddonDoesNotInstallOrOverwrite() throws Exception {
        when(nks.installed(session, ID)).thenReturn(Optional.of(json.readTree("{\"status\":\"CREATE_FAILED\"}")));
        assertThat(service.capability("project-a", "cluster-a").canInstall()).isFalse();
        assertThatThrownBy(() -> service.prepare(service.resolve("project-a", "cluster-a"), m -> {})).hasMessageContaining("failed");
        verify(nks, never()).install(any(), anyString(), anyString());
    }
    @Test void busyNativeClusterDoesNotSubmitInstallation() throws Exception {
        when(nks.cluster(session, ID)).thenReturn(json.readTree("{\"status\":\"UPDATE_IN_PROGRESS\"}"));
        assertThatThrownBy(() -> service.prepare(service.resolve("project-a", "cluster-a"), m -> {})).hasMessageContaining("not ready");
        verify(nks, never()).install(any(), anyString(), anyString());
    }
    @Test void clusterReplacementAfterSubmissionCannotMutateTheReplacement() {
        var target = service.resolve("project-a", "cluster-a");
        var changed = cluster("nhn"); changed.setCspResourceId("01234567-89ab-cdef-0123-456789abcdef");
        when(tumblebug.getK8sClusterByName("project-a", "cluster-a")).thenReturn(changed);
        assertThatThrownBy(() -> service.prepare(target, m -> {})).hasMessageContaining("cluster changed");
        verifyNoInteractions(credentials);
    }
    @Test void duplicateBindingsOrWrongProviderSecretNeverReadCredentials() {
        var binding = properties.getBindings().get(0); properties.setBindings(List.of(binding, binding));
        assertThat(service.capability("project-a", "cluster-a").canInstall()).isFalse();
        properties.setBindings(List.of(binding)); binding.setSecretPath("secret/data/csp/aws");
        assertThat(service.capability("project-a", "cluster-a").canInstall()).isFalse(); verifyNoInteractions(credentials);
    }
    @Test void malformedTargetRejectedBeforeTumblebugLookup() {
        assertThatThrownBy(() -> service.resolve("../other", "cluster-a")).isInstanceOf(StorageOperationException.class);
        verify(tumblebug, never()).getK8sClusterByName("../other", "cluster-a");
    }
    @Test void legacyRegionDetailsUseIdentifierRatherThanDisplayName() {
        var cluster = cluster("nhn"); cluster.getConnectionConfig().setRegionZoneInfo(null);
        var detail = new K8sClusterDto.RegionDetail(); detail.setRegionId("kr1"); detail.setRegionName("Korea (Pangyo)");
        cluster.getConnectionConfig().setRegionDetail(detail);
        when(tumblebug.getK8sClusterByName("project-a", "cluster-a")).thenReturn(cluster);
        assertThat(service.resolve("project-a", "cluster-a").region()).isEqualTo("kr1");
    }
}
