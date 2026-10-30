package kr.co.mcmp.softwarecatalog.application.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.mcmp.softwarecatalog.application.dto.DeploymentRequest;
import kr.co.mcmp.softwarecatalog.application.model.DeploymentHistory;
import kr.co.mcmp.softwarecatalog.application.repository.DeploymentHistoryRepository;
import kr.co.mcmp.softwarecatalog.application.service.ApplicationHistoryService;
import kr.co.mcmp.softwarecatalog.SoftwareCatalogDTO;
import kr.co.mcmp.softwarecatalog.application.constants.VmDeploymentMode;
import kr.co.mcmp.softwarecatalog.docker.model.DockerTarget;
import kr.co.mcmp.softwarecatalog.docker.service.DockerOperationService;
import kr.co.mcmp.softwarecatalog.docker.service.DockerSetupService;
import kr.co.mcmp.softwarecatalog.docker.model.ContainerDeployResult;
import kr.co.mcmp.ape.cbtumblebug.api.CbtumblebugRestApi;
import kr.co.mcmp.ape.cbtumblebug.dto.VmAccessInfo;
import kr.co.mcmp.softwarecatalog.application.dto.PackageInfoDTO;
import kr.co.mcmp.softwarecatalog.application.service.ObjectStorageAccessGrantService;

class DockerDeploymentServiceTest {

    @Test
    void failedReadinessKeepsTheContainerIdAndCannotBePromotedToSuccessJustBecauseItExists() {
        DockerDeploymentService service = mock(DockerDeploymentService.class, CALLS_REAL_METHODS);
        DockerOperationService operations = mock(DockerOperationService.class);
        CbtumblebugRestApi tumblebug = mock(CbtumblebugRestApi.class);
        ReflectionTestUtils.setField(service, "dockerOperationService", operations);
        ReflectionTestUtils.setField(service, "dockerSetupService", mock(DockerSetupService.class));
        ReflectionTestUtils.setField(service, "cbtumblebugRestApi", tumblebug);
        ReflectionTestUtils.setField(service, "applicationHistoryService", mock(ApplicationHistoryService.class));
        ReflectionTestUtils.setField(service, "deploymentHistoryRepository", mock(DeploymentHistoryRepository.class));
        ReflectionTestUtils.setField(service, "objectStorageAccessGrantService", mock(ObjectStorageAccessGrantService.class));
        VmAccessInfo vm = new VmAccessInfo();
        vm.setPublicIP("192.0.2.10");
        when(tumblebug.getVmInfo("ns", "infra", "vm1")).thenReturn(vm);
        when(operations.runDockerContainer(any(), anyMap(), any(), any(), any(), anyList(), anyInt()))
                .thenReturn(new ContainerDeployResult("failed-container", "Container readiness timed out", false));
        SoftwareCatalogDTO catalog = SoftwareCatalogDTO.builder().id(1L).name("Nginx").defaultPort(80)
                .packageInfo(PackageInfoDTO.builder().packageName("nginx").packageVersion("1.27").build()).build();
        DeploymentHistory history = new DeploymentHistory();
        history.setId(2L);
        DeploymentRequest request = DeploymentRequest.builder().namespace("ns").mciId("infra")
                .vmIds(List.of("vm1")).servicePort(8080).vmDeploymentMode(VmDeploymentMode.STANDALONE).build();

        Object result = ReflectionTestUtils.invokeMethod(service, "deployToSingleVmAsync",
                request, catalog, history, null, "vm1", 0, List.of("vm1"), null);

        assertThat(ReflectionTestUtils.getField(result, "success")).isEqualTo(false);
        assertThat(ReflectionTestUtils.getField(result, "containerId")).isEqualTo("failed-container");
        assertThat(history.getContainerId()).isEqualTo("failed-container");
        verify(operations, never()).isContainerRunning(any(), anyString());
    }

