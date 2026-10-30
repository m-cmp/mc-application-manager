package kr.co.mcmp.util;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class DeploymentErrorMessagesTest {
    @Test void keepsNestedReasonsWithoutDuplicateMessages() {
        var error = new RuntimeException("Application deployment failed",
                new RuntimeException("Helm install failed", new RuntimeException("connection refused")));
        assertThat(DeploymentErrorMessages.describe(error))
                .isEqualTo("Application deployment failed; caused by: Helm install failed; caused by: connection refused");
        assertThat(DeploymentErrorMessages.describe(new RuntimeException("same", new RuntimeException("same"))))
                .isEqualTo("same");
    }

    @Test void handlesMissingMessagesAndCyclicCauses() {
        assertThat(DeploymentErrorMessages.describe(null)).contains("No additional");
        var one = new RuntimeException((String) null);
        var two = new RuntimeException("SSH handshake failed", one);
        one.initCause(two);
        assertThat(DeploymentErrorMessages.describe(one)).isEqualTo("SSH handshake failed");
    }

    @Test void removesCredentialsButRetainsEndpointAndUsefulReason() {
        String message = "GET https://user:secret@example.test:6443/api?token=abc&namespace=default "
                + "Authorization: Bearer eyJhbGci.secret.value password=hello "
                + "{\"client-secret\":\"value with spaces\",\"client-key-data\":\"BASE64KEY\"} "
                + "failed: connection refused";
        assertThat(DeploymentErrorMessages.sanitize(message))
                .contains("example.test:6443/api", "namespace=default", "connection refused")
                .doesNotContain("user:secret", "token=abc", "eyJhbGci", "hello", "value with spaces", "BASE64KEY");
        assertThat(DeploymentErrorMessages.sanitize("Authorization: Basic YWRtaW46c2VjcmV0"))
                .doesNotContain("YWRtaW46c2VjcmV0");
    }

    @Test void removesPemAndMultilineYamlSecretsBeforeTruncation() {
        String message = "permission denied\nclient-key-data: |\n  BASE64SECRET\n  MORESECRET\n"
                + "server: https://cluster.test\n-----BEGIN OPENSSH PRIVATE KEY-----\nPRIVATESECRET\n"
                + "-----END OPENSSH PRIVATE KEY-----\nfailed";
        assertThat(DeploymentErrorMessages.sanitize(message))
                .contains("permission denied", "cluster.test", "failed")
                .doesNotContain("BASE64SECRET", "MORESECRET", "PRIVATESECRET");
        assertThat(DeploymentErrorMessages.sanitize("-----BEGIN PRIVATE KEY-----\nUNTERMINATEDSECRET"))
                .doesNotContain("UNTERMINATEDSECRET");
    }

    @Test void redactsCredentialAliasesRawAtSignsAndUnterminatedValues() {
        assertThat(DeploymentErrorMessages.sanitize("registry credentials=TOPSECRET api-token=APISECRET authToken=AUTHSECRET: denied"))
                .contains("denied").doesNotContain("TOPSECRET", "APISECRET", "AUTHSECRET");
        assertThat(DeploymentErrorMessages.sanitize("GET https://user:p@ss@registry.test/v2: denied"))
                .contains("registry.test/v2", "denied").doesNotContain("p@ss", "@ss@");
        assertThat(DeploymentErrorMessages.sanitize("password=\"TRUNCATEDSECRET"))
                .doesNotContain("TRUNCATEDSECRET");
        assertThat(DeploymentErrorMessages.sanitize("rootPassword=ROOTSECRET userPasswd=USERSECRET privateKeyData=KEYSECRET DB_PASSWORD=DBSECRET SecretId=IDSECRET: connection refused"))
                .contains("connection refused").doesNotContain("ROOTSECRET", "USERSECRET", "KEYSECRET", "DBSECRET", "IDSECRET");
        assertThat(DeploymentErrorMessages.sanitize("jdbc:postgresql://user:DBSECRET@db.test/app"))
                .contains("db.test/app").doesNotContain("user:DBSECRET");
    }

    @Test void boundsLongMessagesAndCauseChains() {
        String longMessage = "x".repeat(10000) + " token=SECRET";
        assertThat(DeploymentErrorMessages.sanitize(longMessage)).hasSize(3072).doesNotContain("SECRET");
        Throwable cause = new RuntimeException("root");
        for (int index = 0; index < 30; index++) cause = new RuntimeException(index + longMessage, cause);
        assertThat(DeploymentErrorMessages.describe(cause)).hasSizeLessThanOrEqualTo(3072).doesNotContain("SECRET");
    }
}
