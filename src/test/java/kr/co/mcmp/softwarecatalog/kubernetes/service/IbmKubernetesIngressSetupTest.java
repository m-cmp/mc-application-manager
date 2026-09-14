package kr.co.mcmp.softwarecatalog.kubernetes.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.*;
import java.util.*;
import kr.co.mcmp.ape.cbtumblebug.api.CbtumblebugRestApi;
import kr.co.mcmp.ape.cbtumblebug.dto.K8sClusterDto;
import kr.co.mcmp.softwarecatalog.application.dto.DeploymentConfigDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IbmKubernetesIngressSetupTest {
    final CbtumblebugRestApi tb = mock(CbtumblebugRestApi.class);
    final IbmIngressAutomationProperties props = new IbmIngressAutomationProperties();
    final IbmKubernetesIngressSetup setup = spy(new IbmKubernetesIngressSetup(tb,props));
    final K8sClusterDto cluster = IbmIngressSupportTest.cluster("ibm");
    final DeploymentConfigDTO config = IbmIngressTlsResolverTest.config().toBuilder().ingressTlsEnabled(false).ingressTlsSecret(null).ingressHost("app.test").build();
    IbmKubernetesIngressSetupTest() throws Exception {
        cluster.setConnectionName("ibm-jp-osa"); cluster.setCspResourceId("cluster-1");
        cluster.setNetwork(new K8sClusterDto.Network("vpc-1", List.of("subnet-a","stale-subnet"),List.of(),List.of()));
        when(tb.getVNetSecurityMetadata("project-a","vpc-1")).thenReturn(new ObjectMapper().readTree("""
            {"connectionName":"ibm-jp-osa","cspResourceId":"vpc-csp","subnetInfoList":[
              {"connectionName":"ibm-jp-osa","cspVNetId":"vpc-csp","cspResourceId":"subnet-csp","ipv4_CIDR":"10.2.0.0/24"}]}
            """));
        doNothing().when(setup).awaitReady(any(),any(),any(),any());
    }
    IbmAutomationClusterFixture fixture() {
        var f = new IbmAutomationClusterFixture(false);
        var s=f.client.services().inNamespace("kube-system").withName("public-alb").get();
        s.getMetadata().getAnnotations().put("service.kubernetes.io/ibm-load-balancer-cloud-provider-vpc-lb-name","kube-cluster-1-ingress-public");
        s.getMetadata().getAnnotations().put(IbmKubernetesIngressSetup.OUTBOUND,"443");
        s.getMetadata().getAnnotations().put("operator-keep","yes");
        s.getSpec().setClusterIP("172.21.0.4"); s.getSpec().setSelector(Map.of("operator-selector","keep"));
        s.getSpec().getPorts().get(0).setNodePort(30660); s.getSpec().getPorts().get(0).setTargetPort(new IntOrString(443));
        f.client.services().inNamespace("kube-system").resource(s).update();
        var d=f.client.apps().deployments().inNamespace("kube-system").withName("public-alb").get();
        d.getSpec().getTemplate().getSpec().getContainers().get(0).getArgs().addAll(List.of("--configmap=kube-system/ibm-k8s-controller-config","--http-port=80","--https-port=443"));
        f.client.apps().deployments().inNamespace("kube-system").resource(d).update();
        f.client.nodes().resource(new NodeBuilder().withNewMetadata().withName("worker-1").withLabels(Map.of("ibm-cloud.kubernetes.io/subnet-id","subnet-csp")).endMetadata()
                .withNewStatus().addNewAddress().withType("InternalIP").withAddress("10.2.0.5").endAddress().endStatus().build()).create();
        return f;
    }
    @Test void specCheckReadsWithoutCreatingOrUpdatingAnything() {
        try(var f=fixture()) {
            String before=f.client.getKubernetesSerialization().asJson(f.client.services().inNamespace("kube-system").list());
            assertThat(setup.check(f.client,"project-a",cluster,config)).anyMatch(w -> w.contains("shared IBM"));
            assertThat(f.client.getKubernetesSerialization().asJson(f.client.services().inNamespace("kube-system").list())).isEqualTo(before);
            assertThat(f.client.configMaps().inNamespace("kube-system").withName(IbmKubernetesIngressSetup.JOURNAL).get()).isNull();
            verify(setup,never()).awaitReady(any(),any(),any(),any());
        }
    }
    @Test void httpOrchestrationUsesMcmpCredentialsWithoutIbmKeyOrCertificateCalls() {
        try(var f=fixture()) {
            var cloud=mock(IbmCloudIngressApi.class); var certificates=mock(IbmCertificateManager.class);
            var service=new IbmIngressAutomationService(tb,props,cloud,certificates,setup);
            when(tb.getK8sClusterByName("project-a","selected-cluster")).thenReturn(cluster);
            assertThat(service.check(f.client,"project-a","selected-cluster","default",config)).isNotEmpty();
            var resolved=service.prepare(f.client,"project-a","selected-cluster","default",config,m -> {});
            assertThat(resolved.isTlsEnabled()).isFalse(); assertThat(resolved.getIngressTlsSecret()).isNull();
            assertThat(f.client.configMaps().inNamespace("kube-system").withName(IbmIngressAutomationService.LOCK).get()).isNull();
            verifyNoInteractions(cloud,certificates);
        }
    }
    @Test void preparePreservesExistingHttpsAndUnknownSettingsAndIsIdempotent() {
        try(var f=fixture()) {
            setup.prepare(f.client,"project-a",cluster,config,m -> {});
            var s=f.client.services().inNamespace("kube-system").withName("public-alb").get();
            assertThat(s.getSpec().getPorts()).extracting(ServicePort::getPort).containsExactly(443,80);
            assertThat(s.getSpec().getPorts().get(0).getNodePort()).isEqualTo(30660);
            assertThat(s.getSpec().getClusterIP()).isEqualTo("172.21.0.4"); assertThat(s.getSpec().getSelector()).containsEntry("operator-selector","keep");
            assertThat(s.getMetadata().getAnnotations()).containsEntry("operator-keep","yes").containsEntry(IbmIngressSupport.PROXY_FEATURE,"alb,proxy-protocol");
            var cm=f.client.configMaps().inNamespace("kube-system").withName(IbmKubernetesIngressSetup.CONFIG).get();
            assertThat(cm.getData()).containsEntry("proxy-real-ip-cidr","10.2.0.0/24").containsEntry("use-forwarded-headers","false");
            assertThat(f.client.configMaps().inNamespace("kube-system").withName(IbmKubernetesIngressSetup.JOURNAL).get().getData()).containsEntry("state","READY");
            String version=s.getMetadata().getResourceVersion();
            setup.prepare(f.client,"project-a",cluster,config,m -> {});
            assertThat(f.client.services().inNamespace("kube-system").withName("public-alb").get().getMetadata().getResourceVersion()).isEqualTo(version);
            verify(setup,times(1)).awaitReady(any(),any(),any(),any());
            assertThat(f.client.services().inNamespace("kube-system").list().getItems()).hasSize(1);
            assertThat(f.client.network().v1().ingresses().inAnyNamespace().list().getItems()).isEmpty();
        }
    }
    @Test void failedConvergenceRestoresOwnFieldsAndPreservesConcurrentUnrelatedFields() {
        try(var f=fixture()) {
            doAnswer(call -> {
                var s=f.client.services().inNamespace("kube-system").withName("public-alb").get();
                s.getMetadata().getAnnotations().put("operator-new","keep"); f.client.services().inNamespace("kube-system").resource(s).update();
                throw new IllegalArgumentException("timeout");
            }).when(setup).awaitReady(any(),any(),any(),any());
            assertThatThrownBy(() -> setup.prepare(f.client,"project-a",cluster,config,m -> {})).hasMessageContaining("restored");
            var s=f.client.services().inNamespace("kube-system").withName("public-alb").get();
            assertThat(s.getSpec().getPorts()).extracting(ServicePort::getPort).containsExactly(443);
            assertThat(s.getMetadata().getAnnotations()).containsEntry("operator-new","keep").containsEntry(IbmIngressSupport.PROXY_FEATURE,"alb").containsEntry(IbmKubernetesIngressSetup.OUTBOUND,"443");
            assertThat(f.client.configMaps().inNamespace("kube-system").withName(IbmKubernetesIngressSetup.CONFIG).get().getData()).containsEntry("use-proxy-protocol","false");
            assertThat(f.client.configMaps().inNamespace("kube-system").withName(IbmKubernetesIngressSetup.JOURNAL).get().getData()).containsEntry("state","ROLLED_BACK");
        }
    }
    @Test void concurrentProxyChangesAreNotOverwrittenDuringRollback() {
        try(var f=fixture()) {
            doAnswer(call -> {
                var s=f.client.services().inNamespace("kube-system").withName("public-alb").get();
                s.getMetadata().getAnnotations().put(IbmIngressSupport.PROXY_FEATURE,"operator-value"); f.client.services().inNamespace("kube-system").resource(s).update();
                throw new IllegalArgumentException("timeout");
            }).when(setup).awaitReady(any(),any(),any(),any());
            assertThatThrownBy(() -> setup.prepare(f.client,"project-a",cluster,config,m -> {})).hasMessageContaining("review");
            assertThat(f.client.services().inNamespace("kube-system").withName("public-alb").get().getMetadata().getAnnotations()).containsEntry(IbmIngressSupport.PROXY_FEATURE,"operator-value");
            assertThat(f.client.configMaps().inNamespace("kube-system").withName(IbmKubernetesIngressSetup.JOURNAL).get().getData()).containsEntry("state","REVIEW_REQUIRED");
        }
    }
    @ParameterizedTest @ValueSource(strings={"missing-label","foreign-subnet","foreign-internal-ip","explicit-unknown-lb-subnet","wrong-connection","wrong-vpc"})
    void unverifiedNetworkStopsBeforeWrites(String mode) {
        try(var f=fixture()) {
            var node=f.client.nodes().withName("worker-1").get();
            if(mode.equals("missing-label")) node.getMetadata().setLabels(Map.of());
            if(mode.equals("foreign-subnet")) node.getMetadata().getLabels().put("ibm-cloud.kubernetes.io/subnet-id","foreign");
            if(mode.equals("foreign-internal-ip")) node.getStatus().getAddresses().get(0).setAddress("10.9.0.1");
            f.client.nodes().resource(node).update();
            if(mode.equals("explicit-unknown-lb-subnet")) {
                var s=f.client.services().inNamespace("kube-system").withName("public-alb").get();
                s.getMetadata().getAnnotations().put(IbmKubernetesIngressSetup.SUBNETS,"unknown"); f.client.services().inNamespace("kube-system").resource(s).update();
            }
            if(mode.equals("wrong-connection")) cluster.setConnectionName("other");
            if(mode.equals("wrong-vpc")) cluster.getNetwork().setVNetId("other");
            assertThatThrownBy(() -> setup.prepare(f.client,"project-a",cluster,config,m -> {})).isInstanceOf(IllegalArgumentException.class);
            assertThat(f.client.configMaps().inNamespace("kube-system").withName(IbmKubernetesIngressSetup.JOURNAL).get()).isNull();
        }
    }
    @ParameterizedTest @ValueSource(strings={"PREPARING","REVIEW_REQUIRED","unknown"})
    void restartDoesNotRepeatUnresolvedChanges(String state) {
        try(var f=fixture()) {
            f.client.configMaps().inNamespace("kube-system").resource(new ConfigMapBuilder().withNewMetadata().withName(IbmKubernetesIngressSetup.JOURNAL)
                    .withLabels(Map.of(IbmCertificateManager.OWNER,"true")).endMetadata().withData(Map.of("state",state)).build()).create();
            assertThatThrownBy(() -> setup.prepare(f.client,"project-a",cluster,config,m -> {})).hasMessageContaining("unresolved");
            verify(setup,never()).awaitReady(any(),any(),any(),any());
        }
    }
    @Test void controllerProxyWithoutLbProxyIsNotSilentlyOverwritten() {
        try(var f=fixture()) {
            var cm=f.client.configMaps().inNamespace("kube-system").withName(IbmKubernetesIngressSetup.CONFIG).get();
            cm.getData().put("use-proxy-protocol","true"); f.client.configMaps().inNamespace("kube-system").resource(cm).update();
            assertThatThrownBy(() -> setup.check(f.client,"project-a",cluster,config)).hasMessageContaining("mixed");
        }
    }
    @Test void nonIbmTargetIsRejectedWithoutClusterApiWrites() {
        try(var f=fixture()) {
            cluster.getConnectionConfig().setProviderName("aws");
            assertThatThrownBy(() -> setup.prepare(f.client,"project-a",cluster,config,m -> {})).hasMessageContaining("IBM cluster");
            verifyNoInteractions(tb);
        }
    }
    @Test void newlyAddedHttpListenerIsReconciledWithoutChangingPortsOrFeatures() {
        try(var f=fixture()) {
            var ops=f.client.services().inNamespace("kube-system"); var before=ops.withName("public-alb").get();
            setup.prepare(f.client,"project-a",cluster,config,m -> {});
            var current=ops.withName("public-alb").get();
            var requests=IbmKubernetesIngressSetup.reconcileAddedListeners(f.client,List.of(before));
            var after=ops.withName("public-alb").get();
            assertThat(requests).containsKey("public-alb");
            assertThat(after.getSpec()).isEqualTo(current.getSpec());
            assertThat(after.getMetadata().getAnnotations()).containsEntry(IbmIngressSupport.PROXY_FEATURE,"alb,proxy-protocol").containsKey(IbmKubernetesIngressSetup.RECONCILE);
            assertThat(after.getMetadata().getUid()).isEqualTo(before.getMetadata().getUid());
        }
    }
    @Test void existingHttpPortNeedsNoExtraReconciliation() {
        try(var f=fixture()) {
            setup.prepare(f.client,"project-a",cluster,config,m -> {});
            var current=f.client.services().inNamespace("kube-system").withName("public-alb").get();
            assertThat(IbmKubernetesIngressSetup.reconcileAddedListeners(f.client,List.of(current))).isEmpty();
            assertThat(f.client.services().inNamespace("kube-system").withName("public-alb").get().getMetadata().getResourceVersion()).isEqualTo(current.getMetadata().getResourceVersion());
        }
    }
    @Test void concurrentProxyDisableStopsAddedListenerReconciliation() {
        try(var f=fixture()) {
            var before=f.client.services().inNamespace("kube-system").withName("public-alb").get();
            assertThatThrownBy(() -> IbmKubernetesIngressSetup.reconcileAddedListeners(f.client,List.of(before))).hasMessageContaining("changed");
        }
    }
}
