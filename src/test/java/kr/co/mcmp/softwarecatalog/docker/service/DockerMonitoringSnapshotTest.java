package kr.co.mcmp.softwarecatalog.docker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import kr.co.mcmp.softwarecatalog.application.model.DeploymentHistory;
import kr.co.mcmp.softwarecatalog.application.model.ResourceMetricsHistory;
import kr.co.mcmp.softwarecatalog.application.repository.ResourceMetricsHistoryRepository;
import kr.co.mcmp.softwarecatalog.docker.model.ContainerHealthInfo;

class DockerMonitoringSnapshotTest {

    private final ResourceMetricsHistoryRepository repository = mock(ResourceMetricsHistoryRepository.class);
    private final DockerMonitoringService service = new DockerMonitoringService(
            null, null, repository, null, null, null, null, null);

    @Test void archivesNormalizedPercentageAndItsOriginalCpuBasisTogether() {
        ContainerHealthInfo health = ContainerHealthInfo.builder()
                .cpuUsage(43.74).rawCpuUsage(87.48).cpuCapacityCores(2.0).cpuMetricsVersion(1)
                .memoryUsage(18.45).memoryBytes(400L).memoryLimitBytes(2000L)
                .networkIn(1000.0).networkOut(500.0).restartCount(0).oomKilled(false)
                .status("RUNNING").build();

        snapshot(health);

        ResourceMetricsHistory saved = savedSnapshot();
        assertThat(saved.getCpuUsagePct()).isEqualTo(43.74);
        assertThat(saved.getCpuRawPct()).isEqualTo(87.48);
        assertThat(saved.getCpuCapacityCores()).isEqualTo(2.0);
        assertThat(saved.getCpuMetricsVersion()).isEqualTo(1);
        assertThat(saved.getDeploymentType()).isEqualTo("DOCKER");
        assertThat(saved.getMemoryUsagePct()).isEqualTo(18.45);
        assertThat(saved.getMemoryBytes()).isEqualTo(400L);
        assertThat(saved.getNetworkInBytes()).isEqualTo(1000L);
        assertThat(saved.getNetworkOutBytes()).isEqualTo(500L);
    }

    @Test void unknownCpuCapacityLeavesNormalizedMetricMissingRatherThanZero() {
        snapshot(ContainerHealthInfo.builder().cpuUsage(null).rawCpuUsage(80.0)
                .cpuCapacityCores(null).cpuMetricsVersion(null)
                .memoryUsage(20.0).status("RUNNING").build());

        ResourceMetricsHistory saved = savedSnapshot();
        assertThat(saved.getCpuUsagePct()).isNull();
        assertThat(saved.getCpuRawPct()).isEqualTo(80.0);
        assertThat(saved.getCpuCapacityCores()).isNull();
        assertThat(saved.getCpuMetricsVersion()).isNull();
        assertThat(saved.getMemoryUsagePct()).isEqualTo(20.0);
    }

    @Test void continuesRespectingTenMinuteSnapshotInterval() {
        ContainerHealthInfo health = ContainerHealthInfo.builder()
                .cpuUsage(40.0).rawCpuUsage(80.0).cpuCapacityCores(2.0).cpuMetricsVersion(1)
                .status("RUNNING").build();

        snapshot(health);
        snapshot(health);

        verify(repository, times(1)).save(any(ResourceMetricsHistory.class));
    }

    private void snapshot(ContainerHealthInfo health) {
        DeploymentHistory deployment = DeploymentHistory.builder().id(30L).resourceType("GENERAL_PURPOSE").build();
        ReflectionTestUtils.invokeMethod(service, "saveMetricsSnapshotIfDue", deployment, health);
    }

    private ResourceMetricsHistory savedSnapshot() {
        ArgumentCaptor<ResourceMetricsHistory> saved = ArgumentCaptor.forClass(ResourceMetricsHistory.class);
        verify(repository).save(saved.capture());
        return saved.getValue();
    }
}
