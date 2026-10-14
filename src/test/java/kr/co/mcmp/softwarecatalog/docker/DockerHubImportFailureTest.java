package kr.co.mcmp.softwarecatalog.docker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import java.util.Map;
import kr.co.mcmp.softwarecatalog.CatalogService;
import kr.co.mcmp.softwarecatalog.application.dto.DockerHubImageRegistrationRequest;
import kr.co.mcmp.softwarecatalog.application.repository.PackageInfoRepository;
import kr.co.mcmp.softwarecatalog.application.service.DockerHubIntegrationService;
import kr.co.mcmp.softwarecatalog.application.service.NexusIntegrationService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DockerHubImportFailureTest {
    @ParameterizedTest @ValueSource(strings = {"Engine unavailable", "Registry not configured", "Authentication denied"})
    void failedPushDoesNotCreateMetadataOrReportSuccess(String failure) {
        var packages = mock(PackageInfoRepository.class);
        var hub = mock(DockerHubIntegrationService.class);
        var service = new DockerHubService(mock(CatalogService.class), packages, mock(NexusIntegrationService.class), hub);
        var request = new DockerHubImageRegistrationRequest();
        request.setId("image-id"); request.setName("hello-world"); request.setTag("latest");
        when(hub.getImageInfo("image-id", "latest")).thenReturn(Map.of("success", true));
        when(hub.pushImageToNexus("hello-world", "latest")).thenReturn(Map.of("success", false, "message", failure));
        var result = service.registerDockerHubImage(request, "test");
        assertThat(result.get("success")).isEqualTo(false);
        assertThat(result.get("message").toString()).contains(failure);
        verifyNoInteractions(packages);
    }
}
