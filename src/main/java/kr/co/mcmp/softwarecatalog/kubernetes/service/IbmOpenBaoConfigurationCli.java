package kr.co.mcmp.softwarecatalog.kubernetes.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Operator-only command inside the AM image. No web endpoint, Spring server, or scheduler is started. */
public final class IbmOpenBaoConfigurationCli {
    record Input(String namespace, String connectionName, String openBaoUrl, String token, String secretPath,
                 String roleId, String secretId, String authPath) {
        @Override public String toString() { return "IBM OpenBao operator input (credentials withheld)"; }
    }

    public static void main(String[] args) {
        try {
            if (args.length != 1 || !java.util.Set.of("configure","configure-approle","verify").contains(args[0])) throw new IllegalArgumentException();
            byte[] input = System.in.readNBytes(65537);
            if (input.length > 65536) throw new IllegalArgumentException();
            Input values = new ObjectMapper().readValue(input, Input.class);
            var profile = new IbmIngressAutomationProperties.Profile();
            profile.setNamespace(values.namespace()); profile.setConnectionName(values.connectionName());
            profile.setSecretPath(values.secretPath() == null ? "secret/data/csp/ibm" : values.secretPath());
            IbmStoredOpenBaoConfiguration.requireBinding(profile);
            var properties = new IbmIngressAutomationProperties();
            properties.setCredentialKeyFile(System.getenv().getOrDefault("IBM_INGRESS_CREDENTIAL_KEY_FILE", IbmOpenBaoTokenCipher.DEFAULT_KEY_FILE));
            var source = new DriverManagerDataSource(requiredEnv("DB_URL"), requiredEnv("DB_USER"), requiredEnv("DB_PASS"));
            var stored = new IbmStoredOpenBaoConfiguration(new JdbcTemplate(source), properties);
            stored.initialize();
            var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
            if (args[0].equals("configure")) {
                var connection = new IbmStoredOpenBaoConfiguration.Connection(values.openBaoUrl(), values.token());
                // Validate the registered IBM read before replacing an existing usable configuration.
                IbmOpenBaoCredentials.readKey(http, connection, profile.getSecretPath());
                stored.save(profile, connection.url(), connection.token());
                System.out.println("IBM_OPENBAO_CONFIGURATION_SAVED: encrypted token stored in AM database; persistent encryption key retained");
            } else if (args[0].equals("configure-approle")) {
                var settings = new IbmOpenBaoAppRoleSessions.Settings(values.openBaoUrl(),
                        values.authPath()==null ? "auth/approle/login" : values.authPath(),values.roleId(),values.secretId());
                var sessions = new IbmOpenBaoAppRoleSessions(http);
                IbmOpenBaoCredentials.readKey(http,sessions.connection(profile,settings),profile.getSecretPath());
                stored.saveAppRole(profile,settings);
                System.out.println("IBM_OPENBAO_APPROLE_SAVED: source credentials encrypted in AM database; access tokens stay in memory and are replaced automatically");
            } else {
                new IbmOpenBaoCredentials(properties, http, stored).read(profile);
                System.out.println("IBM_OPENBAO_CONFIGURATION_VERIFIED: stored connection decrypted and registered IBM credential read successfully");
            }
        } catch (Exception ex) {
            System.err.println("IBM OpenBao configuration failed. Check the selected binding, reachable OpenBao address, IBM read policy, AM database and persistent encryption key. Credential values are withheld.");
            System.exit(1);
        }
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException();
        return value;
    }
}
