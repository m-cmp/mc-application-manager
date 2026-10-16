package kr.co.mcmp.softwarecatalog.kubernetes.service;

import jakarta.annotation.PostConstruct;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Project/connection-scoped OpenBao configuration. Only encrypted tokens are written to AM's DB. */
@Component
@org.springframework.context.annotation.Lazy
public class IbmStoredOpenBaoConfiguration {
    record Connection(String url, String token) {
        @Override public String toString() { return "IBM OpenBao connection (credentials withheld)"; }
    }
    private final JdbcTemplate jdbc;
    private final IbmOpenBaoTokenCipher cipher;
    private final ObjectMapper json = new ObjectMapper();

    public IbmStoredOpenBaoConfiguration(JdbcTemplate jdbc, IbmIngressAutomationProperties properties) {
        this.jdbc = jdbc;
        cipher = new IbmOpenBaoTokenCipher(properties.getCredentialKeyFile());
    }

    @PostConstruct
    void initialize() {
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS ibm_ingress_openbao_configuration (
                namespace VARCHAR(63) NOT NULL, connection_name VARCHAR(255) NOT NULL,
                open_bao_url VARCHAR(2048) NOT NULL, token_ciphertext TEXT NOT NULL,
                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (namespace, connection_name))
            """);
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS ibm_ingress_openbao_approle_configuration (
                namespace VARCHAR(63) NOT NULL, connection_name VARCHAR(255) NOT NULL,
                open_bao_url VARCHAR(2048) NOT NULL, credentials_ciphertext TEXT NOT NULL,
                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (namespace, connection_name))
            """);
    }

    Optional<IbmOpenBaoAppRoleSessions.Settings> findAppRole(IbmIngressAutomationProperties.Profile profile) {
        if (profile.getNamespace() == null || profile.getConnectionName() == null) return Optional.empty();
        return jdbc.query("SELECT open_bao_url,credentials_ciphertext FROM ibm_ingress_openbao_approle_configuration WHERE namespace=? AND connection_name=?",
                (rs, n) -> {
                    String url = rs.getString(1);
                    try {
                        var value = json.readTree(cipher.decrypt(rs.getString(2), binding(profile,url) + "\u0000APPROLE"));
                        var settings = new IbmOpenBaoAppRoleSessions.Settings(url, value.path("authPath").asText(),
                                value.path("roleId").asText(), value.path("secretId").asText());
                        IbmOpenBaoAppRoleSessions.validate(settings); return settings;
                    } catch (Exception ex) { throw new IllegalArgumentException("Stored IBM AppRole configuration cannot be decrypted or validated."); }
                }, profile.getNamespace(), profile.getConnectionName()).stream().findFirst();
    }

    void saveAppRole(IbmIngressAutomationProperties.Profile profile, IbmOpenBaoAppRoleSessions.Settings settings) {
        requireBinding(profile); IbmOpenBaoAppRoleSessions.validate(settings); verifyExistingKey();
        final String encrypted;
        try {
            String value = json.writeValueAsString(java.util.Map.of("authPath",settings.authPath(),"roleId",settings.roleId(),"secretId",settings.secretId()));
            encrypted = cipher.encrypt(value, binding(profile,settings.url()) + "\u0000APPROLE");
        } catch (Exception ex) { throw new IllegalArgumentException("IBM AppRole credentials could not be encrypted."); }
        if (updateAppRole(profile,settings.url(),encrypted)==0) {
            try {
                jdbc.update("INSERT INTO ibm_ingress_openbao_approle_configuration(namespace,connection_name,open_bao_url,credentials_ciphertext) VALUES(?,?,?,?)",
                        profile.getNamespace(),profile.getConnectionName(),settings.url(),encrypted);
            } catch (DuplicateKeyException ex) { updateAppRole(profile,settings.url(),encrypted); }
        }
    }

    private int updateAppRole(IbmIngressAutomationProperties.Profile profile,String url,String encrypted) {
        return jdbc.update("UPDATE ibm_ingress_openbao_approle_configuration SET open_bao_url=?,credentials_ciphertext=?,updated_at=CURRENT_TIMESTAMP WHERE namespace=? AND connection_name=?",
                url,encrypted,profile.getNamespace(),profile.getConnectionName());
    }

    Optional<Connection> find(IbmIngressAutomationProperties.Profile profile) {
        if (profile.getNamespace() == null || profile.getConnectionName() == null) return Optional.empty();
        return jdbc.query("SELECT open_bao_url, token_ciphertext FROM ibm_ingress_openbao_configuration WHERE namespace=? AND connection_name=?",
                (rs, n) -> {
                    String url = rs.getString(1);
                    return new Connection(url, cipher.decrypt(rs.getString(2), binding(profile, url)));
                }, profile.getNamespace(), profile.getConnectionName()).stream().findFirst();
    }

    void save(IbmIngressAutomationProperties.Profile profile, String url, String token) {
        requireBinding(profile);
        if (findAppRole(profile).isPresent()) throw new IllegalArgumentException("This binding uses AppRole; update it with configure-approle.");
        IbmOpenBaoCredentials.validateConnection(new Connection(url, token));
        verifyExistingKey();
        String encrypted = cipher.encrypt(token, binding(profile, url));
        if (update(profile, url, encrypted) == 0) {
            try {
                jdbc.update("INSERT INTO ibm_ingress_openbao_configuration(namespace,connection_name,open_bao_url,token_ciphertext) VALUES(?,?,?,?)",
                        profile.getNamespace(), profile.getConnectionName(), url, encrypted);
            } catch (DuplicateKeyException ex) { update(profile, url, encrypted); }
        }
    }

    private void verifyExistingKey() {
        // Never create a replacement key over existing encrypted credentials, including another auth mode.
        jdbc.query("SELECT namespace,connection_name,open_bao_url,token_ciphertext FROM ibm_ingress_openbao_configuration LIMIT 1",
                (rs,n) -> cipher.decrypt(rs.getString(4),rs.getString(1)+"\u0000"+rs.getString(2)+"\u0000"+rs.getString(3)));
        jdbc.query("SELECT namespace,connection_name,open_bao_url,credentials_ciphertext FROM ibm_ingress_openbao_approle_configuration LIMIT 1",
                (rs,n) -> cipher.decrypt(rs.getString(4),rs.getString(1)+"\u0000"+rs.getString(2)+"\u0000"+rs.getString(3)+"\u0000APPROLE"));
    }

    private int update(IbmIngressAutomationProperties.Profile profile, String url, String encrypted) {
        return jdbc.update("UPDATE ibm_ingress_openbao_configuration SET open_bao_url=?,token_ciphertext=?,updated_at=CURRENT_TIMESTAMP WHERE namespace=? AND connection_name=?",
                url, encrypted, profile.getNamespace(), profile.getConnectionName());
    }

    static void requireBinding(IbmIngressAutomationProperties.Profile profile) {
        if (profile.getNamespace() == null || !profile.getNamespace().matches("[a-z0-9][a-z0-9-]{0,62}")
                || profile.getConnectionName() == null || !profile.getConnectionName().matches("[a-z0-9][a-z0-9-]{0,254}"))
            throw new IllegalArgumentException("A valid IBM namespace and connection name are required.");
    }

    private static String binding(IbmIngressAutomationProperties.Profile profile, String url) {
        return profile.getNamespace() + "\u0000" + profile.getConnectionName() + "\u0000" + url;
    }
}