    @Test
    void clusterReadinessChecksBothNodesOnlyAfterTheirStartupResultsAreAvailable() throws Exception {
        DockerDeploymentService service = mock(DockerDeploymentService.class, CALLS_REAL_METHODS);
        DockerOperationService operations = mock(DockerOperationService.class);
        ReflectionTestUtils.setField(service, "dockerOperationService", operations);
        DeploymentRequest request = clusterRequest();

        Map<String, String> failures = ReflectionTestUtils.invokeMethod(service, "verifyElasticsearchCluster",
                request, SoftwareCatalogDTO.builder().name("Elasticsearch").build(),
                List.of(startupResult("vm1", true), startupResult("vm2", true)));

        assertThat(failures).isEmpty();
        verify(operations).awaitElasticsearchReady(new DockerTarget("ns", "infra", "vm1"), "container-vm1", 2);
        verify(operations).awaitElasticsearchReady(new DockerTarget("ns", "infra", "vm2"), "container-vm2", 2);
    }

    @Test
    void clusterDoesNotReportSuccessfulPeerWhenAnotherNodeFailedToStart() throws Exception {
        DockerDeploymentService service = mock(DockerDeploymentService.class, CALLS_REAL_METHODS);
        DockerOperationService operations = mock(DockerOperationService.class);
        ReflectionTestUtils.setField(service, "dockerOperationService", operations);

        Map<String, String> failures = ReflectionTestUtils.invokeMethod(service, "verifyElasticsearchCluster",
                clusterRequest(), SoftwareCatalogDTO.builder().name("Elasticsearch").build(),
                List.of(startupResult("vm1", true), startupResult("vm2", false)));

        assertThat(failures).containsKey("vm1");
        assertThat(failures.get("vm1")).contains("incomplete");
        verifyNoInteractions(operations);
    }

    @Test
    void clusterHealthFailureIsRetainedAndOtherApplicationsAreNotProbedAsElasticsearch() throws Exception {
        DockerDeploymentService service = mock(DockerDeploymentService.class, CALLS_REAL_METHODS);
        DockerOperationService operations = mock(DockerOperationService.class);
        ReflectionTestUtils.setField(service, "dockerOperationService", operations);
        doThrow(new IllegalStateException("Expected 2 nodes; joined=1")).when(operations)
                .awaitElasticsearchReady(new DockerTarget("ns", "infra", "vm1"), "container-vm1", 2);
        List<CompletableFuture<?>> results = List.of(startupResult("vm1", true), startupResult("vm2", true));

        Map<String, String> failures = ReflectionTestUtils.invokeMethod(service, "verifyElasticsearchCluster",
                clusterRequest(), SoftwareCatalogDTO.builder().name("Elasticsearch").build(), results);
        assertThat(failures).containsEntry("vm1", "Expected 2 nodes; joined=1");
        clearInvocations(operations);
        Map<String, String> generic = ReflectionTestUtils.invokeMethod(service, "verifyElasticsearchCluster",
                clusterRequest(), SoftwareCatalogDTO.builder().name("Nginx").build(), results);
        assertThat(generic).isEmpty();
        verifyNoInteractions(operations);
    }

    private DeploymentRequest clusterRequest() {
        return DeploymentRequest.builder().namespace("ns").mciId("infra").vmIds(List.of("vm1", "vm2"))
                .vmDeploymentMode(VmDeploymentMode.CLUSTERING).build();
    }

    private CompletableFuture<?> startupResult(String vmId, boolean success) throws Exception {
        var constructor = Class.forName(DockerDeploymentService.class.getName() + "$DeploymentResult")
                .getDeclaredConstructor(String.class, boolean.class, String.class, String.class);
        constructor.setAccessible(true);
        return CompletableFuture.completedFuture(constructor.newInstance(vmId, success, "container-" + vmId,
                success ? null : "startup failed"));
    }

