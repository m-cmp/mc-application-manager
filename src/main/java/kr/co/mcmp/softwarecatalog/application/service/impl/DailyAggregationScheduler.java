package kr.co.mcmp.softwarecatalog.application.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import kr.co.mcmp.softwarecatalog.application.model.DailyMetricsSummary;
import kr.co.mcmp.softwarecatalog.application.repository.DailyMetricsSummaryRepository;
import kr.co.mcmp.softwarecatalog.application.repository.DeploymentHistoryRepository;
import kr.co.mcmp.softwarecatalog.application.repository.LifecycleEventRepository;
import kr.co.mcmp.softwarecatalog.application.repository.ResourceMetricsHistoryRepository;
import kr.co.mcmp.softwarecatalog.application.repository.ResourceMetricsHistoryRepository.DailyAggregationProjection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Scheduler that rolls up L1 (10-minute raw) → L2 (daily aggregation) every day at 02:00.
 * Processes all deployment IDs in deployment_history for the previous day.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DailyAggregationScheduler {

    private final ResourceMetricsHistoryRepository metricsRepo;
    private final LifecycleEventRepository eventRepo;
    private final DailyMetricsSummaryRepository summaryRepo;
    private final DeploymentHistoryRepository deploymentRepo;

    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void aggregateDailyMetrics() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDateTime start = yesterday.atStartOfDay();
        LocalDateTime end   = yesterday.atTime(23, 59, 59);

        List<Long> deploymentIds = collectAllDeploymentIds();
        log.info("[DailyAggregation] Starting for date={}, deployments={}", yesterday, deploymentIds.size());

        int saved = 0;
        for (Long deploymentId : deploymentIds) {
            try {
                if (summaryRepo.existsByDeploymentIdAndSummaryDate(deploymentId, yesterday)) {
                    log.debug("[DailyAggregation] Already aggregated: deploymentId={}, date={}", deploymentId, yesterday);
                    continue;
                }

                DailyAggregationProjection proj = metricsRepo.aggregateByDeploymentAndDate(deploymentId, start, end);

                if (proj == null || proj.getSampleCount() == null || proj.getSampleCount() == 0) {
                    log.debug("[DailyAggregation] No metrics data for deploymentId={}, date={}", deploymentId, yesterday);
                    continue;
                }

                int oomCount = (int) eventRepo.countByDeploymentIdAndEventTypeAndOccurredAtBetween(
                        deploymentId, "OOM_KILLED", start, end);
                int restartCount = (int) eventRepo.countByDeploymentIdAndEventTypeAndOccurredAtBetween(
                        deploymentId, "RESTART", start, end);
                int crashLoopCount = (int) eventRepo.countByDeploymentIdAndEventTypeAndOccurredAtBetween(
                        deploymentId, "CRASH_LOOP", start, end);

                DailyMetricsSummary summary = DailyMetricsSummary.builder()
                        .deploymentId(deploymentId)
                        .summaryDate(yesterday)
                        .avgCpuPct(proj.getAvgCpuPct())
                        .maxCpuPct(proj.getMaxCpuPct())
                        .p95CpuPct(proj.getP95CpuPct())
                        .stddevCpu(proj.getStddevCpu())
                        .avgMemoryPct(proj.getAvgMemoryPct())
                        .maxMemoryPct(proj.getMaxMemoryPct())
                        .p95MemoryPct(proj.getP95MemoryPct())
                        .stddevMemory(proj.getStddevMemory())
                        .oomCount(oomCount + (proj.getOomCount() != null ? proj.getOomCount() : 0))
                        .restartCount(restartCount)
                        .crashLoopCount(crashLoopCount)
                        .runningMinutes(proj.getRunningMinutes())
                        .totalMinutes(proj.getTotalMinutes() != null ? proj.getTotalMinutes() : 1440)
                        .sampleCount(proj.getSampleCount())
                        .resourceType(proj.getResourceType())
                        .createdAt(LocalDateTime.now())
                        .build();

                summaryRepo.save(summary);
                saved++;
                log.debug("[DailyAggregation] Saved summary: deploymentId={}, date={}, samples={}",
                        deploymentId, yesterday, proj.getSampleCount());
            } catch (Exception e) {
                log.error("[DailyAggregation] Failed for deploymentId={}, date={}", deploymentId, yesterday, e);
            }
        }

        log.info("[DailyAggregation] Completed: date={}, saved={}/{}", yesterday, saved, deploymentIds.size());
    }

    private List<Long> collectAllDeploymentIds() {
        return deploymentRepo.findAll().stream()
                .map(d -> d.getId())
                .distinct()
                .collect(Collectors.toList());
    }
}
