package kr.co.mcmp.softwarecatalog.docker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;

import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import kr.co.mcmp.softwarecatalog.docker.model.ContainerHealthInfo;
import kr.co.mcmp.softwarecatalog.docker.model.DockerCommandResult;
import kr.co.mcmp.softwarecatalog.docker.model.DockerTarget;

@ExtendWith(MockitoExtension.class)
class ContainerStatsCollectorTest {

    private static final DockerTarget TARGET = new DockerTarget("ns01", "mci01", "vm01");
    private static final String CONTAINER_ID = "b".repeat(64);

    @Mock
    private DockerSshCommandExecutor commandExecutor;

    private ContainerStatsCollector collector;

    @BeforeEach
    void setUp() {
        collector = new ContainerStatsCollector(commandExecutor, new ObjectMapper());
    }

    @Test
    void parsesRunningHealthyContainerSnapshot() {
        String inspect = """
                [{
                  "Id": "%s",
                  "HostConfig": {},
                  "State": {
                    "Status": "running",
                    "Running": true,
                    "OOMKilled": false,
                    "Health": {"Status": "healthy"}
                  },
                  "RestartCount": 2,
                  "NetworkSettings": {
                    "Ports": {
                      "8080/tcp": [{"HostIp": "0.0.0.0", "HostPort": "8080"}]
                    }
                  }
                }]
                """.formatted(CONTAINER_ID);
        String stats = """
                {"CPUPerc":"1.25%%","MemUsage":"12.5MiB / 1GiB","MemPerc":"1.22%%","NetIO":"1.5kB / 2MiB"}
                """;
        when(commandExecutor.execute(eq(TARGET), anyString()))
                .thenReturn(new DockerCommandResult(
                        0, structuredOutput(inspect, stats, true), ""));

        ContainerHealthInfo health = collector.collectContainerStats(TARGET, CONTAINER_ID);

        assertThat(health.getStatus()).isEqualTo("RUNNING");
        assertThat(health.getServicePorts()).isEqualTo(8080);
        assertThat(health.getIsPortAccess()).isTrue();
        assertThat(health.getIsHealthCheck()).isTrue();
        assertThat(health.getCpuUsage()).isEqualTo(1.25);
        assertThat(health.getRawCpuUsage()).isEqualTo(1.25);
        assertThat(health.getCpuCapacityCores()).isEqualTo(1.0);
        assertThat(health.getCpuMetricsVersion()).isEqualTo(1);
        assertThat(health.getMemoryUsage()).isEqualTo(1.22);
        assertThat(health.getMemoryBytes()).isEqualTo(13_107_200L);
        assertThat(health.getMemoryLimitBytes()).isEqualTo(1_073_741_824L);
        assertThat(health.getNetworkIn()).isEqualTo(1_500.0);
        assertThat(health.getNetworkOut()).isEqualTo(2_097_152.0);
        assertThat(health.getRestartCount()).isEqualTo(2);
        assertThat(health.getOomKilled()).isFalse();
    }

    @Test
    void mapsStoppedOomContainerWithoutStats() {
        String inspect = """
                [{
                  "State": {
                    "Status": "exited",
                    "Running": false,
                    "OOMKilled": true,
                    "ExitCode": 137
                  },
                  "RestartCount": 4,
                  "NetworkSettings": {"Ports": {}}
                }]
                """;
        when(commandExecutor.execute(eq(TARGET), anyString()))
                .thenReturn(new DockerCommandResult(
                        0, structuredOutput(inspect, "{}", false), ""));

        ContainerHealthInfo health = collector.collectContainerStats(TARGET, CONTAINER_ID);

        assertThat(health.getStatus()).isEqualTo("STOPPED");
        assertThat(health.getIsHealthCheck()).isFalse();
        assertThat(health.getIsPortAccess()).isFalse();
        assertThat(health.getCpuUsage()).isNull();
        assertThat(health.getMemoryUsage()).isNull();
        assertThat(health.getOomKilled()).isTrue();
        assertThat(health.getRestartCount()).isEqualTo(4);
    }

    @Test
    void toleratesMalformedOptionalMetricsWithoutCorruptingLifecycleState() {
        String inspect = """
                [{
                  "State": {"Status": "running", "Running": true, "OOMKilled": false},
                  "RestartCount": 0,
                  "NetworkSettings": {"Ports": null}
                }]
                """;
        String stats = """
                {"CPUPerc":"n/a","MemUsage":"unknown","MemPerc":"--","NetIO":"bad"}
                """;
        when(commandExecutor.execute(eq(TARGET), anyString()))
                .thenReturn(new DockerCommandResult(
                        0, structuredOutput(inspect, stats, false), ""));

        ContainerHealthInfo health = collector.collectContainerStats(TARGET, CONTAINER_ID);

        assertThat(health.getStatus()).isEqualTo("RUNNING");
        assertThat(health.getIsHealthCheck()).isTrue();
        assertThat(health.getCpuUsage()).isNull();
        assertThat(health.getMemoryBytes()).isNull();
        assertThat(health.getNetworkIn()).isNull();
    }

