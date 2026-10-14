package kr.co.mcmp.softwarecatalog.kubernetes.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.stereotype.Component;

/** Reuses the NHN secret already registered by Tumblebug; no raw credential REST endpoint is needed. */
@Component
public class NhnOpenBaoCredentials {
    record Credentials(String identityEndpoint, String username, String password, String tenantId) {
        @Override public String toString() { return "NHN credentials (withheld)"; }
    }
    private final NhnCinderAddonProperties properties;
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();

    @org.springframework.beans.factory.annotation.Autowired
    public NhnOpenBaoCredentials(NhnCinderAddonProperties properties) {
        this(properties, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build());
    }
    NhnOpenBaoCredentials(NhnCinderAddonProperties properties, HttpClient http) {
        this.properties = properties; this.http = http;
    }

    Credentials read(NhnCinderAddonProperties.Binding binding) {
        try {
            URI base = URI.create(properties.getOpenBaoUrl());
            if (!("https".equals(base.getScheme()) || "http".equals(base.getScheme())) || base.getHost() == null
                    || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null
                    || !(base.getPath().isEmpty() || "/".equals(base.getPath()))) throw new IllegalArgumentException();
            String token = IbmIngressAutomationProperties.readOperatorFile(properties.getOpenBaoTokenFile(), "OpenBao token").trim();
            if (token.isEmpty()) throw new IllegalArgumentException();
            var request = HttpRequest.newBuilder(base.resolve("/v1/" + binding.getSecretPath()))
                    .timeout(Duration.ofSeconds(15)).header("X-Vault-Token", token).header("Accept", "application/json").GET().build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || response.body().length() > 65536) throw new IllegalArgumentException();
            JsonNode data = json.readTree(response.body()).path("data").path("data");
            return new Credentials(required(data, "NHN_IDENTITY_ENDPOINT"), required(data, "NHN_USERNAME"),
                    required(data, "NHN_PASSWORD"), required(data, "NHN_TENANT_ID"));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (Exception e) {
            // OpenBao errors, parser failures and file paths can contain credentials. Keep them out of status/logs.
            throw unavailable();
        }
    }

    private static String required(JsonNode data, String key) {
        String value = data.path(key).isTextual() ? data.path(key).asText() : "";
        if (value.isBlank() || value.equals("Hidden for security.") || value.matches("\\*+")) throw new IllegalArgumentException();
        return value;
    }
    private static StorageOperationException unavailable() {
        return new StorageOperationException(503, "NHN_CREDENTIAL_UNAVAILABLE",
                "AM could not read the registered NHN credential. Check the OpenBao token file, read policy and credential binding.");
    }
}
