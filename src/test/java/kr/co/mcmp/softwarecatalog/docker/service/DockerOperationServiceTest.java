package kr.co.mcmp.softwarecatalog.docker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import kr.co.mcmp.softwarecatalog.docker.model.ContainerDeployResult;
import kr.co.mcmp.softwarecatalog.docker.model.DockerCommandResult;
import kr.co.mcmp.softwarecatalog.docker.model.DockerHostResourceInfo;
import kr.co.mcmp.softwarecatalog.docker.model.DockerTarget;

@ExtendWith(MockitoExtension.class)
class DockerOperationServiceTest {

    private static final DockerTarget TARGET = new DockerTarget("ns01", "mci-01", "vm-01");
    private static final String CONTAINER_ID = "a".repeat(64);

    @Mock
    private DockerSshCommandExecutor commandExecutor;

    private DockerOperationService service;
    @Mock
    private kr.co.mcmp.softwarecatalog.application.service.impl.NexusImageAccess nexusImageAccess;

    @Test
    void checksElasticsearchBeforeCreatingAContainerAndVerifiesClusterHealth() {
        when(commandExecutor.execute(eq(TARGET), anyString()))
                .thenReturn(new DockerCommandResult(0, "vm.max_map_count=262144; unchanged", ""));
        when(commandExecutor.executeAllowFailure(eq(TARGET), anyString()))
                .thenReturn(new DockerCommandResult(0, "__MCMP_CONTAINER_ID__=" + CONTAINER_ID, ""))
                .thenReturn(new DockerCommandResult(0, "{\"timed_out\":false,\"status\":\"yellow\",\"number_of_nodes\":1}", ""));
        var result = service.runDockerContainer(TARGET, Map.of("name", "es-test",
                "image", "elasticsearch:8.11.0", "portBindings", "9200:9200"), List.of(), -1);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getDeploymentResult()).contains("vm.max_map_count=262144", "unchanged");
        var ordered = org.mockito.Mockito.inOrder(commandExecutor);
        ordered.verify(commandExecutor).execute(eq(TARGET), org.mockito.ArgumentMatchers.contains("docker pull"));
        ordered.verify(commandExecutor).execute(eq(TARGET), org.mockito.ArgumentMatchers.contains("Passwordless sudo"));
        ordered.verify(commandExecutor).executeAllowFailure(eq(TARGET), org.mockito.ArgumentMatchers.contains("docker start"));
        ordered.verify(commandExecutor).executeAllowFailure(eq(TARGET), org.mockito.ArgumentMatchers.contains("/_cluster/health"));
    }

    @Test
    void prerequisiteDenialPreventsContainerCreationAndPreservesTheReason() {
        when(commandExecutor.execute(eq(TARGET), anyString())).thenAnswer(invocation -> {
            if (((String) invocation.getArgument(1)).contains("mcmp_current"))
                throw new DockerCommandException(TARGET, 77,
                        "vm.max_map_count=65530; minimum=262144; Passwordless sudo is required");
            return new DockerCommandResult(0, "", "");
        });
        var result = service.runDockerContainer(TARGET, Map.of("name", "es-denied",
                "image", "elasticsearch:8.11.0", "portBindings", "9200:9200"), List.of(), -1);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getDeploymentResult()).contains("Elasticsearch prerequisites", "65530", "262144", "sudo");
        verify(commandExecutor, never()).executeAllowFailure(eq(TARGET), anyString());
    }

    @Test
    void readinessFailureKeepsTheExactContainerForFailureDetails() {
        when(commandExecutor.execute(eq(TARGET), anyString())).thenReturn(new DockerCommandResult(0, "", ""));
        when(commandExecutor.executeAllowFailure(eq(TARGET), anyString())).thenReturn(
                new DockerCommandResult(124, "__MCMP_CONTAINER_ID__=" + CONTAINER_ID, "Container readiness timed out"));
        var result = service.runDockerContainer(TARGET, Map.of("name", "unhealthy",
                "image", "nginx:1.27", "portBindings", "8080:80"), List.of(), -1);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getContainerId()).isEqualTo(CONTAINER_ID);
        assertThat(result.getDeploymentResult()).contains("readiness timed out");
    }

    @Test
    void clusteringDoesNotWaitForPeersInsideAnIndividualStartupWorker() {
        when(commandExecutor.execute(eq(TARGET), anyString())).thenReturn(new DockerCommandResult(0, "", ""));
        var result = service.runDockerContainer(TARGET, Map.of("name", "es-cluster",
                "image", "elasticsearch:8.11.0", "portBindings", "9200:9200,9300:9300"),
                List.of("10.0.0.1", "10.0.0.2"), 0);
        assertThat(result.isSuccess()).isTrue();
        verify(commandExecutor, never()).executeAllowFailure(eq(TARGET), org.mockito.ArgumentMatchers.contains("/_cluster/health"));
    }

    @Test
    void pullsAnImportedImageWithShortLivedAuthenticationWithoutSavingALogin() {
        when(nexusImageAccess.dockerAuthConfig("registry.test:5500/nginx:v1"))
                .thenReturn("{\"auths\":{\"registry.test:5500\":{\"auth\":\"test-token\"}}}");
        when(commandExecutor.execute(eq(TARGET), anyString()))
                .thenReturn(new DockerCommandResult(0, "__MCMP_CONTAINER_ID__=" + CONTAINER_ID, ""));
        var result = service.runDockerContainer(TARGET, Map.of("name", "imported-nginx",
                "image", "registry.test:5500/nginx:v1", "portBindings", "8080:80", "artifactSource", "NEXUS"), List.of(), 0);
        assertThat(result.isSuccess()).isTrue();
        var script = ArgumentCaptor.forClass(String.class);
        verify(commandExecutor).execute(eq(TARGET), script.capture());
        assertThat(script.getAllValues().get(0)).contains("umask 077", "mktemp -d", "trap", "rmdir", "docker --config")
                .doesNotContain("docker login", "test-token");
    }

    @BeforeEach
    void setUp() {
        service = new DockerOperationService(commandExecutor, new ObjectMapper(), nexusImageAccess);
        org.mockito.Mockito.lenient().when(commandExecutor.executeAllowFailure(eq(TARGET), anyString()))
                .thenReturn(new DockerCommandResult(0, "__MCMP_CONTAINER_ID__=" + CONTAINER_ID, ""));
    }

    @Test
    void buildsPinnedJenkinsDeploymentWithLabelsAndPort() {
        when(commandExecutor.execute(eq(TARGET), anyString()))
                .thenReturn(new DockerCommandResult(
                        0, "__MCMP_CONTAINER_ID__=" + CONTAINER_ID, ""));
        Map<String, String> params = Map.of(
                "name", "jenkins-test",
                "image", "jenkins/jenkins:2.504.3-lts-jdk17",
                "portBindings", "8080:8080,50000:50000",
                "catalogId", "11",
                "deploymentId", "101",
                "debugKeepAlive", "false");

        ContainerDeployResult result =
                service.runDockerContainer(TARGET, params, List.of("10.0.0.1"), 0);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getContainerId()).isEqualTo(CONTAINER_ID);
        ArgumentCaptor<String> script = ArgumentCaptor.forClass(String.class);
        verify(commandExecutor).execute(eq(TARGET), script.capture());
        verify(commandExecutor).executeAllowFailure(eq(TARGET), script.capture());
        assertThat(script.getAllValues().get(0))
                .contains("docker image inspect")
                .contains("timeout --kill-after=10s 1800s docker pull")
                .doesNotContain("docker create");
        assertThat(script.getAllValues().get(1))
                .contains("'--pull=never'")
                .doesNotContain("docker pull")
                .contains("'jenkins/jenkins:2.504.3-lts-jdk17'")
                .contains("'jenkins-test'")
                .contains("'8080:8080'")
                .contains("'50000:50000'")
                .contains("'mcmp.managed=true'")
                .contains("'mcmp.namespace=ns01'")
                .contains("'mcmp.deployment-id=101'");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "0:80",
            "65536:80",
            "8080:0",
            "8080:65536",
            "abc:80",
            "8080",
            "8080:80:tcp"
    })
    void rejectsInvalidPortMappings(String portMapping) {
        ContainerDeployResult result = service.runDockerContainer(
                TARGET,
                Map.of("name", "safe-name", "image", "nginx:1.27", "portBindings", portMapping),
                List.of(),
                0);

        assertThat(result.isSuccess()).isFalse();
        verify(commandExecutor, never()).execute(eq(TARGET), anyString());
    }

    @Test
    void acceptsPortBoundaryValues() {
        when(commandExecutor.execute(eq(TARGET), anyString()))
                .thenReturn(new DockerCommandResult(
                        0, "__MCMP_CONTAINER_ID__=" + CONTAINER_ID, ""));

        ContainerDeployResult result = service.runDockerContainer(
                TARGET,
                Map.of("name", "port-boundary", "image", "nginx:1.27",
                        "portBindings", "1:1,65535:65535"),
                List.of(),
                0);

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void addsJupyterEnvironmentVolumeAndCommandAsQuotedDockerArguments() {
        when(commandExecutor.execute(eq(TARGET), anyString()))
                .thenReturn(new DockerCommandResult(
                        0, "__MCMP_CONTAINER_ID__=" + CONTAINER_ID, ""));

        ContainerDeployResult result = service.runDockerContainer(
                TARGET,
                Map.of(
                        "name", "jupyter-object-storage",
                        "image", "quay.io/jupyter/scipy-notebook:2026-07-28",
                        "portBindings", "8888:8888",
                        "catalogId", "11",
                        "deploymentId", "102"),
                Map.of(
                        "S3_BUCKET", "sample-bucket",
                        "AWS_SECRET_ACCESS_KEY", "secret$(id)'value"),
                "mcmp-jupyter-work:/home/jovyan/work",
                List.of("bash", "-lc", "exec start-notebook.py --ServerApp.token=\"$JUPYTER_TOKEN\""),
                List.of(),
                0);

        assertThat(result.isSuccess()).isTrue();
        ArgumentCaptor<String> script = ArgumentCaptor.forClass(String.class);
        verify(commandExecutor).execute(eq(TARGET), script.capture());
        verify(commandExecutor).executeAllowFailure(eq(TARGET), script.capture());
        assertThat(script.getAllValues().get(1))
                .contains("'S3_BUCKET=sample-bucket'")
                .contains("'mcmp-jupyter-work:/home/jovyan/work'")
                .contains("'bash'")
                .contains("'exec start-notebook.py --ServerApp.token=\"$JUPYTER_TOKEN\"'")
                .doesNotContain("-e AWS_SECRET_ACCESS_KEY=secret$(id)");
    }

    @Test
    void doesNotCreateContainerWhenImagePreparationFails() {
        when(commandExecutor.execute(eq(TARGET), anyString()))
                .thenThrow(new DockerCommandException(TARGET, 124, "image pull timed out"));

        ContainerDeployResult result = service.runDockerContainer(
                TARGET,
                Map.of("name", "safe", "image", "large/image:latest", "portBindings", "8888:8888"),
                List.of(),
                0);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getDeploymentResult())
                .contains("image preparation")
                .contains("image pull timed out");
        ArgumentCaptor<String> script = ArgumentCaptor.forClass(String.class);
        verify(commandExecutor).execute(eq(TARGET), script.capture());
        assertThat(script.getValue()).contains("docker pull").doesNotContain("docker create");
    }

    @Test
    void rejectsInvalidRequestedEnvironmentAndVolume() {
        ContainerDeployResult invalidEnvironment = service.runDockerContainer(
                TARGET,
                Map.of("name", "safe", "image", "nginx:1.27", "portBindings", "8080:80"),
                Map.of("BAD-NAME", "value"),
                null,
                List.of(),
                List.of(),
                0);
        ContainerDeployResult invalidVolume = service.runDockerContainer(
                TARGET,
                Map.of("name", "safe", "image", "nginx:1.27", "portBindings", "8080:80"),
                Map.of(),
                "../../host:/home/jovyan/work",
                List.of(),
                List.of(),
                0);

        assertThat(invalidEnvironment.isSuccess()).isFalse();
        assertThat(invalidVolume.isSuccess()).isFalse();
        verify(commandExecutor, never()).execute(eq(TARGET), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "safe;touch-/tmp/pwned",
            "safe$(id)",
            "../escape",
            "white space",
            "quote'name"
    })
    void rejectsContainerNameCommandInjection(String name) {
        ContainerDeployResult result = service.runDockerContainer(
                TARGET,
                Map.of("name", name, "image", "nginx:1.27", "portBindings", "8080:80"),
                List.of(),
                0);

        assertThat(result.isSuccess()).isFalse();
        verify(commandExecutor, never()).execute(eq(TARGET), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "nginx;id",
            "nginx $(id)",
            "nginx'bad",
            "../nginx",
            "nginx\nwhoami"
    })
    void rejectsImageReferenceCommandInjection(String image) {
        ContainerDeployResult result = service.runDockerContainer(
                TARGET,
                Map.of("name", "safe", "image", image, "portBindings", "8080:80"),
                List.of(),
                0);

        assertThat(result.isSuccess()).isFalse();
        verify(commandExecutor, never()).execute(eq(TARGET), anyString());
    }

    @Test
    void returnsNullWhenNamedContainerDoesNotExist() {
        when(commandExecutor.execute(eq(TARGET), anyString()))
                .thenReturn(new DockerCommandResult(0, "", ""));

        assertThat(service.getContainerId(TARGET, "jenkins-test")).isNull();
    }

    @Test
    void rejectsUntrustedContainerIdentifierForControlAction() {
        assertThatThrownBy(() -> service.stopDockerContainer(TARGET, "name;shutdown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("container ID");
        verify(commandExecutor, never()).execute(eq(TARGET), anyString());
    }

    @Test
    void parsesDockerHostResourceJson() {
        when(commandExecutor.execute(eq(TARGET), anyString()))
                .thenReturn(new DockerCommandResult(
                        0, "{\"NCPU\":2,\"MemTotal\":4294967296}", ""));

        DockerHostResourceInfo info = service.getHostResourceInfo(TARGET);

        assertThat(info.getCpuCores()).isEqualTo(2);
        assertThat(info.getMemoryGb()).isEqualTo(4.0);
    }

    @Test
    void limitsRemoteLogVolume() {
        assertThatThrownBy(() -> service.getContainerLogs(TARGET, CONTAINER_ID, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.getContainerLogs(TARGET, CONTAINER_ID, 1001))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
