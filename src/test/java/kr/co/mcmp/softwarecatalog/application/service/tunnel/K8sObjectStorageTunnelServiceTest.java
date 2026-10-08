package kr.co.mcmp.softwarecatalog.application.service.tunnel;

import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import kr.co.mcmp.softwarecatalog.application.model.K8sObjectStorageTunnel;
import kr.co.mcmp.softwarecatalog.application.repository.K8sObjectStorageTunnelRepository;

class K8sObjectStorageTunnelServiceTest {
    final K8sObjectStorageTunnelRepository repository=mock(K8sObjectStorageTunnelRepository.class);
    final K8sObjectStorageTunnelRuntime runtime=mock(K8sObjectStorageTunnelRuntime.class);
    final K8sObjectStorageTunnelService service=new K8sObjectStorageTunnelService(repository,runtime);
    final K8sObjectStorageTunnel tunnel=new K8sObjectStorageTunnel();
    final K8sObjectStorageTunnelRuntime.Running first=mock(K8sObjectStorageTunnelRuntime.Running.class);
    @BeforeEach void setup() {
        tunnel.setDeploymentId(41L);
        when(repository.findById(41L)).thenReturn(Optional.of(tunnel));
        when(repository.claim(eq(41L),anyString(),any(),any())).thenReturn(1);
        when(runtime.start(tunnel)).thenReturn(first);
        when(first.alive()).thenReturn(true); when(first.currentPod()).thenReturn(true);
    }
    @Test void registrationPersistsProjectForClusterLookupEvenWhenWorkloadIsInDefault() {
        var deployment = new io.fabric8.kubernetes.api.model.apps.DeploymentBuilder().withNewMetadata()
                .withName("mcmp-jupyter-41").withNamespace("default").withUid("workload-uid").endMetadata().build();
        var secret = new io.fabric8.kubernetes.api.model.SecretBuilder().withNewMetadata()
                .withNamespace("default").withUid("secret-uid").endMetadata().build();
        service.register(41L, "my-project", "cluster-a", deployment, secret);
        verify(repository).saveAndFlush(argThat(t -> "my-project".equals(t.getNamespace())
                && "cluster-a".equals(t.getClusterName()) && "workload-uid".equals(t.getWorkloadUid())));
    }
    @Test void unchangedPodKeepsConnection() {
        service.ensure(41L); service.ensure(41L);
        verify(runtime,times(1)).start(tunnel); verify(first,never()).close();
    }
    @Test void podReplacementReopensPortForwardWithNewCredentialsLookup() {
        service.ensure(41L);
        when(first.currentPod()).thenReturn(false);
        service.ensure(41L);
        verify(first).close(); verify(runtime,times(2)).start(tunnel);
    }
    @Test void lostApiStreamReconnects() {
        service.ensure(41L); when(first.alive()).thenReturn(false);
        service.ensure(41L); verify(first).close(); verify(runtime,times(2)).start(tunnel);
    }
    @Test void leaseLossClosesLocalSessionInsteadOfCompeting() {
        service.ensure(41L);
        when(repository.claim(eq(41L),anyString(),any(),any())).thenReturn(0);
        service.ensure(41L); verify(first).close(); verify(runtime,times(1)).start(tunnel);
    }
    @Test void removalPersistsIntentAndClosesTheSession() {
        service.ensure(41L); service.remove(41L);
        verify(repository).desire(eq(41L),eq("DELETED"),any()); verify(first).close();
        service.ensure(41L); verify(runtime,times(1)).start(tunnel);
    }
    @Test void remoteDeletionObservedByReconcilerClosesSession() {
        service.ensure(41L); tunnel.setDesiredState("DELETED");
        when(repository.findAll()).thenReturn(List.of(tunnel));
        service.reconcile(); verify(first).close();
    }
    @Test void suspendAndResumeReestablishesSession() {
        service.ensure(41L); service.suspend(41L); service.ensure(41L);
        verify(first).close(); verify(runtime,times(1)).start(tunnel);
        service.resume(41L); service.ensure(41L); verify(runtime,times(2)).start(tunnel);
    }
    @Test void shutdownReleasesLeaseAndPreservesDesiredState() {
        service.ensure(41L); service.close();
        verify(first).close(); verify(repository).release(anyString());
        verify(repository,never()).desire(any(),any(),any());
    }
}
