package kr.co.mcmp.softwarecatalog.application.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import kr.co.mcmp.softwarecatalog.application.model.DailyMetricsSummary;
import kr.co.mcmp.softwarecatalog.application.model.DeploymentHistory;
import kr.co.mcmp.softwarecatalog.application.repository.AbnormalEventRepository;
import kr.co.mcmp.softwarecatalog.application.repository.DailyMetricsSummaryRepository;
import kr.co.mcmp.softwarecatalog.application.repository.DeploymentHistoryRepository;
import kr.co.mcmp.softwarecatalog.application.repository.ResourceMetricsHistoryRepository;
import kr.co.mcmp.softwarecatalog.application.repository.ResourceMetricsHistoryRepository.DailyAggregationProjection;

class DailyAggregationSchedulerTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = 1)
    void persistsCpuBasisFromNativeProjectionWithoutInventingOne(Integer cpuVersion) {
        ResourceMetricsHistoryRepository metrics = mock(ResourceMetricsHistoryRepository.class);
        AbnormalEventRepository events = mock(AbnormalEventRepository.class);
        DailyMetricsSummaryRepository summaries = mock(DailyMetricsSummaryRepository.class);
        DeploymentHistoryRepository deployments = mock(DeploymentHistoryRepository.class);
        DailyAggregationProjection projection = mock(DailyAggregationProjection.class);
        when(deployments.findAll()).thenReturn(List.of(DeploymentHistory.builder().id(30L).build()));
        when(metrics.aggregateByDeploymentAndDate(eq(30L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(projection);
        when(projection.getSampleCount()).thenReturn(144);
        when(projection.getRunningMinutes()).thenReturn(1440);
        when(projection.getTotalMinutes()).thenReturn(1440);
        when(projection.getCpuMetricsVersion()).thenReturn(cpuVersion);
        when(projection.getP95CpuPct()).thenReturn(cpuVersion == null ? null : 43.74);
        when(projection.getP95MemoryPct()).thenReturn(18.45);
        when(projection.getMaxNetworkInBytes()).thenReturn(1000L);
        when(projection.getMaxNetworkOutBytes()).thenReturn(500L);
        when(projection.getOomCount()).thenReturn(0);
        when(events.countByDeploymentIdAndEventTypeAndOccurredAtBetween(
                eq(30L), eq("RESTART"), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(1L);

        new DailyAggregationScheduler(metrics, events, summaries, deployments).aggregateDailyMetrics();

        ArgumentCaptor<DailyMetricsSummary> saved = ArgumentCaptor.forClass(DailyMetricsSummary.class);
        verify(summaries).save(saved.capture());
        DailyMetricsSummary summary = saved.getValue();
        assertThat(summary.getCpuMetricsVersion()).isEqualTo(cpuVersion);
        assertThat(summary.getP95CpuPct()).isEqualTo(cpuVersion == null ? null : 43.74);
        assertThat(summary.getSummaryDate()).isEqualTo(LocalDate.now().minusDays(1));
        assertThat(summary.getP95MemoryPct()).isEqualTo(18.45);
        assertThat(summary.getMaxNetworkInBytes()).isEqualTo(1000L);
        assertThat(summary.getMaxNetworkOutBytes()).isEqualTo(500L);
        assertThat(summary.getSampleCount()).isEqualTo(144);
        assertThat(summary.getRunningMinutes()).isEqualTo(1440);
        assertThat(summary.getTotalMinutes()).isEqualTo(1440);
        assertThat(summary.getRestartCount()).isEqualTo(1);
    }
}
