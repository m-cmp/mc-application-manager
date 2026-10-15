package kr.co.mcmp.softwarecatalog.kubernetes.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import org.springframework.stereotype.Component;

/** Reads the existing registered IBM key into memory; never persists it or returns it to API clients. */
@Component
public class IbmOpenBaoCredentials {
    private final IbmIngressAutomationProperties properties;
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();

    @org.springframework.beans.factory.annotation.Autowired
    public IbmOpenBaoCredentials(IbmIngressAutomationProperties properties) {
        this(properties, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER).build());
    }
    IbmOpenBaoCredentials(IbmIngressAutomationProperties properties, HttpClient http) {
        this.properties = properties;
        this.http = http;
    }

    String read(IbmIngressAutomationProperties.Profile profile) {
        try {
            String path = profile.getSecretPath();
            if (path == null || !path.matches("secret/data/(?:csp/ibm|users/[a-z0-9][a-z0-9-]{0,63}/csp/ibm)"))
                throw new IllegalArgumentException();
            URI base = URI.create(properties.getOpenBaoUrl());
            if (!("https".equals(base.getScheme()) || "http".equals(base.getScheme())) || base.getHost() == null
                    || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null
                    || !(base.getPath().isEmpty() || "/".equals(base.getPath()))) throw new IllegalArgumentException();
            String token = IbmIngressAutomationProperties.readOperatorFile(properties.getOpenBaoTokenFile(), "OpenBao token").trim();
            if (token.isEmpty()) throw new IllegalArgumentException();
            var request = HttpRequest.newBuilder(base.resolve("/v1/" + path)).timeout(Duration.ofSeconds(15))
                    .header("X-Vault-Token", token).header("Accept", "application/json").GET().build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || response.body().length() > 65536) throw new IllegalArgumentException();
            var field = json.readTree(response.body()).path("data").path("data").path("IC_API_KEY");
            String key = field.isTextual() ? field.asText() : "";
            if (key.isBlank() || key.equals("Hidden for security.") || key.matches("\\*+")) throw new IllegalArgumentException();
            return key;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (Exception e) {
            throw unavailable();
        }
    }

    private static IllegalArgumentException unavailable() {
        return new IllegalArgumentException("AM could not read the registered IBM credential. Check the IBM OpenBao token file, read policy and Project/connection binding.");
    }
}
