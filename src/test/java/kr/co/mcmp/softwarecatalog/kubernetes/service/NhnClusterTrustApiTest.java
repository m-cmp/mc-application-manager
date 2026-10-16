package kr.co.mcmp.softwarecatalog.kubernetes.service;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NhnClusterTrustApiTest {
    private final NhnTestHttp http = new NhnTestHttp();
    private final NhnNksApi api = new NhnNksApi(http.client);
    private final NhnClusterIdentitySessions.TrustCredential credential =
            new NhnClusterIdentitySessions.TrustCredential("trustee-a", "fake-password", "trust-a");
    private final String endpoint = "https://kr1-api-kubernetes-infrastructure.nhncloudservice.com/v1/";
    NhnClusterTrustApiTest() throws Exception { }
    private void response(String trust, String region, String target, Instant expiry) {
        http.respond(201, NhnTestHttp.trustBody(trust, region, target, expiry), Map.of("X-Subject-Token", List.of("fake-trust-token")));
    }
    @Test void authenticatesTheExistingTrustOverFixedOfficialHttps() throws Exception {
        response("trust-a", "KR1", endpoint, Instant.now().plusSeconds(3600));
        var session = api.loginTrust(credential, "kr1");
        assertThat(session.tenantId()).isEqualTo("tenant-a");
        assertThat(session.endpoint().toString()).isEqualTo(endpoint);
        var request = http.requests.get(0);
        assertThat(request.uri().toString()).isEqualTo("https://api-identity-infrastructure.nhncloudservice.com/v3/auth/tokens");
        var auth = new ObjectMapper().readTree(NhnTestHttp.body(request)).path("auth");
        assertThat(auth.path("scope").path("OS-TRUST:trust").path("id").asText()).isEqualTo("trust-a");
        assertThat(auth.path("identity").path("password").path("user").path("id").asText()).isEqualTo("trustee-a");
        assertThat(session.toString()).doesNotContain("fake-trust-token");
    }
    @Test void rejectsDifferentTrust() {
        response("trust-b", "KR1", endpoint, Instant.now().plusSeconds(3600));
        assertThatThrownBy(() -> api.loginTrust(credential, "kr1")).hasMessageContaining("valid cluster trust token");
    }
    @Test void rejectsAnExpiredTrustToken() {
        response("trust-a", "KR1", endpoint, Instant.now().minusSeconds(1));
        assertThatThrownBy(() -> api.loginTrust(credential, "kr1")).isInstanceOf(StorageOperationException.class);
    }
    @Test void rejectsAnotherRegionWithoutSendingTheToken() {
        response("trust-a", "KR2", endpoint, Instant.now().plusSeconds(3600));
        assertThatThrownBy(() -> api.loginTrust(credential, "kr1")).isInstanceOf(StorageOperationException.class);
        assertThat(http.requests).hasSize(1);
    }
    @ParameterizedTest @ValueSource(strings = {
            "http://kr1-api-kubernetes-infrastructure.nhncloudservice.com/v1/",
            "https://kr1-api-kubernetes-infrastructure.nhncloudservice.com.attacker.example/v1/",
            "https://kr2-api-kubernetes-infrastructure.nhncloudservice.com/v1/",
            "https://kr1-api-kubernetes-infrastructure.nhncloudservice.com:8443/v1/",
            "https://kr1-api-kubernetes-infrastructure.nhncloudservice.com/v1/?secret=x"})
    void rejectsAnUnsafeCatalogBeforeSendingTheTrustToken(String target) {
        response("trust-a", "KR1", target, Instant.now().plusSeconds(3600));
        assertThatThrownBy(() -> api.loginTrust(credential, "kr1")).isInstanceOf(StorageOperationException.class);
        assertThat(http.requests).hasSize(1);
    }
    @ParameterizedTest @ValueSource(ints = {301, 400, 401, 403, 500})
    void authenticationErrorsDoNotRelaySecretResponses(int status) {
        http.respond(status, "fake-password fake-trust-token");
        assertThatThrownBy(() -> api.loginTrust(credential, "kr1"))
                .isInstanceOf(StorageOperationException.class).hasMessageNotContaining("fake-").hasNoCause();
        assertThat(http.requests).hasSize(1);
    }
    @Test void missingSubjectTokenIsRejected() {
        http.respond(201, NhnTestHttp.trustBody("trust-a", "KR1", endpoint, Instant.now().plusSeconds(3600)), Map.of());
        assertThatThrownBy(() -> api.loginTrust(credential, "kr1")).isInstanceOf(StorageOperationException.class);
    }
}
