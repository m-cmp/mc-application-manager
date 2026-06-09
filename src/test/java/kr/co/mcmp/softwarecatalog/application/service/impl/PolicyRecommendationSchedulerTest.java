package kr.co.mcmp.softwarecatalog.application.service.impl;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import kr.co.mcmp.softwarecatalog.application.repository.DailyMetricsSummaryRepository;
import kr.co.mcmp.softwarecatalog.application.repository.OperationProfileAnalysisRepository;
import kr.co.mcmp.softwarecatalog.application.service.PolicyRecommendationService;

@ExtendWith(MockitoExtension.class)
class PolicyRecommendationSchedulerTest {

    @Mock
    private DailyMetricsSummaryRepository dailyMetricsSummaryRepository;

    @Mock
    private OperationProfileAnalysisRepository operationProfileAnalysisRepository;

    @Mock
    private PolicyRecommendationService policyRecommendationService;

    private PolicyRecommendationScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new PolicyRecommendationScheduler(
                dailyMetricsSummaryRepository,
                operationProfileAnalysisRepository,
                policyRecommendationService);
    }

    @Test
    void analyzeRecentOperationProfilesRunsSevenThirtyNinetyDayAnalysisForDeploymentsWithYesterdaySummary() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        when(dailyMetricsSummaryRepository.findDistinctDeploymentIdsBySummaryDate(yesterday))
                .thenReturn(List.of(10L, 20L));

        scheduler.analyzeRecentOperationProfiles();

        verify(policyRecommendationService).analyze(10L, 7);
        verify(policyRecommendationService).analyze(10L, 30);
        verify(policyRecommendationService).analyze(10L, 90);
        verify(policyRecommendationService).analyze(20L, 7);
        verify(policyRecommendationService).analyze(20L, 30);
        verify(policyRecommendationService).analyze(20L, 90);
    }

    @Test
    void analyzeRecentOperationProfilesSkipsDeploymentsAlreadyAnalyzedForSameWindow() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDate startDate = yesterday.minusDays(6);
        when(dailyMetricsSummaryRepository.findDistinctDeploymentIdsBySummaryDate(yesterday))
                .thenReturn(List.of(10L, 20L));
        when(operationProfileAnalysisRepository.existsByDeploymentIdAndAnalysisStartDateAndAnalysisEndDate(
                eq(10L), any(LocalDate.class), eq(yesterday))).thenReturn(false);
        when(operationProfileAnalysisRepository.existsByDeploymentIdAndAnalysisStartDateAndAnalysisEndDate(
                10L, startDate, yesterday)).thenReturn(true);

        scheduler.analyzeRecentOperationProfiles();

        verify(policyRecommendationService, never()).analyze(10L, 7);
        verify(policyRecommendationService).analyze(10L, 30);
        verify(policyRecommendationService).analyze(10L, 90);
        verify(policyRecommendationService).analyze(20L, 7);
        verify(policyRecommendationService).analyze(20L, 30);
        verify(policyRecommendationService).analyze(20L, 90);
    }

    @Test
    void analyzeRecentOperationProfilesDoesNothingWhenNoYesterdaySummaryExists() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        when(dailyMetricsSummaryRepository.findDistinctDeploymentIdsBySummaryDate(yesterday))
                .thenReturn(List.of());

        scheduler.analyzeRecentOperationProfiles();

        verify(policyRecommendationService, never()).analyze(10L, 7);
        verify(policyRecommendationService, never()).analyze(10L, 30);
        verify(policyRecommendationService, never()).analyze(10L, 90);
    }
}
