package kr.co.mcmp.softwarecatalog.kubernetes.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import org.springframework.stereotype.Component;

/** Reads the existing registered IBM key into memory; never persists it or returns it to API clients. */
@Component
@org.springframework.context.annotation.Lazy
public class IbmOpenBaoCredentials {
    private final IbmIngressAutomationProperties properties;
    private final HttpClient http;
    private final IbmStoredOpenBaoConfiguration stored;
    private final IbmOpenBaoAppRoleSessions sessions;

    @org.springframework.beans.factory.annotation.Autowired
    public IbmOpenBaoCredentials(IbmIngressAutomationProperties properties, IbmStoredOpenBaoConfiguration stored) {
        this(properties, defaultHttp(), stored);
    }
    public IbmOpenBaoCredentials(IbmIngressAutomationProperties properties) {
        this(properties, defaultHttp(), null);
    }
    IbmOpenBaoCredentials(IbmIngressAutomationProperties properties, HttpClient http) {
        this(properties, http, null);
    }
    IbmOpenBaoCredentials(IbmIngressAutomationProperties properties, HttpClient http, IbmStoredOpenBaoConfiguration stored) {
        this(properties,http,stored,new IbmOpenBaoAppRoleSessions(http));
    }
    IbmOpenBaoCredentials(IbmIngressAutomationProperties properties, HttpClient http, IbmStoredOpenBaoConfiguration stored, IbmOpenBaoAppRoleSessions sessions) {
        this.properties = properties; this.http = http; this.stored = stored; this.sessions = sessions;
    }
    private static HttpClient defaultHttp() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    String read(IbmIngressAutomationProperties.Profile profile) {
        try {
            validatePath(profile.getSecretPath());
            var appRole = stored == null ? java.util.Optional.<IbmOpenBaoAppRoleSessions.Settings>empty() : stored.findAppRole(profile);
            if (appRole.isPresent()) {
                var settings = appRole.get();
                var connection = sessions.connection(profile,settings);
                try { return readKey(http,connection,profile.getSecretPath()); }
                catch (ReadDenied ex) {
                    sessions.invalidate(profile,settings,connection);
                    // One fresh login for a revoked/expired token. No retry for timeouts, 5xx, or bad secrets.
                    return readKey(http,sessions.connection(profile,settings),profile.getSecretPath());
                }
            }
            // A saved exact binding takes priority. Do not silently bypass a revoked token or a missing key.
            var saved = stored == null ? java.util.Optional.<IbmStoredOpenBaoConfiguration.Connection>empty() : stored.find(profile);
            var connection = saved.orElseGet(() -> new IbmStoredOpenBaoConfiguration.Connection(properties.getOpenBaoUrl(),
                    IbmIngressAutomationProperties.readOperatorFile(properties.getOpenBaoTokenFile(), "OpenBao token").trim()));
            return readKey(http, connection, profile.getSecretPath());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); throw unavailable();
        } catch (Exception e) { throw unavailable(); }
    }

    static void validateConnection(IbmStoredOpenBaoConfiguration.Connection connection) {
        URI base = URI.create(connection.url());
        if (!("https".equals(base.getScheme()) || "http".equals(base.getScheme())) || base.getHost() == null
                || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null
                || !(base.getPath().isEmpty() || "/".equals(base.getPath())) || connection.url().length() > 2048
                || connection.token() == null || connection.token().isEmpty() || connection.token().length() > 8192
                || !connection.token().matches("[a-zA-Z0-9._-]+")) throw new IllegalArgumentException();
    }

    private static void validatePath(String path) {
        if (path == null || !path.matches("secret/data/(?:csp/ibm|users/[a-z0-9][a-z0-9-]{0,63}/csp/ibm)"))
            throw new IllegalArgumentException();
    }

    static String readKey(HttpClient http, IbmStoredOpenBaoConfiguration.Connection connection, String path) throws Exception {
        validatePath(path); validateConnection(connection);
        var request = HttpRequest.newBuilder(URI.create(connection.url()).resolve("/v1/" + path)).timeout(Duration.ofSeconds(15))
                .header("X-Vault-Token", connection.token()).header("Accept", "application/json").GET().build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode()==401 || response.statusCode()==403) throw new ReadDenied();
        if (response.statusCode() != 200 || response.body().length() > 65536) throw new IllegalArgumentException();
        var field = new ObjectMapper().readTree(response.body()).path("data").path("data").path("IC_API_KEY");
        String key = field.isTextual() ? field.asText() : "";
        if (key.isBlank() || key.equals("Hidden for security.") || key.matches("\\*+")) throw new IllegalArgumentException();
        return key;
    }

    private static final class ReadDenied extends IllegalArgumentException { }

    private static IllegalArgumentException unavailable() {
        return new IllegalArgumentException("AM could not read the registered IBM credential. Configure the AM stored OpenBao connection or check the token file, read policy and Project/connection binding.");
    }
}
