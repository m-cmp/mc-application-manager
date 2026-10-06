package kr.co.mcmp.softwarecatalog.kubernetes.service;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NhnNksApiTest {
    private final NhnTestHttp http = new NhnTestHttp();
    private final NhnNksApi api = new NhnNksApi(http.client);
    private final ObjectMapper json = new ObjectMapper();
    private final NhnOpenBaoCredentials.Credentials credential = new NhnOpenBaoCredentials.Credentials(
            "https://api-identity-infrastructure.nhncloudservice.com/v2.0", "fake-user", "fake-secret-password", "tenant-a");
    private final NhnNksApi.Session session = new NhnNksApi.Session("fake-nhn-token", "tenant-a",
            URI.create("https://kr1-api-kubernetes-infrastructure.nhncloudservice.com/v1/"), Instant.now().plusSeconds(3600));
    NhnNksApiTest() throws Exception { }

    @Test void issuesProjectScopedIaasTokenAndUsesCatalogEndpoint() throws Exception {
        http.respond(200, NhnTestHttp.loginBody("tenant-a", session.endpoint().toString()));
        var result = api.login(credential, "kr1");
        assertThat(result.endpoint()).isEqualTo(session.endpoint());
        assertThat(http.requests.get(0).uri().toString()).endsWith("/v2.0/tokens");
        var body = json.readTree(NhnTestHttp.body(http.requests.get(0)));
        assertThat(body.path("auth").path("tenantId").asText()).isEqualTo("tenant-a");
        assertThat(body.path("auth").path("passwordCredentials").path("password").asText()).isEqualTo("fake-secret-password");
        assertThat(result.toString()).doesNotContain("fake-nhn-token"); assertThat(credential.toString()).doesNotContain("fake-secret-password");
    }
    @Test void rejectsTokenFromAnotherProject() {
        http.respond(200, NhnTestHttp.loginBody("tenant-b", session.endpoint().toString()));
        assertThatThrownBy(() -> api.login(credential, "kr1")).hasMessageContaining("valid project token");
    }
    @Test void rejectsForeignCatalogEndpointBeforeSendingNhnToken() {
        http.respond(200, NhnTestHttp.loginBody("tenant-a", "https://nhncloudservice.com.attacker.example/v1/"));
        assertThatThrownBy(() -> api.login(credential, "kr1")).isInstanceOf(StorageOperationException.class);
        assertThat(http.requests).hasSize(1);
    }
    @Test void rejectsForeignIdentityBeforeSendingPassword() {
        var bad = new NhnOpenBaoCredentials.Credentials("https://attacker.example/v2.0", "fake-user", "fake-secret-password", "tenant-a");
        assertThatThrownBy(() -> api.login(bad, "kr1")).hasMessageContaining("official NHN Identity"); assertThat(http.requests).isEmpty();
    }
    @Test void compatibleLookupUsesAllThreeFiltersAndNumericalVersionOrder() throws Exception {
        http.respond(200, """
                {"addons":[{"name":"cinder-csi-plugin","version":"v1.9.0-nks2"},
                {"name":"cinder-csi-plugin","version":"v1.10.0-nks1"},
                {"name":"cinder-csi-plugin","version":"v1.10.0-nks2"},{"name":"other","version":"v99.0.0"}]}
                """);
        var cluster = json.readTree("{\"coe_version\":\"v1.30.0\",\"labels\":{\"node_image\":\"image-a\",\"platform_version\":\"1.202605.0\"}}");
        assertThat(api.compatibleVersion(session, cluster)).isEqualTo("v1.10.0-nks2");
        assertThat(http.requests.get(0).uri().getQuery()).contains("k8s_version=v1.30.0", "image=image-a", "platform_version=1.202605.0");
    }
    @Test void incompleteCompatibilityMetadataCannotSubmitInstallation() throws Exception {
        assertThatThrownBy(() -> api.compatibleVersion(session, json.readTree("{\"coe_version\":\"v1.30.0\"}")))
                .hasMessageContaining("base image and platform"); assertThat(http.requests).isEmpty();
    }
    @Test void installationOnlyRequestsCinderWithNoConflictOverwrite() throws Exception {
        http.respond(200, "{\"uuid\":\"" + NhnCinderAddonServiceTest.ID + "\"}");
        api.install(session, NhnCinderAddonServiceTest.ID, "v1.30.0-nks1");
        var request = http.requests.get(0);
        assertThat(request.method()).isEqualTo("POST"); assertThat(request.uri().getPath()).endsWith("/" + NhnCinderAddonServiceTest.ID + "/addons/");
        assertThat(request.headers().firstValue("X-Auth-Token")).contains("fake-nhn-token");
        assertThat(request.headers().firstValue("OpenStack-API-Version")).contains("container-infra latest");
        assertThat(json.readTree(NhnTestHttp.body(request)).path("resolve_conflicts").asText()).isEqualTo("none");
        assertThat(json.readTree(NhnTestHttp.body(request)).path("name").asText()).isEqualTo("cinder-csi-plugin");
    }
    @ParameterizedTest @ValueSource(ints = {301, 400, 401, 403, 409, 500})
    void upstreamErrorsNeverRelaySecretBodyOrCause(int status) {
        http.respond(status, "fake-secret-password-and-token");
        assertThatThrownBy(() -> api.cluster(session, NhnCinderAddonServiceTest.ID)).isInstanceOf(StorageOperationException.class)
                .hasMessageNotContaining("fake-secret").hasNoCause();
    }
    @Test void wrongNativeProjectCannotBeUsedForInstallation() {
        http.respond(200, "{\"uuid\":\"" + NhnCinderAddonServiceTest.ID + "\",\"project_id\":\"tenant-b\"}");
        assertThatThrownBy(() -> api.cluster(session, NhnCinderAddonServiceTest.ID)).hasMessageContaining("does not match");
    }
    @Test void wrongInstalledAddonTargetCannotBeReused() {
        http.respond(200, "{\"addons\":[{\"name\":\"cinder-csi-plugin\",\"cluster_uuid\":\"other\",\"project_id\":\"tenant-a\"}]}");
        assertThatThrownBy(() -> api.installed(session, NhnCinderAddonServiceTest.ID)).hasMessageContaining("different NKS cluster");
    }
    @Test void expiredTokenStopsBeforeSendingRequest() {
        var expired = new NhnNksApi.Session("fake", "tenant-a", session.endpoint(), Instant.now().minusSeconds(1));
        assertThatThrownBy(() -> api.cluster(expired, NhnCinderAddonServiceTest.ID)).hasMessageContaining("expired"); assertThat(http.requests).isEmpty();
    }
}
