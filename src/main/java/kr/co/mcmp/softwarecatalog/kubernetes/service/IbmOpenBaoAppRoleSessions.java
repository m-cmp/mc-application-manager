package kr.co.mcmp.softwarecatalog.kubernetes.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Access tokens stay in memory. Expiry/denial triggers AppRole login, never a privileged fallback. */
final class IbmOpenBaoAppRoleSessions {
    record Settings(String url, String authPath, String roleId, String secretId) {
        @Override public String toString() { return "IBM OpenBao AppRole settings (credentials withheld)"; }
    }
    private record Scope(String namespace, String connection) {}
    private record Session(Settings settings, String token, Instant expires) {
        @Override public String toString() { return "IBM OpenBao AppRole session (credentials withheld)"; }
    }
    private final ConcurrentHashMap<Scope, Session> sessions = new ConcurrentHashMap<>();
    private final HttpClient http;
    private final Clock clock;
    private final ObjectMapper json = new ObjectMapper();

    IbmOpenBaoAppRoleSessions(HttpClient http) { this(http, Clock.systemUTC()); }
    IbmOpenBaoAppRoleSessions(HttpClient http, Clock clock) { this.http = http; this.clock = clock; }

    IbmStoredOpenBaoConfiguration.Connection connection(IbmIngressAutomationProperties.Profile profile, Settings settings) {
        validate(settings);
        var scope = new Scope(profile.getNamespace(), profile.getConnectionName());
        Session session = sessions.compute(scope, (key, existing) -> {
            if (existing != null && existing.settings.equals(settings) && clock.instant().isBefore(existing.expires)) return existing;
            return login(settings);
        });
        return new IbmStoredOpenBaoConfiguration.Connection(settings.url(), session.token);
    }

    void invalidate(IbmIngressAutomationProperties.Profile profile, Settings settings, IbmStoredOpenBaoConfiguration.Connection denied) {
        sessions.computeIfPresent(new Scope(profile.getNamespace(), profile.getConnectionName()), (key, existing) ->
                existing.settings.equals(settings) && existing.token.equals(denied.token()) ? null : existing);
    }

    static void validate(Settings settings) {
        IbmOpenBaoCredentials.validateConnection(new IbmStoredOpenBaoConfiguration.Connection(settings.url(), "validation-placeholder"));
        if (settings.authPath() == null || !settings.authPath().matches("auth/[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}/login")
                || settings.roleId() == null || !settings.roleId().matches("[a-zA-Z0-9._-]{1,1024}")
                || settings.secretId() == null || !settings.secretId().matches("[a-zA-Z0-9._-]{1,1024}"))
            throw unavailable();
    }

    private Session login(Settings settings) {
        try {
            String body = json.writeValueAsString(Map.of("role_id", settings.roleId(), "secret_id", settings.secretId()));
            var request = HttpRequest.newBuilder(URI.create(settings.url()).resolve("/v1/" + settings.authPath()))
                    .timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json").header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || response.body().length() > 65536) throw unavailable();
            var auth = json.readTree(response.body()).path("auth");
            String token = auth.path("client_token").asText(); long ttl = auth.path("lease_duration").asLong();
            IbmOpenBaoCredentials.validateConnection(new IbmStoredOpenBaoConfiguration.Connection(settings.url(), token));
            if (ttl <= 0) throw unavailable();
            long cacheSeconds = Math.min(ttl, 86400);
            cacheSeconds -= Math.min(5, cacheSeconds / 10);
            return new Session(settings, token, clock.instant().plusSeconds(cacheSeconds));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); throw unavailable();
        } catch (Exception ex) { throw unavailable(); }
    }

    private static IllegalArgumentException unavailable() {
        return new IllegalArgumentException("IBM OpenBao AppRole authentication failed. Check the registered role, SecretID and IBM read policy.");
    }
}
