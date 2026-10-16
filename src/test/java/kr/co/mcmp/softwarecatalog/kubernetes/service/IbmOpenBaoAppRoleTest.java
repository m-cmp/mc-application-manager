package kr.co.mcmp.softwarecatalog.kubernetes.service;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class IbmOpenBaoAppRoleTest {
    @TempDir Path directory;
    final JdbcTemplate jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1"));
    final IbmIngressAutomationProperties properties=new IbmIngressAutomationProperties();
    final IbmIngressAutomationProperties.Profile profile=new IbmIngressAutomationProperties.Profile();
    final MutableClock clock=new MutableClock();
    final NhnTestHttp http=new NhnTestHttp();
    final IbmOpenBaoAppRoleSessions sessions=new IbmOpenBaoAppRoleSessions(http.client,clock);
    final String role="private-role-id",secret="private-secret-id";
    IbmOpenBaoAppRoleTest() throws Exception {
        profile.setNamespace("default");profile.setConnectionName("ibm-jp-tok");profile.setSecretPath("secret/data/csp/ibm");
    }
    IbmStoredOpenBaoConfiguration store() {
        properties.setCredentialKeyFile(directory.resolve("persistent/key").toString());
        var stored=new IbmStoredOpenBaoConfiguration(jdbc,properties);stored.initialize();return stored;
    }
    IbmOpenBaoAppRoleSessions.Settings settings() { return new IbmOpenBaoAppRoleSessions.Settings("http://openbao:8200","auth/approle/login",role,secret); }
    IbmOpenBaoCredentials reader() {
        var stored=store();stored.saveAppRole(profile,settings());
        return new IbmOpenBaoCredentials(properties,http.client,stored,sessions);
    }
    void login(String token,int ttl) {http.respond(200,"{\"auth\":{\"client_token\":\""+token+"\",\"lease_duration\":"+ttl+"}}");}
    void key() {http.respond(200,"{\"data\":{\"data\":{\"IC_API_KEY\":\"key-only-in-memory\"}}}");}
    long logins() {return http.requests.stream().filter(r->r.method().equals("POST")).count();}

    @Test void accessTokensAreCachedOnlyInMemoryAndCredentialsRemainEncrypted() {
        var reader=reader();login("short-access-token",30);key();key();
        assertThat(reader.read(profile)).isEqualTo("key-only-in-memory");assertThat(reader.read(profile)).isEqualTo("key-only-in-memory");
        assertThat(logins()).isEqualTo(1);
        String encrypted=jdbc.queryForObject("SELECT credentials_ciphertext FROM ibm_ingress_openbao_approle_configuration",String.class);
        assertThat(encrypted).doesNotContain(role,secret,"short-access-token","key-only-in-memory");
        assertThat(settings().toString()).doesNotContain(role,secret);
        assertThat(NhnTestHttp.body(http.requests.get(0))).contains("role_id",role,"secret_id",secret);
        assertThat(http.requests.get(0).headers().firstValue("X-Vault-Token")).isEmpty();
    }

    @Test void tokenExpiryAutomaticallyLogsInAgainWithoutConfigurationChange() {
        var reader=reader();login("first-access",5);key();assertThat(reader.read(profile)).isEqualTo("key-only-in-memory");
        clock.now=clock.now.plusSeconds(6);login("second-access",5);key();
        assertThat(reader.read(profile)).isEqualTo("key-only-in-memory");assertThat(logins()).isEqualTo(2);
        assertThat(http.requests.get(3).headers().firstValue("X-Vault-Token")).contains("second-access");
    }

    @Test void revokedTokenRetriesWithOneFreshLogin() {
        var reader=reader();login("first-access",3600);key();reader.read(profile);
        http.respond(403,"private-denial");login("replacement-access",3600);key();
        assertThat(reader.read(profile)).isEqualTo("key-only-in-memory");assertThat(logins()).isEqualTo(2);
        assertThat(http.requests.get(4).headers().firstValue("X-Vault-Token")).contains("replacement-access");
    }

    @Test void deniedPolicyDoesNotLoopOrFallBackToOldStaticToken() {
        store().save(profile,"http://openbao:8200","old-static-token");var reader=reader();
        login("first-access",3600);http.respond(403,"private-denial");login("second-access",3600);http.respond(403,"private-denial");
        assertThatThrownBy(()->reader.read(profile)).hasMessageContaining("registered IBM credential").hasMessageNotContaining(secret).hasMessageNotContaining("private-denial").hasNoCause();
        assertThat(http.requests).hasSize(4);assertThat(logins()).isEqualTo(2);
        assertThat(http.requests).noneSatisfy(r->assertThat(r.headers().firstValue("X-Vault-Token")).contains("old-static-token"));
    }

    @Test void deniedAppRoleNeverUsesStaticTokenFallback() {
        store().save(profile,"http://openbao:8200","old-static-token");var reader=reader();http.respond(403,"private-auth-denial");
        assertThatThrownBy(()->reader.read(profile)).hasMessageNotContaining(secret).hasMessageNotContaining("private-auth-denial").hasNoCause();
        assertThat(http.requests).hasSize(1);
    }

    @Test void temporaryServerFailureDoesNotTriggerRepeatedLogins() {
        var reader=reader();login("valid-access",3600);http.respond(503,"private-error");
        assertThatThrownBy(()->reader.read(profile)).hasMessageNotContaining("private-error").hasNoCause();
        key();assertThat(reader.read(profile)).isEqualTo("key-only-in-memory");assertThat(logins()).isEqualTo(1);
    }

    @Test void restartedReaderLogsInFromRetainedEncryptedSourceCredentials() {
        var reader=reader();login("old-process-token",3600);key();reader.read(profile);
        var restarted=new IbmOpenBaoCredentials(properties,http.client,store());login("new-process-token",3600);key();
        assertThat(restarted.read(profile)).isEqualTo("key-only-in-memory");assertThat(logins()).isEqualTo(2);
    }

    @Test void rotatedSourceCredentialsInvalidateCachedSession() {
        var reader=reader();login("old-access",3600);key();reader.read(profile);
        store().saveAppRole(profile,new IbmOpenBaoAppRoleSessions.Settings(settings().url(),settings().authPath(),role,"rotated-secret"));
        login("new-access",3600);key();reader.read(profile);
        assertThat(logins()).isEqualTo(2);assertThat(NhnTestHttp.body(http.requests.get(2))).contains("rotated-secret");
    }

    @Test void missingPersistentKeyCannotBeRecreatedOverAppRoleData() throws Exception {
        reader();Path key=Path.of(properties.getCredentialKeyFile());Files.delete(key);
        assertThatThrownBy(()->store().saveAppRole(profile,settings())).hasMessageContaining("encryption key").hasNoCause();
        assertThat(key).doesNotExist();
    }

    @Test void settingsAreNotSharedAcrossNamespacesAndStaticOverwriteIsRejected() {
        reader();var other=new IbmIngressAutomationProperties.Profile();other.setNamespace("another-project");other.setConnectionName("ibm-jp-tok");
        assertThat(store().findAppRole(other)).isEmpty();
        assertThatThrownBy(()->store().save(profile,"http://openbao:8200","different-token")).hasMessageContaining("configure-approle");
    }

    @Test void concurrentCallersShareOneLogin() throws Exception {
        login("shared-access",3600);var pool=Executors.newFixedThreadPool(8);
        try {
            var futures=pool.invokeAll(java.util.stream.IntStream.range(0,16)
                    .<Callable<String>>mapToObj(i->()->sessions.connection(profile,settings()).token()).toList());
            for(var future:futures)assertThat(future.get()).isEqualTo("shared-access");
            assertThat(logins()).isEqualTo(1);
        } finally {pool.shutdownNow();}
    }

    @Test void invalidLoginResponseAndUntrustedAuthPathAreRejectedWithoutSecretLeak() {
        http.respond(200,"{\"auth\":{\"client_token\":\"access-without-ttl\"}}");
        assertThatThrownBy(()->sessions.connection(profile,settings())).hasMessageNotContaining(role).hasMessageNotContaining(secret).hasMessageNotContaining("access-without-ttl").hasNoCause();
        assertThatThrownBy(()->sessions.connection(profile,new IbmOpenBaoAppRoleSessions.Settings(settings().url(),"https://other-host/login",role,secret)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(http.requests).hasSize(1);
    }

    static final class MutableClock extends Clock {
        Instant now=Instant.parse("2026-10-16T00:00:00Z");
        public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return now;}
    }
}
