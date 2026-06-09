package kr.co.mcmp.softwarecatalog.application.service.impl;

import java.time.LocalDate;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import kr.co.mcmp.softwarecatalog.application.repository.DailyMetricsSummaryRepository;
import kr.co.mcmp.softwarecatalog.application.repository.OperationProfileAnalysisRepository;
import kr.co.mcmp.softwarecatalog.application.service.PolicyRecommendationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class PolicyRecommendationScheduler {

    // Run long windows first so the latest recommendation shown by the existing API is the recent 7-day result.
    private static final List<Integer> AUTO_ANALYSIS_DAYS = List.of(90, 30, 7);

    private final DailyMetricsSummaryRepository dailyMetricsSummaryRepository;
    private final OperationProfileAnalysisRepository operationProfileAnalysisRepository;
    private final PolicyRecommendationService policyRecommendationService;

    @Scheduled(cron = "${policy-recommendation.analysis.cron:0 10 2 * * *}")
    public void analyzeRecentOperationProfiles() {
        LocalDate endDate = LocalDate.now().minusDays(1);
        List<Long> deploymentIds = dailyMetricsSummaryRepository.findDistinctDeploymentIdsBySummaryDate(endDate);

        log.info("[PolicyRecommendation] Starting scheduled analysis: endDate={}, periods={}, deployments={}",
                endDate, AUTO_ANALYSIS_DAYS, deploymentIds.size());

        int analyzed = 0;
        int skipped = 0;
        int failed = 0;

        for (Long deploymentId : deploymentIds) {
            for (Integer days : AUTO_ANALYSIS_DAYS) {
                LocalDate startDate = endDate.minusDays(days - 1L);
                try {
                    if (hasAnalysisForWindow(deploymentId, startDate, endDate)) {
                        skipped++;
                        log.debug("[PolicyRecommendation] Already analyzed: deploymentId={}, days={}, window={}~{}",
                                deploymentId, days, startDate, endDate);
                        continue;
                    }

                    policyRecommendationService.analyze(deploymentId, days);
                    analyzed++;
                } catch (Exception e) {
                    failed++;
                    log.error("[PolicyRecommendation] Failed scheduled analysis: deploymentId={}, days={}, window={}~{}",
                            deploymentId, days, startDate, endDate, e);
                }
            }
        }

        log.info("[PolicyRecommendation] Completed scheduled analysis: analyzed={}, skipped={}, failed={}, total={}",
                analyzed, skipped, failed, deploymentIds.size() * AUTO_ANALYSIS_DAYS.size());
    }

    private boolean hasAnalysisForWindow(Long deploymentId, LocalDate startDate, LocalDate endDate) {
        return operationProfileAnalysisRepository.existsByDeploymentIdAndAnalysisStartDateAndAnalysisEndDate(
                deploymentId, startDate, endDate);
    }
}