    @ParameterizedTest
    @CsvSource({"false,FAILED", "true,SUCCESS"})
    void persistsFinalHistoryStatusInsteadOfLeavingItInProgress(boolean success, String expectedStatus) {
        DockerDeploymentService service = mock(DockerDeploymentService.class, CALLS_REAL_METHODS);
        DeploymentHistoryRepository repository = mock(DeploymentHistoryRepository.class);
        ReflectionTestUtils.setField(service, "deploymentHistoryRepository", repository);
        ReflectionTestUtils.setField(service, "applicationHistoryService", mock(ApplicationHistoryService.class));
        DeploymentHistory history = new DeploymentHistory();
        history.setStatus("IN_PROGRESS");

        ReflectionTestUtils.invokeMethod(service, "processDeploymentResults", history, null,
                success ? List.of("vm") : List.of(),
                success ? List.of() : List.of("vm (image pull timed out)"), new DeploymentRequest());

        assertThat(history.getStatus()).isEqualTo(expectedStatus);
        assertThat(history.getUpdatedAt()).isNotNull();
        verify(repository).save(history);
    }

    @Test
    void batchSummaryDoesNotReplaceTheFirstVmsAlreadySavedIndividualStatus() {
        DockerDeploymentService service = mock(DockerDeploymentService.class, CALLS_REAL_METHODS);
        DeploymentHistoryRepository repository = mock(DeploymentHistoryRepository.class);
        ReflectionTestUtils.setField(service, "deploymentHistoryRepository", repository);
        ReflectionTestUtils.setField(service, "applicationHistoryService", mock(ApplicationHistoryService.class));
        DeploymentHistory history = new DeploymentHistory();
        history.setStatus("FAILED");
        DeploymentRequest request = new DeploymentRequest();
        request.setVmIds(List.of("failed-vm", "successful-vm"));

        ReflectionTestUtils.invokeMethod(service, "processDeploymentResults", history, null,
                List.of("successful-vm"), List.of("failed-vm"), request);

        assertThat(history.getStatus()).isEqualTo("PARTIAL_SUCCESS");
        verifyNoInteractions(repository);
    }

    @Test
    void bundledObjectStorageNotebookIsValidAndDoesNotContainCredentials() throws Exception {
        String notebook = DockerDeploymentService.jupyterObjectStorageNotebook();
        JsonNode root = new ObjectMapper().readTree(notebook);

        assertThat(root.path("nbformat").asInt()).isEqualTo(4);
        assertThat(root.path("cells").size()).isEqualTo(15);
        assertThat(root.path("metadata").path("mcmp").path("templateVersion").asInt()).isEqualTo(3);
        assertThat(notebook)
                .contains("MCMP_OBJECT_STORAGE_GATEWAY_URL")
                .contains("presigned-url")
                .doesNotContain("AWS_SECRET_ACCESS_KEY")
                .doesNotContain("TUMBLEBUG_PASSWORD");
    }

    @Test
    void notebookIncludesAnalysisFlowWithoutAutomaticWrites() throws Exception {
        JsonNode root = new ObjectMapper().readTree(DockerDeploymentService.jupyterObjectStorageNotebook());
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (JsonNode cell : root.path("cells")) {
            assertThat(ids.add(cell.path("id").asText())).isTrue();
            if ("code".equals(cell.path("cell_type").asText())) {
                assertThat(cell.path("outputs").isEmpty()).isTrue();
                assertThat(cell.path("execution_count").isNull()).isTrue();
            }
            if ("mcmp-export".equals(cell.path("id").asText())) {
                for (JsonNode line : cell.path("source")) {
                    assertThat(line.asText().strip()).startsWith("#");
                }
            }
        }
        assertThat(ids).contains("mcmp-storages", "mcmp-settings", "mcmp-objects",
                "mcmp-preview", "mcmp-chart", "mcmp-export");
        assertThat(DockerDeploymentService.jupyterObjectStorageNotebook())
                .contains("sample-data")
                .contains("background_gradient")
                .contains("plt.cm.Blues")
                .doesNotContain("CONNECTED CLOUD STORAGE")
                .doesNotContain("SAMPLE DATA DASHBOARD")
                .doesNotContain("card_html");
    }
}
