package kr.co.mcmp.softwarecatalog.kubernetes.service;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NhnOpenBaoCredentialsTest {
    @TempDir Path directory;
    private final NhnTestHttp http = new NhnTestHttp();
    NhnOpenBaoCredentialsTest() throws Exception { }
    private NhnOpenBaoCredentials reader() throws Exception {
        var p = NhnCinderAddonServiceTest.configured(); p.setOpenBaoUrl("http://openbao:8200");
        var token = directory.resolve("token"); Files.writeString(token, "fake-read-only-token\n"); p.setOpenBaoTokenFile(token.toString());
        return new NhnOpenBaoCredentials(p, http.client);
    }
    @Test void readsExistingNhnKvV2SecretUsingServerSideTokenFile() throws Exception {
        http.respond(200, "{\"data\":{\"data\":{\"NHN_IDENTITY_ENDPOINT\":\"https://api-identity-infrastructure.nhncloudservice.com/v2.0\",\"NHN_USERNAME\":\"fake-user\",\"NHN_PASSWORD\":\"fake-password\",\"NHN_TENANT_ID\":\"tenant-a\"}}}");
        var credential = reader().read(NhnCinderAddonServiceTest.configured().getBindings().get(0));
        assertThat(credential.tenantId()).isEqualTo("tenant-a"); assertThat(credential.toString()).doesNotContain("fake-password");
        assertThat(http.requests.get(0).uri().getPath()).isEqualTo("/v1/secret/data/csp/nhn");
        assertThat(http.requests.get(0).headers().firstValue("X-Vault-Token")).contains("fake-read-only-token");
    }
    @Test void missingFieldsAndMaskedCredentialsCannotAuthenticateNhn() throws Exception {
        http.respond(200, "{\"data\":{\"data\":{\"NHN_IDENTITY_ENDPOINT\":\"identity\",\"NHN_USERNAME\":\"fake-user\",\"NHN_PASSWORD\":\"Hidden for security.\",\"NHN_TENANT_ID\":\"tenant-a\"}}}");
        var reader = reader();
        assertThatThrownBy(() -> reader.read(NhnCinderAddonServiceTest.configured().getBindings().get(0)))
                .isInstanceOf(StorageOperationException.class).hasMessageNotContaining("fake-user").hasNoCause();
    }
    @Test void vaultDenialDoesNotExposeSecretErrorPayload() throws Exception {
        http.respond(403, "private-secret-from-upstream"); var reader = reader();
        assertThatThrownBy(() -> reader.read(NhnCinderAddonServiceTest.configured().getBindings().get(0))).hasMessageNotContaining("private-secret").hasNoCause();
    }
}
