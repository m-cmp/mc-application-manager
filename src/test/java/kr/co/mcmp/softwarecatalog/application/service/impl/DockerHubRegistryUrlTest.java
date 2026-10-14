package kr.co.mcmp.softwarecatalog.application.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import kr.co.mcmp.oss.dto.OssDto;
import kr.co.mcmp.softwarecatalog.application.service.NexusIntegrationService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class DockerHubRegistryUrlTest {
    @Test void importedPackageReceivesTheActualPushedImageUrlAndKeepsLegacyResponseKeys() {
        var nexus = mock(NexusIntegrationService.class);
        when(nexus.getNexusInfoFromDB()).thenReturn(OssDto.builder().ossUrl("http://internal-nexus:8081").build());
        when(nexus.pushImageAndRegisterCatalog(any())).thenReturn(Map.of("success", true));
        when(nexus.getRepositoryNameByFormat("docker")).thenReturn("docker-hosted");
        var service = new DockerHubIntegrationServiceImpl(nexus, new ObjectMapper());
        ReflectionTestUtils.setField(service, "dockerRegistryUrl", "https://registry.example:5443");
        var result = service.pushImageToNexus("library/ghost", "bookworm");
        assertThat(result.get("success")).isEqualTo(true);
        assertThat(result.get("fullImageUrl")).isEqualTo("registry.example:5443/library/ghost:bookworm");
        assertThat(result.get("fullImageName")).isEqualTo(result.get("fullImageUrl"));
        assertThat(result.get("nexusRegistry")).isEqualTo(result.get("registryUrl"));
        assertThat(result.get("repository")).isEqualTo("docker-hosted");
        verify(nexus).pushImageAndRegisterCatalog(any());
    }
    @Test void invalidOverrideFailsBeforeAttemptingImageTransfer() {
        var nexus = mock(NexusIntegrationService.class);
        when(nexus.getNexusInfoFromDB()).thenReturn(OssDto.builder().ossUrl("http://internal-nexus:8081").build());
        var service = new DockerHubIntegrationServiceImpl(nexus, new ObjectMapper());
        ReflectionTestUtils.setField(service, "dockerRegistryUrl", "http://user:password@registry:5500/repository");
        assertThat(service.pushImageToNexus("ghost", "bookworm").get("success")).isEqualTo(false);
        verify(nexus, never()).pushImageAndRegisterCatalog(any());
    }
}