    @Test
    void returnsErrorForMissingStructuredSections() {
        when(commandExecutor.execute(eq(TARGET), anyString()))
                .thenReturn(new DockerCommandResult(0, "unexpected human output", ""));

        assertThat(collector.collectContainerStats(TARGET, CONTAINER_ID).getStatus())
                .isEqualTo("ERROR");
    }

    @Test
    void rejectsContainerIdInjectionBeforeRemoteExecution() {
        assertThatThrownBy(() -> collector.collectContainerStats(TARGET, "id;cat-/etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void normalizesRawCpuOnceAgainstTwoCpuHostOnEveryCollection() {
        String output = structuredOutput(runningInspect("{}"),
                "{\"CPUPerc\":\"87.48%\",\"MemPerc\":\"20%\",\"NetIO\":\"1MB / 2MB\"}", true)
                .replace("__MCMP_HOST_CPUS__=1", "__MCMP_HOST_CPUS__=2");
        when(commandExecutor.execute(eq(TARGET), anyString()))
                .thenReturn(new DockerCommandResult(0, output, ""));

        for (int i = 0; i < 2; i++) {
            ContainerHealthInfo health = collector.collectContainerStats(TARGET, CONTAINER_ID);
            assertThat(health.getCpuUsage()).isEqualTo(43.74);
            assertThat(health.getRawCpuUsage()).isEqualTo(87.48);
            assertThat(health.getCpuCapacityCores()).isEqualTo(2.0);
            assertThat(health.getCpuMetricsVersion()).isEqualTo(1);
            assertThat(health.getMemoryUsage()).isEqualTo(20.0);
            assertThat(health.getNetworkIn()).isEqualTo(1_000_000.0);
        }
    }

    @Test
    void honorsRuntimeParentQuotaAndEffectiveAffinity() {
        String output = structuredOutput(runningInspect("{\"NanoCpus\":2000000000,\"CpusetCpus\":\"0-3\"}"),
                "{\"CPUPerc\":\"40%\"}", false)
                .replace("__MCMP_HOST_CPUS__=1", "__MCMP_HOST_CPUS__=4")
                .replace("__MCMP_ALLOWED_CPUS__=0-255", "__MCMP_ALLOWED_CPUS__=2-3")
                + "\n"
                + "__MCMP_RUNTIME_CPU_QUOTA__=max 100000\n"
                + "__MCMP_RUNTIME_CPU_QUOTA__=50000 100000\n";
        when(commandExecutor.execute(eq(TARGET), anyString()))
                .thenReturn(new DockerCommandResult(0, output, ""));

        ContainerHealthInfo health = collector.collectContainerStats(TARGET, CONTAINER_ID);

        assertThat(health.getCpuCapacityCores()).isEqualTo(0.5);
        assertThat(health.getCpuUsage()).isEqualTo(80.0);
    }

    @Test
    void missingHostCapacityRetainsRawCpuAndDoesNotDamageLifecycleOrMemory() {
        String output = structuredOutput(runningInspect("{}"),
                "{\"CPUPerc\":\"150%\",\"MemUsage\":\"10MiB / 1GiB\"}", false)
                .replace("__MCMP_HOST_CPUS__=1\n", "");
        when(commandExecutor.execute(eq(TARGET), anyString()))
                .thenReturn(new DockerCommandResult(0, output, ""));

        ContainerHealthInfo health = collector.collectContainerStats(TARGET, CONTAINER_ID);

        assertThat(health.getStatus()).isEqualTo("RUNNING");
        assertThat(health.getIsHealthCheck()).isTrue();
        assertThat(health.getMemoryBytes()).isEqualTo(10_485_760L);
        assertThat(health.getRawCpuUsage()).isEqualTo(150.0);
        assertThat(health.getCpuUsage()).isNull();
        assertThat(health.getCpuCapacityCores()).isNull();
        assertThat(health.getCpuMetricsVersion()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-probe", "failed-probe", "missing-affinity", "bad-affinity", "bad-quota"})
    void requiresCompleteVerifiedRuntimeProbeBeforeClaimingNormalizedCpu(String failure) {
        String output = structuredOutput(runningInspect("{}"), "{\"CPUPerc\":\"80%\",\"MemPerc\":\"20%\"}", true);
        output = switch (failure) {
            case "missing-probe" -> output.replace("__MCMP_RUNTIME_CPU_PROBE_OK__=true\n", "");
            case "failed-probe" -> output.replace("__MCMP_RUNTIME_CPU_PROBE_OK__=true", "__MCMP_RUNTIME_CPU_PROBE_OK__=false");
            case "missing-affinity" -> output.replace("__MCMP_ALLOWED_CPUS__=0-255\n", "");
            case "bad-affinity" -> output.replace("__MCMP_ALLOWED_CPUS__=0-255", "__MCMP_ALLOWED_CPUS__=unknown");
            case "bad-quota" -> output + "\n__MCMP_RUNTIME_CPU_QUOTA__=unreadable\n";
            default -> throw new IllegalArgumentException(failure);
        };
        when(commandExecutor.execute(eq(TARGET), anyString()))
                .thenReturn(new DockerCommandResult(0, output, ""));

        ContainerHealthInfo health = collector.collectContainerStats(TARGET, CONTAINER_ID);

        assertThat(health.getStatus()).isEqualTo("RUNNING");
        assertThat(health.getMemoryUsage()).isEqualTo(20.0);
        assertThat(health.getRawCpuUsage()).isEqualTo(80.0);
        assertThat(health.getCpuCapacityCores()).isNull();
        assertThat(health.getCpuUsage()).isNull();
        assertThat(health.getCpuMetricsVersion()).isNull();
    }

    @Test
    void toleratesSmallSamplingOvershootButRetainsRawSource() {
        String output = structuredOutput(runningInspect("{}"), "{\"CPUPerc\":\"201%\"}", true)
                .replace("__MCMP_HOST_CPUS__=1", "__MCMP_HOST_CPUS__=2");
        when(commandExecutor.execute(eq(TARGET), anyString()))
                .thenReturn(new DockerCommandResult(0, output, ""));

        ContainerHealthInfo health = collector.collectContainerStats(TARGET, CONTAINER_ID);

        assertThat(health.getCpuUsage()).isEqualTo(100.0);
        assertThat(health.getRawCpuUsage()).isEqualTo(201.0);
        assertThat(health.getCpuCapacityCores()).isEqualTo(2.0);
        assertThat(health.getCpuMetricsVersion()).isEqualTo(1);
    }

    @Test
    void probesCapacityWithoutContainerExecOrCgroupMutation() throws Exception {
        when(commandExecutor.execute(eq(TARGET), anyString()))
                .thenReturn(new DockerCommandResult(0,
                        structuredOutput(runningInspect("{}"), "{\"CPUPerc\":\"0%\"}", false), ""));

        collector.collectContainerStats(TARGET, CONTAINER_ID);

        ArgumentCaptor<String> script = ArgumentCaptor.forClass(String.class);
        Mockito.verify(commandExecutor).execute(eq(TARGET), script.capture());
        assertThat(script.getValue()).contains("docker info --format '{{.NCPU}}'",
                "Cpus_allowed_list", "cpu.max", "cpu.cfs_quota_us");
        assertThat(script.getValue()).doesNotContain("docker exec", "docker update", "tee ", "chmod ");
        Process syntaxCheck = new ProcessBuilder("sh", "-n").redirectErrorStream(true).start();
        try (var input = syntaxCheck.getOutputStream()) {
            input.write(script.getValue().getBytes(StandardCharsets.UTF_8));
        }
        String errors = new String(syntaxCheck.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(syntaxCheck.waitFor()).as(errors).isZero();
    }

    private String runningInspect(String hostConfig) {
        return "[{\"HostConfig\":" + hostConfig
                + ",\"State\":{\"Status\":\"running\",\"Running\":true},\"NetworkSettings\":{\"Ports\":{}}}]";
    }

    private String structuredOutput(String inspect, String stats, boolean portAccessible) {
        return "__MCMP_INSPECT_BEGIN__\n"
                + inspect.strip() + "\n"
                + "__MCMP_INSPECT_END__\n"
                + "__MCMP_HOST_CPUS__=1\n"
                + "__MCMP_ALLOWED_CPUS__=0-255\n"
                + "__MCMP_RUNTIME_CPU_PROBE_OK__=true\n"
                + "__MCMP_STATS_BEGIN__\n"
                + stats.strip() + "\n"
                + "__MCMP_STATS_END__\n"
                + "__MCMP_PORT_ACCESS__=" + portAccessible;
    }
}
