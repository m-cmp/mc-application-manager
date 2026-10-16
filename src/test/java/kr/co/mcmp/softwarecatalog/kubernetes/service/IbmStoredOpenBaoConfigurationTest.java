package kr.co.mcmp.softwarecatalog.kubernetes.service;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class IbmStoredOpenBaoConfigurationTest {
    @TempDir Path directory;
    final JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1"));
    final IbmIngressAutomationProperties properties = new IbmIngressAutomationProperties();
    final String token = "scoped-ibm-test-token";

    IbmStoredOpenBaoConfiguration store() {
        properties.setCredentialKeyFile(directory.resolve("persistent/key").toString());
        var stored = new IbmStoredOpenBaoConfiguration(jdbc, properties); stored.initialize(); return stored;
    }
    IbmIngressAutomationProperties.Profile profile() {
        var p = new IbmIngressAutomationProperties.Profile();
        p.setNamespace("default"); p.setConnectionName("ibm-jp-tok"); p.setSecretPath("secret/data/csp/ibm"); return p;
    }
    String encrypted() { return jdbc.queryForObject("SELECT token_ciphertext FROM ibm_ingress_openbao_configuration", String.class); }

    @Test void restartedReaderUsesDatabaseAndPersistentKeyWithoutTokenMountOrEnvironment() throws Exception {
        store().save(profile(), "http://reachable-openbao:8200", token);
        byte[] key = Files.readAllBytes(Path.of(properties.getCredentialKeyFile()));
        String ciphertext = encrypted();
        assertThat(ciphertext).startsWith("v1:").doesNotContain(token);
        var http = new NhnTestHttp(); http.respond(200,"{\"data\":{\"data\":{\"IC_API_KEY\":\"ibm-api-key-memory-only\"}}}");
        // New service/reader instances emulate a new AM process using only the retained DB and key.
        var restarted = store();
        assertThat(new IbmOpenBaoCredentials(properties, http.client, restarted).read(profile())).isEqualTo("ibm-api-key-memory-only");
        assertThat(http.requests).singleElement().satisfies(request -> {
            assertThat(request.uri().toString()).isEqualTo("http://reachable-openbao:8200/v1/secret/data/csp/ibm");
            assertThat(request.headers().firstValue("X-Vault-Token")).contains(token);
        });
        assertThat(encrypted()).isEqualTo(ciphertext).doesNotContain("ibm-api-key-memory-only");
        assertThat(Files.readAllBytes(Path.of(properties.getCredentialKeyFile()))).isEqualTo(key).hasSize(32);
        assertThat(restarted.find(profile()).orElseThrow().toString()).doesNotContain(token);
    }

    @Test void credentialsAreNotSharedAcrossNamespacesOrConnections() {
        var stored = store(); stored.save(profile(), "http://openbao:8200", token);
        var other = profile(); other.setNamespace("another-project"); assertThat(stored.find(other)).isEmpty();
        other = profile(); other.setConnectionName("ibm-us-south"); assertThat(stored.find(other)).isEmpty();
    }

    @Test void rotatingTokenRetainsKeyAndOnlyReplacesThatBinding() throws Exception {
        var stored = store(); stored.save(profile(), "http://openbao:8200", token);
        byte[] key = Files.readAllBytes(Path.of(properties.getCredentialKeyFile())); String before = encrypted();
        store().save(profile(), "http://openbao:8200", "replacement-scoped-token");
        assertThat(encrypted()).isNotEqualTo(before).doesNotContain("replacement-scoped-token");
        assertThat(store().find(profile()).orElseThrow().token()).isEqualTo("replacement-scoped-token");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ibm_ingress_openbao_configuration", Integer.class)).isEqualTo(1);
        assertThat(Files.readAllBytes(Path.of(properties.getCredentialKeyFile()))).isEqualTo(key);
    }

    @Test void missingKeyFailsWithoutCreatingANewKeyOrFallingBackToAnotherToken() throws Exception {
        var stored = store(); stored.save(profile(), "http://openbao:8200", token);
        String before = encrypted(); Files.delete(Path.of(properties.getCredentialKeyFile()));
        properties.setOpenBaoTokenFile(directory.resolve("other-token").toString());
        Files.writeString(Path.of(properties.getOpenBaoTokenFile()), "different-token");
        var http = new NhnTestHttp();
        assertThatThrownBy(() -> new IbmOpenBaoCredentials(properties, http.client, store()).read(profile()))
                .hasMessageContaining("registered IBM credential").hasMessageNotContaining(token).hasNoCause();
        assertThat(Files.exists(Path.of(properties.getCredentialKeyFile()))).isFalse();
        assertThat(encrypted()).isEqualTo(before); assertThat(http.requests).isEmpty();
        assertThatThrownBy(() -> store().save(profile(), "http://openbao:8200", "new-token"))
                .hasMessageContaining("encryption key").hasNoCause();
        assertThat(Files.exists(Path.of(properties.getCredentialKeyFile()))).isFalse();
        assertThat(encrypted()).isEqualTo(before);
    }

    @Test void changingEndpointWithoutReencryptingTokenIsRejected() {
        var stored = store(); stored.save(profile(), "http://openbao:8200", token);
        jdbc.update("UPDATE ibm_ingress_openbao_configuration SET open_bao_url='http://another-server:8200'");
        assertThatThrownBy(() -> store().find(profile())).hasMessageContaining("encryption key").hasMessageNotContaining(token).hasNoCause();
    }

    @Test void copyingCiphertextIntoAnotherBindingIsRejected() {
        var stored = store(); stored.save(profile(), "http://openbao:8200", token);
        jdbc.update("UPDATE ibm_ingress_openbao_configuration SET namespace='another-project'");
        var other = profile(); other.setNamespace("another-project");
        assertThatThrownBy(() -> store().find(other)).hasMessageContaining("encryption key").hasNoCause();
    }

    @Test void tamperedCiphertextIsRejectedWithoutHttp() throws Exception {
        var stored = store(); stored.save(profile(), "http://openbao:8200", token);
        String value = encrypted(); String changed = value.substring(0, 8) + (value.charAt(8) == 'A' ? 'B' : 'A') + value.substring(9);
        jdbc.update("UPDATE ibm_ingress_openbao_configuration SET token_ciphertext=?", changed);
        var http = new NhnTestHttp();
        assertThatThrownBy(() -> new IbmOpenBaoCredentials(properties, http.client, stored).read(profile()))
                .hasMessageNotContaining(token).hasNoCause();
        assertThat(http.requests).isEmpty();
    }

    @Test void deniedSavedTokenIsNotBypassedUsingFileFallback() throws Exception {
        var stored = store(); stored.save(profile(), "http://openbao:8200", token);
        var http = new NhnTestHttp(); http.respond(403, "private-upstream-details");
        assertThatThrownBy(() -> new IbmOpenBaoCredentials(properties, http.client, stored).read(profile()))
                .hasMessageNotContaining("private-upstream-details").hasMessageNotContaining(token).hasNoCause();
        assertThat(http.requests).hasSize(1);
    }

    @Test void invalidOperatorBindingOrHeaderIsRejectedBeforeSaving() {
        var stored = store(); var invalid = profile(); invalid.setNamespace("../default");
        assertThatThrownBy(() -> stored.save(invalid, "http://openbao:8200", token)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> stored.save(profile(), "http://openbao:8200", "token\r\nInjected: secret"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ibm_ingress_openbao_configuration", Integer.class)).isZero();
    }
}
