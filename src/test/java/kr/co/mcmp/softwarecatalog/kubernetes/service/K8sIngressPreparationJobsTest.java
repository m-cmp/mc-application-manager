package kr.co.mcmp.softwarecatalog.kubernetes.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.util.concurrent.*;
import kr.co.mcmp.softwarecatalog.*;
import kr.co.mcmp.softwarecatalog.application.dto.*;
import kr.co.mcmp.softwarecatalog.kubernetes.config.KubernetesClientFactory;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.jupiter.api.*;

class K8sIngressPreparationJobsTest {
    final KubernetesIngressPreflightService preflight=mock(KubernetesIngressPreflightService.class);
    final KubernetesClientFactory clients=mock(KubernetesClientFactory.class);
    final CatalogRepository catalogs=mock(CatalogRepository.class);
    final K8sIngressAccessService access=mock(K8sIngressAccessService.class);
    final IbmIngressAutomationService automation=mock(IbmIngressAutomationService.class);
    final K8sIngressPreparationJobs jobs=new K8sIngressPreparationJobs(preflight,clients,catalogs,access,automation);
    @AfterEach void cleanup() { jobs.shutdown(); }
    K8sIngressCheckRequest request(String ns, String cluster) {
        var r=new K8sIngressCheckRequest(); r.setNamespace(ns); r.setClusterName(cluster); r.setCatalogId(7L);
        r.setIngressEnabled(true); r.setIngressHost("app.company.com"); r.setIngressPath("/");
        r.setIngressClass(IbmIngressSupport.PUBLIC_CLASS); r.setServicePortCidr("203.0.113.4/32"); return r;
    }
    void valid() {
        when(preflight.check(any())).thenReturn(new K8sIngressCheckResult(true,List.of(),List.of()));
        when(catalogs.findById(7L)).thenReturn(Optional.of(new SoftwareCatalog()));
        when(clients.getClient(any(),any())).thenReturn(mock(KubernetesClient.class));
    }
    K8sIngressPreparationJobs.Status finish(K8sIngressPreparationJobs.Status initial) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(System.nanoTime()<deadline) {
            var status=jobs.get(initial.namespace(),initial.id());
            if(Set.of("READY","FAILED").contains(status.state())) return status;
            Thread.sleep(5);
        }
        throw new AssertionError("Job did not finish");
    }
    @Test void actualWorkerPreflightsThenPreparesExactlyTheSelectedTarget() throws Exception {
        valid(); var status=finish(jobs.start(request("project-a","cluster-a")));
        assertThat(status.state()).isEqualTo("READY");
        var order=inOrder(preflight,catalogs,access,clients,automation);
        order.verify(preflight).check(any()); order.verify(catalogs).findById(7L);
        order.verify(access).resolveTarget(any(),any()); order.verify(clients).getClient("project-a","cluster-a");
        order.verify(automation).prepare(any(),eq("project-a"),eq("cluster-a"),eq("default"),
                argThat(c -> "app.company.com".equals(c.getIngressHost())),any());
        assertThatThrownBy(() -> jobs.get("other-project",status.id())).hasMessageContaining("not found");
    }
    @Test void failedPreflightNeverPreparesAndProvidesActionableErrors() throws Exception {
        when(preflight.check(any())).thenReturn(new K8sIngressCheckResult(false,List.of("Host conflict"),List.of()));
        var status=finish(jobs.start(request("project-a","cluster-a")));
        assertThat(status.state()).isEqualTo("FAILED"); assertThat(status.message()).isEqualTo("Host conflict");
        verifyNoInteractions(automation,clients,catalogs);
    }
    @Test void secretBearingUpstreamExceptionsAreNeverReturned() throws Exception {
        valid(); doThrow(new IllegalStateException("DNS Secret=private-secret")).when(automation).prepare(any(),any(),any(),any(),any(),any());
        var status=finish(jobs.start(request("project-a","cluster-a")));
        assertThat(status.state()).isEqualTo("FAILED"); assertThat(status.message()).doesNotContain("private-secret","DNS Secret");
    }
    @Test void duplicateClicksReuseActiveJobButDifferentInputsDoNotQueueAnotherSharedChange() throws Exception {
        valid(); var entered=new CountDownLatch(1); var release=new CountDownLatch(1);
        doAnswer(c -> { entered.countDown(); assertThat(release.await(5,TimeUnit.SECONDS)).isTrue(); return null; })
                .when(automation).prepare(any(),any(),any(),any(),any(),any());
        try {
            var first=jobs.start(request("project-a","cluster-a")); assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
            assertThat(jobs.start(request("project-a","cluster-a")).id()).isEqualTo(first.id());
            var changed=request("project-a","cluster-a"); changed.setIngressHost("different.company.com");
            assertThatThrownBy(() -> jobs.start(changed)).hasMessageContaining("already running");
            release.countDown(); assertThat(finish(first).state()).isEqualTo("READY");
            verify(automation,times(1)).prepare(any(),any(),any(),any(),any(),any());
        } finally { release.countDown(); }
    }
    @Test void regularNginxCannotUseSharedIbmProvisioner() throws Exception {
        valid(); var r=request("project-a","cluster-a"); r.setIngressClass("nginx");
        assertThat(finish(jobs.start(r)).state()).isEqualTo("FAILED"); verifyNoInteractions(automation);
    }
    @Test void queueIsBoundedAndRejectsExcessBeforeAnyProvisioning() throws Exception {
        valid(); var release=new CountDownLatch(1);
        when(preflight.check(any())).thenAnswer(c -> { release.await(5,TimeUnit.SECONDS); return new K8sIngressCheckResult(false,List.of("stopped"),List.of()); });
        try {
            for(int i=0;i<6;i++) jobs.start(request("project-a","cluster-"+i));
            assertThatThrownBy(() -> jobs.start(request("project-a","cluster-7"))).hasMessageContaining("queue is full");
            verifyNoInteractions(automation);
        } finally { release.countDown(); }
    }
}
