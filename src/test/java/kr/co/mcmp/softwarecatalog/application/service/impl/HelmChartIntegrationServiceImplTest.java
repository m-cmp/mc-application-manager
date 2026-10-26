package kr.co.mcmp.softwarecatalog.application.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import kr.co.mcmp.oss.dto.OssDto;
import kr.co.mcmp.softwarecatalog.CatalogRepository;
import kr.co.mcmp.softwarecatalog.application.config.NexusConfig;
import kr.co.mcmp.softwarecatalog.application.dto.HelmChartRegistrationRequest;
import kr.co.mcmp.softwarecatalog.application.repository.HelmChartRepository;
import kr.co.mcmp.softwarecatalog.application.repository.PackageInfoRepository;
import kr.co.mcmp.softwarecatalog.application.service.ArtifactHubIntegrationService;
import kr.co.mcmp.softwarecatalog.application.service.NexusIntegrationService;
import kr.co.mcmp.softwarecatalog.users.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class HelmChartIntegrationServiceImplTest {

    @Test
    void failedChartCopyDoesNotSaveAHelmPackageOrReportRegistrationSuccess() {
        var request = request("5.8.56", "5.8.56", "https://charts.jenkins.io");
        request.setCategory("WEB_SERVERS");
        var detail = org.mockito.Mockito.mock(kr.co.mcmp.externalrepo.model.ArtifactHubPackage.Package.class);
        when(artifactHubIntegrationService.getPackageDetailInfo("helm", "jenkins", "jenkins", "5.8.56"))
                .thenReturn(detail);
        var importing = org.mockito.Mockito.spy(service);
        org.mockito.Mockito.doReturn(java.util.Map.of("success", false, "message", "Registry unavailable"))
                .when(importing).pushHelmChartToNexus(request);
        var result = importing.registerHelmChart(request, "test");
        assertThat(result.get("success")).isEqualTo(false);
        assertThat(result.get("message").toString()).contains("Registry unavailable");
        org.mockito.Mockito.verifyNoInteractions(helmChartRepository);
    }

    @Test
    void importingAChartDoesNotReuseAnExistingSeededOrAlreadyLinkedChart() {
        var seeded = kr.co.mcmp.softwarecatalog.application.model.HelmChart.builder()
                .chartName("jenkins").chartVersion("5.8.56").chartRepositoryUrl("https://charts.jenkins.io").build();
        when(helmChartRepository.findByChartNameAndChartVersion("jenkins", "5.8.56"))
                .thenReturn(java.util.List.of(seeded));
        java.util.Optional<?> existing = ReflectionTestUtils.invokeMethod(service, "findExistingHelmChart",
                request("5.8.56", "5.8.56", "https://charts.jenkins.io"));
        assertThat(existing).isEmpty();
        assertThat(seeded.getChartRepositoryUrl()).isEqualTo("https://charts.jenkins.io");
    }

    @Test
    void anImportedChartMustBeVerifiedInNexusInsteadOfUsingAnExternalFallback() {
        when(nexusIntegrationService.getRepositoryNameByFormat("helm")).thenReturn("helm-hosted");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service,
                "requireImportedChartRepositoryUrl", request("5.8.56", "5.8.56", "https://charts.jenkins.io")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("verified in Nexus");
    }

    @Mock
    private ArtifactHubIntegrationService artifactHubIntegrationService;

    @Mock
    private NexusIntegrationService nexusIntegrationService;

    @Mock
    private HelmChartRepository helmChartRepository;

    @Mock
    private PackageInfoRepository packageInfoRepository;

    @Mock
    private CatalogRepository catalogRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NexusConfig nexusConfig;

    @InjectMocks
    private HelmChartIntegrationServiceImpl service;

    @Test
    void usesNexusUrlOnlyWhenTheExactChartExists() {
        HelmChartRegistrationRequest request = request("5.8.56", "5.8.56", "https://charts.jenkins.io");
        when(nexusIntegrationService.getRepositoryNameByFormat("helm")).thenReturn("helm-hosted");
        when(nexusIntegrationService.checkHelmChartExistsInNexus("helm-hosted", "jenkins", "5.8.56"))
                .thenReturn(true);
        when(nexusIntegrationService.getNexusInfoFromDB()).thenReturn(
                OssDto.builder().ossUrl("http://nexus.test:8081").build());

        String resolved = ReflectionTestUtils.invokeMethod(service, "resolveChartRepositoryUrl", request);

        assertThat(resolved).isEqualTo("http://nexus.test:8081/repository/helm-hosted");
    }

    @Test
    void fallsBackToExternalRepositoryWhenTheExactChartIsMissing() {
        HelmChartRegistrationRequest request = request("5.8.56", "5.8.56", "https://charts.jenkins.io");
        when(nexusIntegrationService.getRepositoryNameByFormat("helm")).thenReturn("helm-hosted");
        when(nexusIntegrationService.checkHelmChartExistsInNexus("helm-hosted", "jenkins", "5.8.56"))
                .thenReturn(false);

        String resolved = ReflectionTestUtils.invokeMethod(service, "resolveChartRepositoryUrl", request);

        assertThat(resolved).isEqualTo("https://charts.jenkins.io");
        verify(nexusIntegrationService, never()).getNexusInfoFromDB();
    }

    @Test
    void usesTagAsChartVersionWhenVersionIsBlank() {
        HelmChartRegistrationRequest request = request(" ", "5.8.56", "https://charts.jenkins.io");
        when(nexusIntegrationService.getRepositoryNameByFormat("helm")).thenReturn("helm-hosted");
        when(nexusIntegrationService.checkHelmChartExistsInNexus("helm-hosted", "jenkins", "5.8.56"))
                .thenReturn(true);
        when(nexusIntegrationService.getNexusInfoFromDB()).thenReturn(
                OssDto.builder().ossUrl("http://nexus.test:8081").build());

        String resolved = ReflectionTestUtils.invokeMethod(service, "resolveChartRepositoryUrl", request);

        assertThat(resolved).isEqualTo("http://nexus.test:8081/repository/helm-hosted");
        verify(nexusIntegrationService)
                .checkHelmChartExistsInNexus("helm-hosted", "jenkins", "5.8.56");
    }

    @Test
    void fallsBackToExternalRepositoryWhenNexusLookupFails() {
        HelmChartRegistrationRequest request = request("5.8.56", "5.8.56", "https://charts.jenkins.io");
        when(nexusIntegrationService.getRepositoryNameByFormat("helm"))
                .thenThrow(new RuntimeException("Nexus unavailable"));

        String resolved = ReflectionTestUtils.invokeMethod(service, "resolveChartRepositoryUrl", request);

        assertThat(resolved).isEqualTo("https://charts.jenkins.io");
        verify(nexusIntegrationService, never())
                .checkHelmChartExistsInNexus("helm-hosted", "jenkins", "5.8.56");
    }

    @Test
    void returnsEmptyUrlWhenNeitherNexusChartNorExternalRepositoryExists() {
        HelmChartRegistrationRequest request = request("5.8.56", "5.8.56", " ");
        when(nexusIntegrationService.getRepositoryNameByFormat("helm")).thenReturn("helm-hosted");
        when(nexusIntegrationService.checkHelmChartExistsInNexus("helm-hosted", "jenkins", "5.8.56"))
                .thenReturn(false);

        String resolved = ReflectionTestUtils.invokeMethod(service, "resolveChartRepositoryUrl", request);

        assertThat(resolved).isEmpty();
    }

    private HelmChartRegistrationRequest request(String version, String tag, String externalRepositoryUrl) {
        return HelmChartRegistrationRequest.builder()
                .name("jenkins")
                .version(version)
                .tag(tag)
                .repository(HelmChartRegistrationRequest.Repository.builder()
                        .name("jenkins")
                        .url(externalRepositoryUrl)
                        .build())
                .build();
    }
}
