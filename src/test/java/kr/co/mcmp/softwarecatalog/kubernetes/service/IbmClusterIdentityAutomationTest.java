package kr.co.mcmp.softwarecatalog.kubernetes.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import kr.co.mcmp.ape.cbtumblebug.dto.K8sClusterDto;

class IbmClusterIdentityAutomationTest {
    @Test void newEnvironmentPreflightUsesRegisteredRegionAndCreatesNoIdentityPod() throws Exception {
        var test=new IbmIngressAutomationServiceTest();test.properties.setCredentialSource("cluster");test.properties.setReadyTimeoutSeconds(2);test.properties.setPollSeconds(1);
        var region=new K8sClusterDto.RegionZoneInfo();region.setAssignedRegion("jp-osa");
        test.target.getConnectionConfig().setRegionZoneInfo(region);
        try(var fixture=new IbmAutomationClusterFixture(false)) {
            new IbmTraefikIngressTest().cluster(fixture.client,false);
            var setup=mock(IbmKubernetesIngressSetup.class);
            var service=new IbmIngressAutomationService(test.tb,test.properties,test.cloud,test.certificates,setup);
            when(setup.check(any(),any(),any(),any())).thenReturn(List.of());
            assertThat(service.check(fixture.client,"project-a","chosen-cluster","default",new IbmTraefikIngressTest().config()))
                    .anyMatch(m->m.contains("managed Secret Provider"));
            verify(test.cloud).checkClusterIdentity(fixture.client);
            verify(test.cloud,never()).login(any());
            verify(test.cloud,never()).login(any(),any(),any());
            assertThat(fixture.client.pods().inNamespace("kube-system").list().getItems()).isEmpty();
            assertThat(fixture.client.serviceAccounts().inNamespace("kube-system").list().getItems()).isEmpty();
            assertThat(fixture.client.configMaps().inNamespace("kube-system").withName(IbmIngressAutomationService.INTENT).get()).isNull();
        }
    }
    @Test void newEnvironmentDeployUsesClusterIdentityAndKeepsTheExistingAlb() throws Exception {
        var test=new IbmIngressAutomationServiceTest();test.properties.setCredentialSource("cluster");test.properties.setReadyTimeoutSeconds(2);test.properties.setPollSeconds(1);
        var region=new K8sClusterDto.RegionZoneInfo();region.setAssignedRegion("jp-osa");
        test.target.getConnectionConfig().setRegionZoneInfo(region);
        when(test.cloud.login(any(),any(),eq("ibm-cluster-selected"))).thenReturn(test.session);
        try(var fixture=new IbmAutomationClusterFixture(false)) {
            new IbmTraefikIngressTest().cluster(fixture.client,false);
            var lb=fixture.client.services().inNamespace("kube-system").withName("public-traefik").get();
            lb.getSpec().getPorts().add(new io.fabric8.kubernetes.api.model.ServicePortBuilder().withPort(443).build());
            fixture.client.services().inNamespace("kube-system").resource(lb).update();
            var setup=mock(IbmKubernetesIngressSetup.class);
            when(setup.check(any(),any(),any(),any())).thenReturn(List.of());
            when(setup.plan(any(),any(),any(),any())).thenReturn(new IbmKubernetesIngressSetup.Plan(List.of(),null,List.of(),true));
            when(test.cloud.trustedSubnets(eq(test.session),any(),anySet(),eq(true))).thenReturn(List.of("10.2.0.0/24"));
            var before=fixture.client.services().inNamespace("kube-system").list().getItems().stream().map(s->s.getMetadata().getUid()).toList();
            doAnswer(call->{
                var d=fixture.client.apps().deployments().inNamespace("kube-system").withName("traefik").get();
                d.getSpec().getTemplate().getSpec().getContainers().get(0).getArgs().addAll(List.of(
                        "--entryPoints.web.proxyProtocol.trustedIPs=10.2.0.0/24",
                        "--entryPoints.websecure.proxyProtocol.trustedIPs=10.2.0.0/24"));
                fixture.client.apps().deployments().inNamespace("kube-system").resource(d).update();return null;
            }).when(test.cloud).enableProxy(test.session,"ibm-cluster-selected",List.of("10.2.0.0/24"));
            new IbmIngressAutomationService(test.tb,test.properties,test.cloud,test.certificates,setup)
                    .prepare(fixture.client,"project-a","chosen-cluster","default",new IbmTraefikIngressTest().config(),m->{});
            var profile=org.mockito.ArgumentCaptor.forClass(IbmIngressAutomationProperties.Profile.class);
            verify(test.cloud).login(eq(fixture.client),profile.capture(),eq("ibm-cluster-selected"));
            assertThat(profile.getValue().getRegion()).isEqualTo("jp-osa");
            verify(test.cloud,never()).login(any());
            verify(test.cloud).enableProxy(test.session,"ibm-cluster-selected",List.of("10.2.0.0/24"));
            assertThat(fixture.client.services().inNamespace("kube-system").list().getItems().stream().map(s->s.getMetadata().getUid()).toList()).isEqualTo(before);
        }
    }
    @Test void automaticAuthenticationStillRespectsTheSharedChangeOptOut() throws Exception {
        var test=new IbmIngressAutomationServiceTest();test.properties.setCredentialSource("cluster");test.properties.setAllowSharedLbChanges(false);
        try(var fixture=new IbmAutomationClusterFixture(false)) {
            new IbmTraefikIngressTest().cluster(fixture.client,false);
            assertThatThrownBy(()->test.service.check(fixture.client,"project-a","chosen-cluster","default",new IbmTraefikIngressTest().config())).hasMessageContaining("authorize");
            verifyNoInteractions(test.cloud);
        }
    }
}
