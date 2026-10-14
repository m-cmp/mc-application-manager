package kr.co.mcmp.softwarecatalog.application.service;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DeploymentFailureExceptionTest {
    @Test void legacyApiReturnsFailureStatusAndHistoryWithoutProviderSecrets() {
        var response = new kr.co.mcmp.exception.GlobalExceptionHandler().handleDeploymentFailure(
                new DeploymentFailureException(42L, new RuntimeException("password=secret")));
        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().getData()).containsEntry("deploymentId",42L);
        assertThat(response.getBody().getDetail()).doesNotContain("secret");
    }
    @Test void classifiesKnownFailuresWithoutLeakingProviderText() {
        for (String text : new String[]{"password=secret", "Bearer secret", "-----BEGIN PRIVATE KEY-----",
                "timeout password=secret", "Forbidden token=secret", "ImagePullBackOff credentials=secret"}) {
            assertThat(DeploymentFailureException.publicReason(new RuntimeException(text))).doesNotContain("secret", "PRIVATE KEY");
        }
        assertThat(DeploymentFailureException.publicReason(new RuntimeException("outer",
                new IllegalArgumentException("Enable Ingress to configure external CIDR access."))))
                .isEqualTo("Enable Ingress to configure external CIDR access.");
        assertThat(DeploymentFailureException.publicReason(new RuntimeException("timed out")))
                .contains("timed out");
        assertThat(new DeploymentFailureException(null, new RuntimeException("unknown")).getDeploymentId()).isNull();
    }
}
