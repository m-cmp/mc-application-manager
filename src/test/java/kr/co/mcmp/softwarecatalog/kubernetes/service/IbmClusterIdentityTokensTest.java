package kr.co.mcmp.softwarecatalog.kubernetes.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import io.fabric8.kubernetes.api.model.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IbmClusterIdentityTokensTest {
    static final String ID="db8nbb1t0dmfse1vul1g", IMAGE="icr.io/obs/armada-storage-secret@sha256:"+"a".repeat(64);
    static final String TOKEN="test-token-"+"a".repeat(50);
    static class Time extends Clock {
        Instant now=Instant.parse("2026-10-16T00:00:00Z");
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){return this;}
        public Instant instant(){return now;}
    }
    IbmClusterIdentityTokens reader(Time clock) throws Exception {
        var service=spy(new IbmClusterIdentityTokens(clock));
        doReturn(IMAGE).when(service).providerImage(any());
        doNothing().when(service).awaitReady(any(),anyString(),any());
        doReturn("IAM 3600 "+TOKEN+"\n").when(service).execute(any(),anyString());
        return service;
    }
    @Test void identityIsCachedOnlyInMemoryAndReissuedAfterExpiry() throws Exception {
        var clock=new Time();var service=reader(clock);
        try(var fixture=new IbmAutomationClusterFixture(true)) {
            assertThat(service.token(fixture.client,ID)).isEqualTo(TOKEN);
            assertThat(service.token(fixture.client,ID)).isEqualTo(TOKEN);
            verify(service,times(1)).execute(any(),anyString());
            clock.now=clock.now.plusSeconds(901);
            assertThat(service.token(fixture.client,ID)).isEqualTo(TOKEN);
            verify(service,times(2)).execute(any(),anyString());
            assertThat(fixture.client.serviceAccounts().inNamespace("kube-system").withLabel(IbmClusterIdentityTokens.OWNER,"true").list().getItems()).isEmpty();
            // The mock API has no garbage collector: children retain a real SA ownerReference.
            for(var pod:fixture.client.pods().inNamespace("kube-system").list().getItems()) {
                assertThat(pod.getMetadata().getOwnerReferences()).singleElement().satisfies(o->assertThat(o.getKind()).isEqualTo("ServiceAccount"));
                assertThat(pod.getSpec().getContainers().get(1).getCommand()).containsExactly("sh","-c","sleep 450");
                assertThat(pod.getSpec().getContainers().get(1).getCommand().toString()).doesNotContain("IdentityBridge");
            }
            assertThat(fixture.client.services().inNamespace("kube-system").list().getItems()).hasSize(1);
            for(var role:fixture.client.rbac().roles().inNamespace("kube-system").list().getItems())
                assertThat(role.getRules().get(0).getResourceNames()).containsExactly("storage-secret-store","ibm-cloud-credentials");
        }
    }
    @Test void anotherSelectedClusterDoesNotReuseThePreviousToken() throws Exception {
        var service=reader(new Time());
        try(var fixture=new IbmAutomationClusterFixture(true)) {
            service.token(fixture.client,ID);service.token(fixture.client,"ab8nbb1t0dmfse1vul1g");
            verify(service,times(2)).execute(any(),anyString());
        }
    }
    @Test void execFailureDeletesTheHelperAndDoesNotExposeServerError() throws Exception {
        var service=reader(new Time());doThrow(new IllegalStateException("private-token-body")).when(service).execute(any(),anyString());
        try(var fixture=new IbmAutomationClusterFixture(true)) {
            assertThatThrownBy(()->service.token(fixture.client,ID)).hasMessageContaining("no ALB change").hasMessageNotContaining("private-token-body").hasNoCause();
            assertThat(fixture.client.serviceAccounts().inNamespace("kube-system").withLabel(IbmClusterIdentityTokens.OWNER,"true").list().getItems()).isEmpty();
        }
    }
    @ParameterizedTest @ValueSource(strings={"IAM 1 short","IAM 120 aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","IAM 86401 aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","IAM 3600 wrong\nextra","Bearer 3600 aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    void rejectsMalformedOrNearlyExpiredTokens(String input) {
        assertThatThrownBy(()->new IbmClusterIdentityTokens(new Time()).parse(input)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void tokenNeverAppearsInItsDiagnosticRepresentation() {
        assertThat(new IbmClusterIdentityTokens(new Time()).parse("IAM 3600 "+TOKEN).toString()).doesNotContain(TOKEN);
    }
    @Test void imageMustBeAReadyOfficialProviderDigest() {
        try(var fixture=new IbmAutomationClusterFixture(true)) {
            var reader=new IbmClusterIdentityTokens();
            assertThatThrownBy(()->reader.providerImage(fixture.client)).hasMessageContaining("ready IBM");
            var pod=new PodBuilder().withNewMetadata().withName("ibm-vpc-block-csi-controller-0").endMetadata()
                    .withNewSpec().addNewContainer().withName("provider").withImage("icr.io/obs/armada-storage-secret:v1.3.67").endContainer().endSpec()
                    .withNewStatus().addNewContainerStatus().withName("provider").withReady(true).withImageID(IMAGE).endContainerStatus().endStatus().build();
            fixture.client.pods().inNamespace("kube-system").resource(pod).create();
            assertThat(reader.providerImage(fixture.client)).isEqualTo(IMAGE);
            pod=fixture.client.pods().inNamespace("kube-system").withName(pod.getMetadata().getName()).get();
            pod.getStatus().getContainerStatuses().get(0).setImageID("untrusted.example/image@sha256:"+"a".repeat(64));
            fixture.client.pods().inNamespace("kube-system").resource(pod).update();
            assertThatThrownBy(()->reader.providerImage(fixture.client)).hasMessageContaining("ready IBM");
        }
    }
    @Test void preflightOnlyChecksPermissionsWithoutCreatingAHelper() throws Exception {
        var service=reader(new Time());
        try(var fixture=new IbmAutomationClusterFixture(true)) {
            service.check(fixture.client);
            assertThat(fixture.client.pods().inNamespace("kube-system").list().getItems()).isEmpty();
            assertThat(fixture.client.serviceAccounts().inNamespace("kube-system").list().getItems()).isEmpty();
            verify(service,never()).execute(any(),anyString());
        }
    }
    @Test void cleanupNeverDeletesAnUnownedOrReplacedAccount() {
        try(var fixture=new IbmAutomationClusterFixture(true)) {
            var account=fixture.client.serviceAccounts().inNamespace("kube-system").resource(new ServiceAccountBuilder()
                    .withNewMetadata().withName(IbmClusterIdentityTokens.PREFIX+"unowned").endMetadata().build()).create();
            assertThatThrownBy(()->new IbmClusterIdentityTokens().remove(fixture.client,account,ID)).isInstanceOf(IllegalArgumentException.class);
            assertThat(fixture.client.serviceAccounts().inNamespace("kube-system").withName(account.getMetadata().getName()).get()).isNotNull();
        }
    }
    @Test void clusterLoginNeverReadsOpenBao() {
        var legacy=mock(IbmOpenBaoCredentials.class);var identity=mock(IbmClusterIdentityTokens.class);
        var api=new IbmCloudIngressApi(legacy,identity);var profile=new IbmIngressAutomationProperties.Profile();profile.setRegion("jp-osa");
        try(var fixture=new IbmAutomationClusterFixture(true)) {
            when(identity.token(fixture.client,ID)).thenReturn(TOKEN);
            assertThat(api.login(fixture.client,profile,ID).vpc().toString()).isEqualTo("https://jp-osa.iaas.cloud.ibm.com");
            verifyNoInteractions(legacy);
        }
    }
}
