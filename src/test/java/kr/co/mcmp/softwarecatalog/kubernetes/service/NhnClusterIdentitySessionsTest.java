package kr.co.mcmp.softwarecatalog.kubernetes.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.*;

class NhnClusterIdentitySessionsTest {
    private final KubernetesServer kube = new KubernetesServer(false, true);
    private final NhnNksApi nks = mock(NhnNksApi.class);
    private final NhnClusterIdentitySessions identities = new NhnClusterIdentitySessions(nks);
    @BeforeEach void start() { kube.before(); }
    @AfterEach void stop() { kube.after(); }
    private String encoded(String text) { return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8)); }
    private void secret(String password) {
        kube.getClient().secrets().inNamespace("kube-system").resource(new SecretBuilder()
                .withNewMetadata().withName("os-trustee").endMetadata().withType("Opaque")
                .withData(Map.of("os-trusteeID", encoded("trustee-a"), "os-trusteePassword", encoded(password),
                        "os-trustID", encoded("trust-a"), "os-authURL", encoded("http://169.254.169.241/v3"),
                        "os-region", encoded("RegionOne"))).build()).create();
    }
    @Test void readsOnlyTheFixedSelectedClusterTrustAndUsesThePublicConnectionRegion() {
        secret("fake-password"); identities.login(kube.getClient(), "kr1");
        verify(nks).loginTrust(new NhnClusterIdentitySessions.TrustCredential("trustee-a", "fake-password", "trust-a"), "kr1");
        assertThat(new NhnClusterIdentitySessions.TrustCredential("trustee-a", "fake-password", "trust-a").toString())
                .doesNotContain("fake-password", "trust-a", "trustee-a");
    }
    @Test void missingTrustFailsWithNoFallbackOrSecretCause() {
        assertThatThrownBy(() -> identities.login(kube.getClient(), "kr1"))
                .isInstanceOf(StorageOperationException.class).hasMessageContaining("os-trustee").hasNoCause();
        verifyNoInteractions(nks);
    }
    @Test void malformedSecretDoesNotSendCredentialsOrLeakThem() {
        secret("fake-password\n");
        assertThatThrownBy(() -> identities.login(kube.getClient(), "kr1"))
                .hasMessageNotContaining("fake-password").hasNoCause(); verifyNoInteractions(nks);
    }
    @Test void publicRegionCannotBeReplacedByTheInternalSecretRegion() {
        secret("fake-password");
        assertThatThrownBy(() -> identities.login(kube.getClient(), "RegionOne")).hasMessageContaining("NKS region");
        verifyNoInteractions(nks);
    }
}
