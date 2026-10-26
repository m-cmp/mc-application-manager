package kr.co.mcmp.softwarecatalog.application.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import kr.co.mcmp.oss.dto.OssDto;
import kr.co.mcmp.softwarecatalog.application.config.NexusConfig;
import kr.co.mcmp.softwarecatalog.application.service.NexusIntegrationService;

class NexusImageAccessTest {
    private final NexusIntegrationService nexus = mock(NexusIntegrationService.class);
    private final NexusConfig config = new NexusConfig();
    private final ObjectMapper mapper = new ObjectMapper();
    private final NexusImageAccess access = new NexusImageAccess(nexus, config, mapper);

    @BeforeEach void setup() {
        config.setRegistryEndpoint("http://internal-nexus:5500");
        config.setDockerPort(5500);
        when(nexus.getNexusInfoFromDB()).thenReturn(OssDto.builder().ossUrl("http://internal-nexus:8081")
                .ossUsername("registry-reader")
                .ossPassword(Base64.getEncoder().encodeToString("test-password".getBytes(StandardCharsets.UTF_8))).build());
    }

    @Test void recordsTheVmReachableEndpointWhileKeepingTheActualImagePathAndTag() {
        config.setDeploymentRegistryEndpoint("https://registry.example.test");
        assertThat(access.deploymentReference("internal-nexus:5500/team/nginx:ReleaseA"))
                .isEqualTo("registry.example.test:443/team/nginx:ReleaseA");
    }

    @Test void neverAcceptsAnImageThatWasPushedToAnotherRegistry() {
        assertThatThrownBy(() -> access.deploymentReference("other.test:5500/nginx:v1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void onlyPreparesAuthenticationForTheConfiguredDeploymentRegistry() throws Exception {
        config.setDeploymentRegistryEndpoint("https://registry.example.test");
        var auth = mapper.readTree(access.dockerAuthConfig("registry.example.test:443/nginx:v1"));
        assertThat(auth.path("auths").has("registry.example.test:443")).isTrue();
        assertThatThrownBy(() -> access.dockerAuthConfig("other.test:443/nginx:v1"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("another Registry");
    }
}
