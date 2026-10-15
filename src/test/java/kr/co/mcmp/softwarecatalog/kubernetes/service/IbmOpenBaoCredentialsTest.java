package kr.co.mcmp.softwarecatalog.kubernetes.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.file.*;
import java.util.*;
import java.net.URI;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IbmOpenBaoCredentialsTest {
    @TempDir Path directory;
    final NhnTestHttp http = new NhnTestHttp();
    final IbmIngressAutomationProperties properties = new IbmIngressAutomationProperties();
    final IbmIngressAutomationProperties.Profile profile = new IbmIngressAutomationProperties.Profile();

    IbmOpenBaoCredentialsTest() throws Exception { }
    IbmOpenBaoCredentials reader() throws Exception {
        properties.setOpenBaoUrl("http://openbao:8200");
        Path token=directory.resolve("token"); Files.writeString(token,"scoped-ibm-read-token\n");
        properties.setOpenBaoTokenFile(token.toString());
        profile.setSecretPath("secret/data/csp/ibm"); profile.setRegion("jp-tok");
        return new IbmOpenBaoCredentials(properties,http.client);
    }

    @Test void readsRegisteredIbmKeyAndAuthenticatesWithoutAnApiKeyFile() throws Exception {
        var reader=reader();
        http.respond(200,"{\"data\":{\"data\":{\"IC_API_KEY\":\"test-ibm-api-key\"}}}");
        var api=spy(new IbmCloudIngressApi(java.net.http.HttpClient.newHttpClient(),reader));
        doReturn(new ObjectMapper().readTree("{\"access_token\":\"iam-access-token\"}"))
                .when(api).request(eq("POST"),eq(URI.create("https://iam.cloud.ibm.com/identity/token")),isNull(),anyString(),anyString());
        var session=api.login(profile);
        assertThat(session.toString()).doesNotContain("iam-access-token","test-ibm-api-key");
        assertThat(http.requests).singleElement().satisfies(request -> {
            assertThat(request.uri().toString()).isEqualTo("http://openbao:8200/v1/secret/data/csp/ibm");
            assertThat(request.headers().firstValue("X-Vault-Token")).contains("scoped-ibm-read-token");
            assertThat(request.method()).isEqualTo("GET");
        });
        verify(api).request(eq("POST"),any(),isNull(),contains("apikey=test-ibm-api-key"),any());
        try (var files = Files.list(directory)) {
            assertThat(files.map(p->p.getFileName().toString()).toList()).containsExactly("token");
        }
    }

    @ParameterizedTest @ValueSource(strings={"secret/data/csp/nhn","secret/data/csp/ibm/../aws","secret/data/csp/ibm?list=true","../sys/mounts"})
    void rejectsUnrelatedOrUntrustedSecretPathsBeforeHttp(String path) throws Exception {
        var reader=reader();profile.setSecretPath(path);
        assertThatThrownBy(()->reader.read(profile)).hasMessageContaining("registered IBM credential").hasNoCause();
        assertThat(http.requests).isEmpty();
    }
    @ParameterizedTest @ValueSource(strings={"{}","{\"data\":{\"data\":{\"IC_API_KEY\":\"Hidden for security.\"}}}","{\"data\":{\"data\":{\"IC_API_KEY\":\"*****\"}}}","private-upstream-body"})
    void missingMaskedOrMalformedCredentialsDoNotLeakResponse(String body) throws Exception {
        var reader=reader();http.respond(200,body);
        assertThatThrownBy(()->reader.read(profile)).hasMessageContaining("registered IBM credential")
                .hasMessageNotContaining(body).hasNoCause();
    }
    @Test void deniedTokenDoesNotLeakCredentialsOrUpstreamBody() throws Exception {
        var reader=reader();http.respond(403,"private-upstream-body");
        assertThatThrownBy(()->reader.read(profile)).hasMessageNotContaining("private-upstream-body")
                .hasMessageNotContaining("scoped-ibm-read-token").hasNoCause();
    }
}
