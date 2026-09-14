package kr.co.mcmp.softwarecatalog.kubernetes.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import io.fabric8.kubernetes.api.model.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IbmCertificateManagerTest {
    @TempDir Path directory;
    final IbmIngressAutomationProperties properties=new IbmIngressAutomationProperties();
    final IbmCertificateManager manager=spy(new IbmCertificateManager(properties,null));
    IbmIngressAutomationProperties.Profile profile() throws Exception {
        var profile=new IbmIngressAutomationProperties.Profile(); profile.setAllowedDomains(List.of("company.com")); profile.setInstallCertManager(true);
        var issuer=directory.resolve("issuer.yaml");
        Files.writeString(issuer,"""
                apiVersion: cert-manager.io/v1
                kind: ClusterIssuer
                metadata:
                  name: am-issuer
                spec:
                  acme:
                    email: ops@company.com
                    server: https://acme-v02.api.letsencrypt.org/directory
                    privateKeySecretRef:
                      name: am-acme-account
                    solvers:
                    - dns01:
                        cloudflare:
                          apiTokenSecretRef:
                            name: am-dns
                            key: api-token
                """);
        var secret=directory.resolve("dns-secret.yaml"); Files.writeString(secret,"""
                apiVersion: v1
                kind: Secret
                metadata:
                  name: am-dns
                  namespace: cert-manager
                type: Opaque
                stringData:
                  api-token: fake-DNS-token-for-unit-test
                """);
        profile.setIssuerTemplateFile(issuer.toString()); profile.setDnsSecretFiles(List.of(secret.toString())); return profile;
    }
    @ParameterizedTest @ValueSource(strings={"company.com","grafana.company.com","deep.app.company.com"})
    void allowedDomainSuffixMatchesOnDnsLabelBoundaries(String host) throws Exception { assertThat(profile().allowsHost(host)).isTrue(); }
    @ParameterizedTest @ValueSource(strings={"evilcompany.com","company.com.evil.net","*.company.com","http://company.com","127.0.0.1","test.com",""})
    void foreignOrInvalidHostsAreNeverAutomaticallyIssued(String host) throws Exception { assertThat(profile().allowsHost(host)).isFalse(); }

    @Test void installOnlyWhenAbsentAndReuseOneCertificateAndExistingBindings() throws Exception {
        var profile=profile(); String host="grafana.company.com";
        try(var fixture=new IbmAutomationClusterFixture(true)) {
            fixture.server.getKubernetesMockServer().setVersionInfo(new io.fabric8.kubernetes.client.VersionInfo.Builder().withMajor("1").withMinor("32").build());
            fixture.client.configMaps().inNamespace("default").resource(new ConfigMapBuilder().withNewMetadata().withName(IbmIngressTlsResolver.BINDINGS_CONFIG_MAP).endMetadata()
                    .withData(Map.of("other-data","keep","bindings.json","[{\"host\":\"existing.company.com\",\"secretName\":\"existing-tls\"}]")).build()).create();
            var plan=manager.plan(fixture.client,profile,host); assertThat(plan.install).isTrue();
            assertThat(fixture.client.secrets().inNamespace("cert-manager").list().getItems()).isEmpty(); // read-only plan
            doAnswer(call -> { fixture.certManagerPresent(); return null; }).when(manager).install("project-a","cluster-a");
            doAnswer(call -> {
                var issuers=fixture.client.genericKubernetesResources(IbmCertificateManager.ISSUER);
                var issuer=issuers.withName("am-issuer").get();
                makeReady(issuer); issuers.resource(issuer).update();
                var certs=fixture.client.genericKubernetesResources(IbmCertificateManager.CERT).inNamespace("default");
                var cert=certs.withName(IbmCertificateManager.certificateName(host)).get();
                if (cert!=null) {
                    makeReady(cert); certs.resource(cert).update();
                    if (fixture.client.secrets().inNamespace("default").withName(cert.getMetadata().getName()).get()==null)
                        fixture.client.secrets().inNamespace("default").resource(IbmIngressTlsResolverTest.certificate(cert.getMetadata().getName(),IbmIngressTlsResolverTest.RSA,host)).create();
                }
                assertThat(((BooleanSupplier)call.getArgument(0)).getAsBoolean()).isTrue(); return null;
            }).when(manager).await(any(),anyString());
            manager.prepare(fixture.client,"project-a","cluster-a","default",host,plan,m -> {});
            var second=manager.plan(fixture.client,profile,host); assertThat(second.install).isFalse();
            manager.prepare(fixture.client,"project-a","cluster-a","default",host,second,m -> {});
            verify(manager,times(1)).install(any(),any());
            assertThat(fixture.client.genericKubernetesResources(IbmCertificateManager.CERT).inNamespace("default").list().getItems()).hasSize(1);
            var bindings=IbmIngressTlsResolver.bindings(fixture.client,"default");
            assertThat(bindings).containsEntry("existing.company.com","existing-tls").containsEntry(host,IbmCertificateManager.certificateName(host));
            assertThat(fixture.client.configMaps().inNamespace("default").withName(IbmIngressTlsResolver.BINDINGS_CONFIG_MAP).get().getData()).containsEntry("other-data","keep");
            var cfg=IbmIngressTlsResolverTest.config(); cfg.setIngressHost(host);
            assertThat(IbmIngressTlsResolver.resolve(fixture.client,"default",cfg).getIngressTlsSecret()).isEqualTo(IbmCertificateManager.certificateName(host));
        }
    }
    @Test void neverOverwritesExistingIssuerOrDnsCredentials() throws Exception {
        var profile=profile();
        try(var fixture=new IbmAutomationClusterFixture(true)) {
            fixture.certManagerPresent();
            fixture.client.secrets().inNamespace("cert-manager").resource(new SecretBuilder().withNewMetadata().withName("am-dns").endMetadata()
                    .withType("Opaque").withData(Map.of("api-token","ZGlmZmVyZW50")).build()).create();
            assertThatThrownBy(() -> manager.plan(fixture.client,profile,"app.company.com")).hasMessageContaining("different data");
            assertThat(fixture.client.secrets().inNamespace("cert-manager").withName("am-dns").get().getData()).containsEntry("api-token","ZGlmZmVyZW50");
        }
    }
    @Test void installationRequiresExplicitOptInAndCompatibleVersion() throws Exception {
        var profile=profile();
        try(var fixture=new IbmAutomationClusterFixture(true)) {
            profile.setInstallCertManager(false);
            assertThatThrownBy(() -> manager.plan(fixture.client,profile,"app.company.com")).hasMessageContaining("install-cert-manager");
            profile.setInstallCertManager(true);
            fixture.server.getKubernetesMockServer().setVersionInfo(new io.fabric8.kubernetes.client.VersionInfo.Builder().withMajor("1").withMinor("30").build());
            assertThatThrownBy(() -> manager.plan(fixture.client,profile,"app.company.com")).hasMessageContaining("not supported");
        }
    }
    @Test void statusMustBeReadyForCurrentGeneration() {
        var cert=IbmCertificateManager.certificateResource("default","app.company.com","issuer"); cert.getMetadata().setGeneration(2L);
        assertThat(IbmCertificateManager.ready(cert)).isFalse(); makeReady(cert);
        assertThat(IbmCertificateManager.ready(cert)).isTrue(); cert.getMetadata().setGeneration(3L);
        assertThat(IbmCertificateManager.ready(cert)).isFalse();
    }
    @Test void missingDnsCredentialKeyFailsWithoutInstallingAnything() throws Exception {
        var profile=profile(); profile.setDnsSecretFiles(List.of());
        try(var fixture=new IbmAutomationClusterFixture(true)) {
            fixture.certManagerPresent();
            assertThatThrownBy(() -> manager.plan(fixture.client,profile,"app.company.com")).hasMessageContaining("credential Secret/key");
            verify(manager,never()).install(any(),any());
            assertThat(fixture.client.genericKubernetesResources(IbmCertificateManager.ISSUER).list().getItems()).isEmpty();
        }
    }
    @Test void partialOrLegacyCrdInstallationIsNeverOverwritten() throws Exception {
        var profile=profile();
        try(var fixture=new IbmAutomationClusterFixture(true)) {
            fixture.certManagerPresent();
            var crds=fixture.client.apiextensions().v1().customResourceDefinitions();
            var old=crds.withName("certificates.cert-manager.io").get();
            old.getSpec().getVersions().get(0).setServed(false); crds.resource(old).update();
            assertThatThrownBy(() -> manager.plan(fixture.client,profile,"app.company.com")).hasMessageContaining("must serve");
            crds.withName("certificates.cert-manager.io").delete();
            assertThatThrownBy(() -> manager.plan(fixture.client,profile,"app.company.com")).hasMessageContaining("Partial");
            verify(manager,never()).install(any(),any());
        }
    }
    @Test void deniedKubernetesPrivilegesFailDuringPreflight() throws Exception {
        var profile=profile();
        try(var fixture=new IbmAutomationClusterFixture(true)) {
            fixture.certManagerPresent();
            fixture.server.getKubernetesMockServer().clearExpectations();
            fixture.server.expect().post().withPath("/apis/authorization.k8s.io/v1/selfsubjectaccessreviews")
                    .andReturn(201,new io.fabric8.kubernetes.api.model.authorization.v1.SelfSubjectAccessReviewBuilder()
                            .withNewStatus().withAllowed(false).endStatus().build()).always();
            assertThatThrownBy(() -> manager.plan(fixture.client,profile,"app.company.com")).hasMessageContaining("permission required");
            verify(manager,never()).install(any(),any());
        }
    }
    @Test void rejectHttp01AndArbitraryAcmeEndpointsBeforeClusterCalls() throws Exception {
        var profile=profile(); var issuer=Path.of(profile.getIssuerTemplateFile()); String yaml=Files.readString(issuer);
        var client=mock(io.fabric8.kubernetes.client.KubernetesClient.class);
        for(String invalid : List.of(yaml.replace("dns01:","http01:"),yaml.replace("https://acme-v02.api.letsencrypt.org/directory","https://untrusted.example/issue"))) {
            Files.writeString(issuer,invalid);
            assertThatThrownBy(() -> manager.plan(client,profile,"app.company.com")).hasMessageContaining("Invalid server-side");
        }
        verifyNoInteractions(client);
    }
    @Test void serverDefaultsAreAcceptedButChangedHostsAndIssuerAreRejected() {
        var desired=IbmCertificateManager.certificateResource("default","app.company.com","issuer");
        var actual=IbmCertificateManager.certificateResource("default","app.company.com","issuer");
        var spec=new HashMap<>((Map<String,Object>)actual.getAdditionalProperties().get("spec")); spec.put("revisionHistoryLimit",1); actual.setAdditionalProperty("spec",spec);
        assertThat(IbmCertificateManager.sameSpec(actual,desired)).isTrue(); spec.put("dnsNames",List.of("different.company.com"));
        assertThat(IbmCertificateManager.sameSpec(actual,desired)).isFalse();
    }
    @Test void issuerSolverAdmissionDefaultsAreAcceptedButDifferentDnsZonesAreNot() throws Exception {
        var profile=profile();
        try(var fixture=new IbmAutomationClusterFixture(true)) {
            fixture.certManagerPresent();
            var desired=manager.plan(fixture.client,profile,"app.company.com").issuer;
            var json=new com.fasterxml.jackson.databind.ObjectMapper();
            var actual=json.valueToTree(desired);
            ((com.fasterxml.jackson.databind.node.ObjectNode)actual.path("spec").path("acme").path("solvers").get(0).path("dns01"))
                    .put("cnameStrategy","None");
            assertThat(IbmCertificateManager.sameSpec(json.treeToValue(actual,GenericKubernetesResource.class),desired)).isTrue();
            ((com.fasterxml.jackson.databind.node.ObjectNode)actual.path("spec").path("acme").path("solvers").get(0).path("dns01").path("cloudflare").path("apiTokenSecretRef"))
                    .put("name","different-credentials");
            assertThat(IbmCertificateManager.sameSpec(json.treeToValue(actual,GenericKubernetesResource.class),desired)).isFalse();
        }
    }
    static void makeReady(GenericKubernetesResource resource) {
        long generation=resource.getMetadata().getGeneration()==null?1:resource.getMetadata().getGeneration();
        resource.getMetadata().setGeneration(generation);
        resource.setAdditionalProperty("status",Map.of("conditions",List.of(Map.of("type","Ready","status","True","observedGeneration",generation))));
    }
}
