package kr.co.mcmp.softwarecatalog.kubernetes.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import io.fabric8.kubernetes.client.KubernetesClient;
import kr.co.mcmp.softwarecatalog.SoftwareCatalog;
import kr.co.mcmp.softwarecatalog.application.constants.DeploymentType;
import kr.co.mcmp.softwarecatalog.application.dto.DeploymentRequest;
import kr.co.mcmp.softwarecatalog.application.model.DeploymentHistory;
import kr.co.mcmp.softwarecatalog.application.model.HelmChart;
import kr.co.mcmp.softwarecatalog.application.repository.ApplicationStatusRepository;
import kr.co.mcmp.softwarecatalog.application.repository.DeploymentHistoryRepository;
import kr.co.mcmp.softwarecatalog.kubernetes.config.KubeconfigResolver;
import kr.co.mcmp.softwarecatalog.kubernetes.config.KubernetesClientFactory;
import kr.co.mcmp.softwarecatalog.kubernetes.config.KubernetesNamespaces;
import kr.co.mcmp.softwarecatalog.kubernetes.util.KubernetesUtils;
import kr.co.mcmp.softwarecatalog.service.SoftwareSourceService;
import kr.co.mcmp.softwarecatalog.users.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class KubernetesDeployServiceNamespaceTest {

    @Mock
    private KubernetesClientFactory clientFactory;
    @Mock
    private KubernetesNamespaceService namespaceService;
    @Mock
    private HelmChartService helmChartService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ApplicationStatusRepository applicationStatusRepository;
    @Mock
    private DeploymentHistoryRepository deploymentHistoryRepository;
    @Mock
    private SoftwareSourceService softwareSourceService;
    @Mock
    private KubeconfigResolver kubeconfigResolver;

    @InjectMocks
    private KubernetesDeployService service;

    @ParameterizedTest
    @ValueSource(strings = {"project-a", "project-b", "default"})
    void keepsTumblebugProjectSeparateFromKubernetesWorkloadNamespace(String tumblebugNamespace) {
        String clusterName = "cluster-a";
        KubernetesClient client = mock(KubernetesClient.class);
        HelmChart helmChart = HelmChart.builder().chartName("grafana").build();
        SoftwareCatalog catalog = SoftwareCatalog.builder()
                .id(7L)
                .name("Grafana")
                .defaultPort(3000)
                .ingressEnabled(false)
                .helmChart(helmChart)
                .build();
        DeploymentRequest request = DeploymentRequest.builder()
                .namespace(tumblebugNamespace)
                .clusterName(clusterName)
                .catalogId(catalog.getId())
                .deploymentType(DeploymentType.K8S)
                .build();

        when(softwareSourceService.getArtifactHubSource(catalog.getId())).thenReturn(Optional.of(helmChart));
        when(clientFactory.getClient(tumblebugNamespace, clusterName)).thenReturn(client);
        when(applicationStatusRepository.findLatestByNamespaceAndClusterNameAndCatalogId(
                tumblebugNamespace, clusterName, catalog.getId())).thenReturn(Optional.empty());
        when(helmChartService.deployHelmChart(
                client, tumblebugNamespace, catalog, helmChart, clusterName, request)).thenReturn(null);
        when(helmChartService.findLatestReleaseNameForChart(
                tumblebugNamespace, clusterName, helmChart.getChartName())).thenReturn("grafana-release");
        when(deploymentHistoryRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(DeploymentHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        try (MockedStatic<KubernetesUtils> kubernetesUtils = mockStatic(KubernetesUtils.class)) {
            kubernetesUtils.when(() -> KubernetesUtils.getPodStatus(
                    client, KubernetesNamespaces.APPLICATION_WORKLOAD, helmChart.getChartName()))
                    .thenReturn("Running");
            kubernetesUtils.when(() -> KubernetesUtils.getServicePort(
                    client, KubernetesNamespaces.APPLICATION_WORKLOAD, helmChart.getChartName()))
                    .thenReturn(3000);

            DeploymentHistory history = service.deployApplication(
                    tumblebugNamespace, clusterName, catalog, null, request);

            assertThat(history.getNamespace()).isEqualTo(tumblebugNamespace);
            assertThat(history.getPodStatus()).isEqualTo("Running");
            assertThat(history.getServicePort()).isEqualTo(3000);
            kubernetesUtils.verify(() -> KubernetesUtils.getPodStatus(
                    client, KubernetesNamespaces.APPLICATION_WORKLOAD, helmChart.getChartName()));
            kubernetesUtils.verify(() -> KubernetesUtils.getServicePort(
                    client, KubernetesNamespaces.APPLICATION_WORKLOAD, helmChart.getChartName()));
        }

        verify(clientFactory).getClient(tumblebugNamespace, clusterName);
        verify(helmChartService).ensureMetricsServer(client, tumblebugNamespace, clusterName);
    }
}
