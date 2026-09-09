package kr.co.mcmp.softwarecatalog.kubernetes.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Optional;
import com.marcnuri.helm.Release;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.jupiter.api.Test;
import kr.co.mcmp.softwarecatalog.SoftwareCatalog;
import kr.co.mcmp.softwarecatalog.application.dto.DeploymentRequest;
import kr.co.mcmp.softwarecatalog.application.model.DeploymentHistory;
import kr.co.mcmp.softwarecatalog.application.model.HelmChart;
import kr.co.mcmp.softwarecatalog.application.repository.ApplicationStatusRepository;
import kr.co.mcmp.softwarecatalog.application.repository.DeploymentHistoryRepository;
import kr.co.mcmp.softwarecatalog.kubernetes.config.KubeconfigResolver;
import kr.co.mcmp.softwarecatalog.kubernetes.config.KubernetesClientFactory;
import kr.co.mcmp.softwarecatalog.kubernetes.util.KubernetesUtils;
import kr.co.mcmp.softwarecatalog.service.SoftwareSourceService;
import kr.co.mcmp.softwarecatalog.users.repository.UserRepository;

class KubernetesDeployRollbackTest {
    @Test void accessFailureRollsBackCreatedReleaseBeforeReleasingFirewall() { check(false); }
    @Test void failedRollbackKeepsDeletePendingAndDoesNotReleaseFirewall() { check(true); }

    private void check(boolean rollbackFails) {
        var factory = mock(KubernetesClientFactory.class);
        var client = mock(KubernetesClient.class, RETURNS_DEEP_STUBS);
        var routes = KubernetesIngressRouteValidatorTest.stubIngressList(client);
        when(routes.list()).thenReturn(new io.fabric8.kubernetes.api.model.networking.v1.IngressListBuilder().build());
        var helm = mock(HelmChartService.class);
        var histories = mock(DeploymentHistoryRepository.class);
        var access = mock(K8sIngressAccessService.class);
        var sources = mock(SoftwareSourceService.class);
        var service = new KubernetesDeployService(factory, mock(KubernetesNamespaceService.class), helm,
                mock(UserRepository.class), mock(ApplicationStatusRepository.class), histories,
                sources, mock(KubeconfigResolver.class), access);
        var catalog = new SoftwareCatalog(); catalog.setId(10L); catalog.setDefaultPort(5572);
        var chart = new HelmChart(); chart.setChartName("rclone");
        var request = DeploymentRequest.builder().namespace("default").clusterName("azure")
                .ingressEnabled(true).ingressClass("nginx").ingressHost("rclone.test.com")
                .openServicePort(true).servicePortCidr("203.0.113.10/32").build();
        var release = mock(Release.class);
        when(release.getName()).thenReturn("rclone-exact-created-release");
        when(factory.getClient("default", "azure")).thenReturn(client);
        when(sources.getArtifactHubSource(10L)).thenReturn(Optional.of(chart));
        when(helm.deployHelmChart(client, "default", catalog, chart, "azure", request)).thenReturn(release);
        when(histories.saveAndFlush(any(DeploymentHistory.class))).thenAnswer(i -> {
            DeploymentHistory history = i.getArgument(0); history.setId(37L); return history;
        });
        doThrow(new IllegalArgumentException("SG lookup failed")).when(access).open(eq(request), any());
        if (rollbackFails) doThrow(new IllegalStateException("K8s unavailable")).when(helm)
                .uninstallRelease("default", "azure", "rclone-exact-created-release");
        try (var utilities = mockStatic(KubernetesUtils.class)) {
            assertThatThrownBy(() -> service.deployApplication("default", "azure", catalog, null, request))
                    .isInstanceOf(KubernetesDeployService.DeploymentFailure.class)
                    .hasMessageContaining("SG lookup failed");
        }
        var saved = org.mockito.ArgumentCaptor.forClass(DeploymentHistory.class);
        verify(histories, times(2)).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(rollbackFails ? "DELETE_PENDING" : "FAILED");
        var order = inOrder(helm, access);
        order.verify(helm).uninstallRelease("default", "azure", "rclone-exact-created-release");
        if (rollbackFails) verify(access, never()).release(anyLong());
        else order.verify(access).release(37L);
        verify(helm, never()).uninstallHelmChart(anyString(), any(), any(HelmChart.class), anyString());
    }
}
