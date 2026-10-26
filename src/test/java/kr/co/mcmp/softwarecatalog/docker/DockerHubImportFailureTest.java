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
    @org.junit.jupiter.api.Test
    void aVerifiedCopyReturnsTheExactPackageIdAndDoesNotRequireOptionalSearchMetadata() {
        var packages = mock(PackageInfoRepository.class);
        var hub = mock(DockerHubIntegrationService.class);
        var access = mock(kr.co.mcmp.softwarecatalog.application.service.impl.NexusImageAccess.class);
        var service = new DockerHubService(mock(CatalogService.class), packages, mock(NexusIntegrationService.class), hub, access);
        var request = new DockerHubImageRegistrationRequest();
        request.setId("library/busybox"); request.setName("busybox"); request.setTag("1.37.0");
        when(hub.getImageInfo("library/busybox", "1.37.0")).thenReturn(Map.of("success", true));
        when(hub.pushImageToNexus("busybox", "1.37.0")).thenReturn(Map.of("success", true,
                "fullImageUrl", "nexus:5000/busybox:1.37.0"));
        when(access.deploymentReference("nexus:5000/busybox:1.37.0")).thenReturn("registry.test:5500/busybox:1.37.0");
        when(packages.save(any())).thenAnswer(invocation -> {
            var entity = invocation.getArgument(0, kr.co.mcmp.softwarecatalog.application.model.PackageInfo.class);
            entity.setId(42L); return entity;
        });
        var result = service.registerDockerHubImage(request, "test");
        assertThat(result.get("success")).isEqualTo(true);
        assertThat(result.get("packageInfoId")).isEqualTo(42L);
        var entity = org.mockito.ArgumentCaptor.forClass(kr.co.mcmp.softwarecatalog.application.model.PackageInfo.class);
        verify(packages).save(entity.capture());
        assertThat(entity.getValue().getArtifactSource())
                .isEqualTo(kr.co.mcmp.softwarecatalog.application.constants.ArtifactSource.NEXUS);
        assertThat(entity.getValue().getRepositoryUrl()).isEqualTo("registry.test:5500/busybox:1.37.0");
    }

    @ParameterizedTest @ValueSource(strings = {"Engine unavailable", "Registry not configured", "Authentication denied"})
    void failedPushDoesNotCreateMetadataOrReportSuccess(String failure) {
        var packages = mock(PackageInfoRepository.class);
        var hub = mock(DockerHubIntegrationService.class);
        var service = new DockerHubService(mock(CatalogService.class), packages, mock(NexusIntegrationService.class), hub,
                mock(kr.co.mcmp.softwarecatalog.application.service.impl.NexusImageAccess.class));
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
