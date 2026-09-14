package kr.co.mcmp.softwarecatalog.kubernetes.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.StringWriter;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IbmIngressTlsResolverTest {
    static final String DOMAIN = "cluster-test.jp-osa.containers.appdomain.cloud";
    static final String HOST = "grafana." + DOMAIN;
    static final String SECRET = "ibm-default-cert";
    static final KeyPair RSA = keyPair("RSA");
    static final KeyPair EC = keyPair("EC");

    static KeyPair keyPair(String algorithm) {
        try { var generator = KeyPairGenerator.getInstance(algorithm); generator.initialize("RSA".equals(algorithm) ? 2048 : 256); return generator.generateKeyPair(); }
        catch (Exception e) { throw new AssertionError(e); }
    }

    static Secret certificate(String name, KeyPair key, String... hosts) {
        return certificate(name, key, Instant.now().minusSeconds(3600), Instant.now().plusSeconds(86400), hosts);
    }

    static Secret certificate(String name, KeyPair key, Instant from, Instant until, String... hosts) {
        try {
            var subject = new X500Name("CN=AM test certificate");
            var builder = new JcaX509v3CertificateBuilder(subject, BigInteger.ONE, Date.from(from), Date.from(until), subject, key.getPublic());
            builder.addExtension(Extension.subjectAlternativeName, false, new GeneralNames(Stream.of(hosts).map(h -> new GeneralName(GeneralName.dNSName, h)).toArray(GeneralName[]::new)));
            var cert = builder.build(new JcaContentSignerBuilder("RSA".equals(key.getPrivate().getAlgorithm()) ? "SHA256withRSA" : "SHA256withECDSA").build(key.getPrivate()));
            var writer = new StringWriter();
            try (var pem = new JcaPEMWriter(writer)) {
                // SunEC's private-key encoding keeps curve parameters in PKCS#8, not SEC1.
                // Retain that information; a bare SEC1 conversion would generate an invalid fixture.
                pem.writeObject("EC".equals(key.getPrivate().getAlgorithm())
                        ? new org.bouncycastle.util.io.pem.PemObject("PRIVATE KEY", key.getPrivate().getEncoded()) : key.getPrivate());
            }
            return new SecretBuilder().withNewMetadata().withName(name).withNamespace("default").endMetadata().withType("kubernetes.io/tls")
                    .withData(Map.of("tls.crt", Base64.getEncoder().encodeToString(cert.getEncoded()),
                            "tls.key", Base64.getEncoder().encodeToString(writer.toString().getBytes(java.nio.charset.StandardCharsets.US_ASCII)))).build();
        } catch (Exception e) { throw new AssertionError(e); }
    }

    static Resource<ConfigMap> stubBindings(KubernetesClient client, String json) {
        var ops = mock(io.fabric8.kubernetes.client.dsl.NonNamespaceOperation.class);
        var configMaps = client.configMaps();
        doReturn(ops).when(configMaps).inNamespace("default");
        Resource<ConfigMap> resource = mock(Resource.class);
        doReturn(resource).when(ops).withName(IbmIngressTlsResolver.BINDINGS_CONFIG_MAP);
        when(resource.get()).thenReturn(json == null ? null : new ConfigMapBuilder().withData(Map.of("bindings.json", json)).build());
        return resource;
    }

    static void configureDefault(KubernetesClient client) {
        stubBindings(client, null);
        var deployment = client.apps().deployments().inNamespace("kube-system").withLabel("ingress-class", IbmIngressSupport.PUBLIC_CLASS).list().getItems().get(0);
        deployment.setSpec(new DeploymentBuilder().withNewSpec().withNewTemplate().withNewSpec()
                .addNewContainer().withName("nginx").withArgs("--default-ssl-certificate=default/" + SECRET).endContainer()
                .endSpec().endTemplate().endSpec().build().getSpec());
        when(KubernetesIngressTlsWarningsTest.stubSecret(client, "default", SECRET).get()).thenReturn(certificate(SECRET, RSA, DOMAIN, "*." + DOMAIN));
    }

    static KubernetesClient client() {
        var client = IbmIngressSupportTest.readyClient(IbmIngressSupport.PUBLIC_CLASS, true, "10.150.0.0/24", "proxy-protocol", false);
        configureDefault(client);
        return client;
    }

    static kr.co.mcmp.softwarecatalog.application.dto.DeploymentConfigDTO config() {
        var config = HelmIngressValuesTest.config(); config.setIngressClass(IbmIngressSupport.PUBLIC_CLASS);
        config.setIngressHost(HOST); config.setIngressTlsEnabled(true); config.setIngressTlsSecret("stale-catalog-cert");
        return config;
    }

    @Test void explicitHttpsDiscoversDefaultWithoutChangingInputOrCluster() {
        var client = client(); var input = config();
        var resolved = IbmIngressTlsResolver.resolve(client, "default", input);
        assertThat(resolved.isTlsEnabled()).isTrue(); assertThat(resolved.getIngressTlsSecret()).isEqualTo(SECRET);
        assertThat(input.isTlsEnabled()).isTrue(); assertThat(input.getIngressTlsSecret()).isEqualTo("stale-catalog-cert");
        assertThat(IbmIngressSupport.verify(client, resolved)).isNotEmpty(); // real resolved mode selects 443, not 80
        var settings = IbmIngressTlsResolver.describe(client, "default", IbmIngressSupport.PUBLIC_CLASS);
        assertThat(settings.defaultDomain()).isEqualTo(DOMAIN); assertThat(settings.warnings()).isEmpty();
        assertThat(settings.toString()).doesNotContain("PRIVATE KEY", "tls.key", SECRET);
        verify(client.secrets().inNamespace("default").withName(SECRET), never()).create();
    }

    @ParameterizedTest @ValueSource(strings={"rclone.test.com", "example.test", HOST})
    void httpDoesNotReadCertificatesOrBindingsAndClearsHiddenTlsSecret(String host) {
        var client = mock(KubernetesClient.class); var input = config();
        input.setIngressHost(host); input.setIngressTlsEnabled(false);
        var resolved = IbmIngressTlsResolver.resolve(client, "default", input);
        assertThat(resolved.isTlsEnabled()).isFalse(); assertThat(resolved.getIngressTlsSecret()).isNull();
        var request = new kr.co.mcmp.softwarecatalog.application.dto.DeploymentRequest();
        IbmIngressTlsResolver.apply(request, resolved);
        assertThat(request.getIngressTlsEnabled()).isFalse(); assertThat(request.getIngressTlsSecret()).isNull();
        verifyNoInteractions(client);
    }

    @Test void customBindingWinsOverDefaultAndHonorsEcCertificates() {
        var client = client(); stubBindings(client, "[{\"host\":\"*.company.com\",\"secretName\":\"company-tls\"}]");
        when(KubernetesIngressTlsWarningsTest.stubSecret(client, "default", "company-tls").get()).thenReturn(certificate("company-tls", EC, "*.company.com"));
        var config = config(); config.setIngressHost("grafana.company.com");
        assertThat(IbmIngressTlsResolver.resolve(client, "default", config).getIngressTlsSecret()).isEqualTo("company-tls");
        assertThat(IbmIngressTlsResolver.describe(client, "default", IbmIngressSupport.PUBLIC_CLASS).customDomains()).containsExactly("*.company.com");
    }

    @Test void ecPemKeyPairVerification() throws Exception {
        var secret = certificate("test", EC, "*.company.com");
        var cert = (java.security.cert.X509Certificate) java.security.cert.CertificateFactory.getInstance("X.509").generateCertificate(
                new java.io.ByteArrayInputStream(Base64.getDecoder().decode(secret.getData().get("tls.crt"))));
        IbmIngressTlsResolver.verifyKeyPair(cert, new String(Base64.getDecoder().decode(secret.getData().get("tls.key")), java.nio.charset.StandardCharsets.US_ASCII));
        var ec = (java.security.interfaces.ECPrivateKey) EC.getPrivate();
        var sec1 = new org.bouncycastle.asn1.sec.ECPrivateKey(256, ec.getS(), new org.bouncycastle.asn1.ASN1ObjectIdentifier("1.2.840.10045.3.1.7"));
        var text = new StringWriter();
        try (var pem = new JcaPEMWriter(text)) { pem.writeObject(new org.bouncycastle.util.io.pem.PemObject("EC PRIVATE KEY", sec1.getEncoded())); }
        IbmIngressTlsResolver.verifyKeyPair(cert, text.toString());
    }

    @Test void exactBindingOverridesWildcardAndDoesNotFallBackIfItsSecretIsBroken() {
        assertThat(IbmIngressTlsResolver.selectBinding(Map.of("*.company.com", "wild", "app.company.com", "exact"), "app.company.com")).isEqualTo("exact");
        var client = client(); stubBindings(client, "[{\"host\":\"" + HOST + "\",\"secretName\":\"missing-cert\"}]");
        when(KubernetesIngressTlsWarningsTest.stubSecret(client, "default", "missing-cert").get()).thenReturn(null);
        assertThatThrownBy(() -> IbmIngressTlsResolver.resolve(client, "default", config())).hasMessageContaining("does not exist");
    }

    @ParameterizedTest @ValueSource(strings={"grafana.company.com", "dev.grafana." + DOMAIN, "other-cluster.jp-osa.containers.appdomain.cloud", "*." + DOMAIN})
    void rejectsUnregisteredAndWildcardHosts(String host) {
        var config = config(); config.setIngressHost(host);
        assertThatThrownBy(() -> IbmIngressTlsResolver.resolve(client(), "default", config)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void wildcardMatchesOneDnsLabelOnly() {
        assertThat(IbmIngressTlsResolver.matches("*.company.com", "grafana.company.com")).isTrue();
        for (String host : List.of("company.com", "dev.grafana.company.com", "company.com.attacker.example", "*.company.com", "-bad.company.com"))
            assertThat(IbmIngressTlsResolver.matches("*.company.com", host)).as(host).isFalse();
    }

    @Test void registeredHostMustStillBeCoveredByCertificate() {
        var client = client(); stubBindings(client, "[{\"host\":\"grafana.company.com\",\"secretName\":\"" + SECRET + "\"}]");
        var config = config(); config.setIngressHost("grafana.company.com");
        assertThatThrownBy(() -> IbmIngressTlsResolver.resolve(client, "default", config)).hasMessageContaining("does not cover");
    }

    @ParameterizedTest @ValueSource(strings={"expired", "future", "mismatched-key", "bad-cert", "bad-key", "wrong-type", "missing-cert", "missing-key"})
    void invalidCertificateBlocksWithoutExposingSecretContents(String kind) {
        var client = client(); Secret secret = certificate(SECRET, RSA, "*." + DOMAIN);
        if (kind.equals("expired")) secret = certificate(SECRET, RSA, Instant.now().minusSeconds(86400), Instant.now().minusSeconds(60), "*." + DOMAIN);
        if (kind.equals("future")) secret = certificate(SECRET, RSA, Instant.now().plusSeconds(3600), Instant.now().plusSeconds(86400), "*." + DOMAIN);
        secret.setData(new HashMap<>(secret.getData()));
        if (kind.equals("mismatched-key")) secret.getData().put("tls.key", certificate("other", EC, "*." + DOMAIN).getData().get("tls.key"));
        if (kind.equals("bad-cert")) secret.getData().put("tls.crt", "sensitive-invalid-data");
        if (kind.equals("bad-key")) secret.getData().put("tls.key", "sensitive-invalid-data");
        if (kind.equals("wrong-type")) secret.setType("Opaque");
        if (kind.equals("missing-cert")) secret.getData().remove("tls.crt");
        if (kind.equals("missing-key")) secret.getData().remove("tls.key");
        when(client.secrets().inNamespace("default").withName(SECRET).get()).thenReturn(secret);
        assertThatThrownBy(() -> IbmIngressTlsResolver.resolve(client, "default", config())).hasMessageContaining("currently valid certificate").hasMessageNotContaining("sensitive").hasMessageNotContaining("PRIVATE KEY");
    }

    @ParameterizedTest @ValueSource(strings={"", "{}", "null", "[1]", "[{\"host\":\"*.company.com\"}]", "[{\"host\":\"*.*.com\",\"secretName\":\"cert\"}]", "[{\"host\":\"app.company.com\",\"secretName\":\"default/cert\"}]", "[{\"host\":\"app.company.com\",\"secretName\":\"cert\",\"extra\":true}]", "[{\"host\":\"app.company.com\",\"secretName\":\"a\"},{\"host\":\"app.company.com\",\"secretName\":\"b\"}]"})
    void malformedAdministratorBindingsFailClosed(String json) {
        var client = client(); stubBindings(client, json);
        assertThatThrownBy(() -> IbmIngressTlsResolver.resolve(client, "default", config())).hasMessageContaining("Invalid am-ingress-tls-bindings");
    }

    @Test void readFailuresDoNotLeakUpstreamBodies() {
        var client = client(); when(client.secrets().inNamespace("default").withName(SECRET).get()).thenThrow(new IllegalStateException("private content"));
        assertThatThrownBy(() -> IbmIngressTlsResolver.resolve(client, "default", config())).hasMessageContaining("Secret read permission").hasMessageNotContaining("private content");
        when(stubBindings(client, null).get()).thenThrow(new IllegalStateException("private content"));
        assertThatThrownBy(() -> IbmIngressTlsResolver.resolve(client, "default", config())).hasMessageContaining("ConfigMap read permission").hasMessageNotContaining("private content");
    }

    @Test void rereadsRotatedSecretsAndDoesNotCacheTheCertificate() {
        var client = client(); assertThat(IbmIngressTlsResolver.resolve(client, "default", config()).isTlsEnabled()).isTrue();
        when(client.secrets().inNamespace("default").withName(SECRET).get()).thenReturn(certificate(SECRET, EC, "other.example.com"));
        assertThatThrownBy(() -> IbmIngressTlsResolver.resolve(client, "default", config())).hasMessageContaining("does not identify one IBM Ingress domain");
    }

    @Test void missingOrDifferentDefaultReferencesAreRejected() {
        var client = client(); var items = client.apps().deployments().inNamespace("kube-system").withLabel("ingress-class", IbmIngressSupport.PUBLIC_CLASS).list().getItems();
        items.get(0).getSpec().getTemplate().getSpec().getContainers().get(0).setArgs(List.of("--default-ssl-certificate", "other/" + SECRET));
        assertThatThrownBy(() -> IbmIngressTlsResolver.resolve(client, "default", config())).hasMessageContaining("another namespace");
        items.get(0).getSpec().getTemplate().getSpec().getContainers().get(0).setArgs(List.of());
        assertThatThrownBy(() -> IbmIngressTlsResolver.resolve(client, "default", config())).hasMessageContaining("Cannot identify");
    }

    @Test void otherProvidersAndDisabledIngressRemainUnchangedAndDoNotReadSecrets() {
        var client = mock(KubernetesClient.class); var config = config(); config.setIngressClass("nginx");
        assertThat(IbmIngressTlsResolver.resolve(client, "default", config)).isSameAs(config);
        config.setIngressClass(IbmIngressSupport.PUBLIC_CLASS); config.setIngressEnabled(false);
        assertThat(IbmIngressTlsResolver.resolve(client, "default", config)).isSameAs(config);
        verifyNoInteractions(client);
    }

    @Test void selectedSecretFlowsThroughEveryChartProfileAndHistoryRequest() {
        var config = IbmIngressTlsResolver.resolve(client(), "default", config());
        var request = new kr.co.mcmp.softwarecatalog.application.dto.DeploymentRequest();
        IbmIngressTlsResolver.apply(request, config);
        assertThat(request.getIngressTlsSecret()).isEqualTo(SECRET); assertThat(request.getIngressTlsEnabled()).isTrue();
        for (var chart : List.of(HelmIngressValuesTest.chart("grafana", "https://grafana.github.io/helm-charts", "7.3.0"),
                HelmIngressValuesTest.chart("nginx", "https://charts.bitnami.com/bitnami", "21.1.23"),
                HelmIngressValuesTest.chart("prometheus", "https://prometheus-community.github.io/helm-charts", "25.8.0"),
                HelmIngressValuesTest.chart("rclone", "https://jacobcolvin.com/helm-charts", "1.0.1"),
                HelmIngressValuesTest.chart("unknown", "https://example.com", "1.0.0"))) {
            assertThat(HelmIngressValues.from(chart, config).toString()).contains(SECRET, HOST, IbmIngressSupport.PUBLIC_CLASS).doesNotContain("stale-catalog-cert");
        }
    }
}
