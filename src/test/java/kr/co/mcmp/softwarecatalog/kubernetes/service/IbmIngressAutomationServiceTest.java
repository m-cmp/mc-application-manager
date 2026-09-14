package kr.co.mcmp.softwarecatalog.kubernetes.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import kr.co.mcmp.ape.cbtumblebug.api.CbtumblebugRestApi;
import kr.co.mcmp.ape.cbtumblebug.dto.K8sClusterDto;
import java.net.URI;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IbmIngressAutomationServiceTest {
    final CbtumblebugRestApi tb=mock(CbtumblebugRestApi.class);
    final IbmCloudIngressApi cloud=mock(IbmCloudIngressApi.class);
    final IbmIngressAutomationProperties properties=new IbmIngressAutomationProperties();
    final IbmCertificateManager certificates=spy(new IbmCertificateManager(properties,null));
    final IbmIngressAutomationService service=new IbmIngressAutomationService(tb,properties,cloud,certificates,new IbmKubernetesIngressSetup(tb,properties));
    final IbmCloudIngressApi.Session session=new IbmCloudIngressApi.Session("test",URI.create("https://containers.cloud.ibm.com/global/"),URI.create("https://jp-osa.iaas.cloud.ibm.com"));
    final K8sClusterDto target=IbmIngressSupportTest.cluster("ibm");

    IbmIngressAutomationServiceTest() throws Exception {
        target.setConnectionName("ibm-jp-osa"); target.setCspResourceId("ibm-cluster-selected");
        when(tb.getK8sClusterByName("project-a","chosen-cluster")).thenReturn(target);
        when(cloud.login(any())).thenReturn(session);
        when(cloud.cluster(session,"ibm-cluster-selected")).thenReturn(new ObjectMapper().readTree("{\"region\":\"jp-osa\",\"vpcs\":[\"vpc-1\"]}"));
        when(cloud.proxyConfig(session,"ibm-cluster-selected","public")).thenReturn(new ObjectMapper().readTree("{\"proxyProtocol\":{\"enable\":false}}"));
        when(cloud.trustedSubnets(eq(session),any(),eq(Set.of("a.lb.appdomain.cloud")),eq(true))).thenReturn(List.of("10.2.0.0/24"));
    }
    IbmIngressAutomationProperties.Profile authorize() {
        var p=new IbmIngressAutomationProperties.Profile(); p.setNamespace("project-a"); p.setConnectionName("ibm-jp-osa");
        p.setRegion("jp-osa"); p.setAllowSharedLbChanges(true); properties.setProfiles(List.of(p)); return p;
    }
    @Test void readyClusterUsesExistingCertificateWithoutAnyAutomationWrites() {
        try(var fixture=new IbmAutomationClusterFixture(true)) {
            var result=service.prepare(fixture.client,"project-a","chosen-cluster","default",IbmIngressTlsResolverTest.config(),m -> {});
            assertThat(result.isTlsEnabled()).isTrue();
            assertThat(result.getIngressTlsSecret()).isEqualTo(IbmIngressTlsResolverTest.SECRET);
            assertThat(fixture.client.configMaps().inNamespace("kube-system").withName(IbmIngressAutomationService.LOCK).get()).isNull();
            verifyNoInteractions(cloud,certificates);
        }
    }
    @Test void preflightOnlyWarnsButDeployAppliesOnceToTheSelectedCluster() throws Exception {
        authorize();
        try(var fixture=new IbmAutomationClusterFixture(false)) {
            var config=IbmIngressTlsResolverTest.config();
            assertThat(service.check(fixture.client,"project-a","chosen-cluster","default",config)).anyMatch(w -> w.contains("shared IBM LBs"));
            verify(cloud,never()).enableProxy(any(),any(),any());
            assertThat(fixture.client.configMaps().inNamespace("kube-system").withName(IbmIngressAutomationService.INTENT).get()).isNull();
            doAnswer(call -> { fixture.proxyReady(); return null; }).when(cloud).enableProxy(session,"ibm-cluster-selected",List.of("10.2.0.0/24"));
            service.prepare(fixture.client,"project-a","chosen-cluster","default",config,m -> {});
            service.prepare(fixture.client,"project-a","chosen-cluster","default",config,m -> {});
            verify(cloud,times(1)).enableProxy(session,"ibm-cluster-selected",List.of("10.2.0.0/24"));
            assertThat(fixture.client.configMaps().inNamespace("kube-system").withName(IbmIngressAutomationService.INTENT).get().getData().get("clusterId")).isEqualTo("ibm-cluster-selected");
            assertThat(fixture.client.configMaps().inNamespace("kube-system").withName(IbmIngressAutomationService.LOCK).get()).isNull();
            assertThat(fixture.client.network().v1().ingresses().inAnyNamespace().list().getItems()).isEmpty();
        }
    }
    @ParameterizedTest @ValueSource(strings={"missing-profile","wrong-project","wrong-connection","not-authorized"})
    void unauthorizedProfilesCannotWriteSharedResources(String scenario) {
        if (!scenario.equals("missing-profile")) {
            var p=authorize();
            if (scenario.equals("wrong-project")) p.setNamespace("other");
            if (scenario.equals("wrong-connection")) p.setConnectionName("other");
            if (scenario.equals("not-authorized")) p.setAllowSharedLbChanges(false);
        }
        try(var fixture=new IbmAutomationClusterFixture(false)) {
            assertThatThrownBy(() -> service.prepare(fixture.client,"project-a","chosen-cluster","default",IbmIngressTlsResolverTest.config(),m -> {})).hasMessageContaining("authorize");
            verifyNoInteractions(cloud);
            assertThat(fixture.client.configMaps().inNamespace("kube-system").withName(IbmIngressAutomationService.LOCK).get()).isNull();
        }
    }
    @Test void uncertainPatchIsNotRepeatedOnRetry() {
        authorize();
        try(var fixture=new IbmAutomationClusterFixture(false)) {
            doThrow(new IllegalArgumentException("IBM request timed out")).when(cloud).enableProxy(any(),any(),any());
            assertThatThrownBy(() -> service.prepare(fixture.client,"project-a","chosen-cluster","default",IbmIngressTlsResolverTest.config(),m -> {})).hasMessageContaining("timed out");
            assertThatThrownBy(() -> service.prepare(fixture.client,"project-a","chosen-cluster","default",IbmIngressTlsResolverTest.config(),m -> {})).hasMessageContaining("unresolved");
            verify(cloud,times(1)).enableProxy(any(),any(),any());
        }
    }
    @Test void activePreparationLockCannotBeStolen() {
        authorize();
        try(var fixture=new IbmAutomationClusterFixture(false)) {
            fixture.client.configMaps().inNamespace("kube-system").resource(new ConfigMapBuilder().withNewMetadata().withName(IbmIngressAutomationService.LOCK)
                    .withLabels(Map.of(IbmCertificateManager.OWNER,"true")).endMetadata().withData(Map.of("holder","someone-else","clusterId","ibm-cluster-selected",
                            "expiresAt",java.time.Instant.now().plusSeconds(900).toString())).build()).create();
            assertThatThrownBy(() -> service.prepare(fixture.client,"project-a","chosen-cluster","default",IbmIngressTlsResolverTest.config(),m -> {})).hasMessageContaining("in progress");
            verify(cloud,never()).enableProxy(any(),any(),any());
            assertThat(fixture.client.configMaps().inNamespace("kube-system").withName(IbmIngressAutomationService.LOCK).get().getData().get("holder")).isEqualTo("someone-else");
        }
    }
    @Test void invalidDomainStopsBeforeAnyProxyChange() {
        authorize();
        try(var fixture=new IbmAutomationClusterFixture(false)) {
            var config=IbmIngressTlsResolverTest.config(); config.setIngressHost("unowned.test.com");
            assertThatThrownBy(() -> service.prepare(fixture.client,"project-a","chosen-cluster","default",config,m -> {})).hasMessageContaining("custom domain");
            verifyNoInteractions(cloud);
        }
    }
    @Test void otherCspCannotBeClaimedAsIbm() {
        target.getConnectionConfig().setProviderName("aws");
        try(var fixture=new IbmAutomationClusterFixture(true)) {
            assertThatThrownBy(() -> service.check(fixture.client,"project-a","chosen-cluster","default",IbmIngressTlsResolverTest.config())).hasMessageContaining("IBM cluster");
            verifyNoInteractions(cloud,certificates);
        }
    }
}
