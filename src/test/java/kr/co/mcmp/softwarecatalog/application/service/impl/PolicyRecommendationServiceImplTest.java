package kr.co.mcmp.softwarecatalog.application.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import kr.co.mcmp.softwarecatalog.application.constants.DeploymentType;
import kr.co.mcmp.softwarecatalog.application.dto.OperationProfileAnalysisDTO;
import kr.co.mcmp.softwarecatalog.application.model.DailyMetricsSummary;
import kr.co.mcmp.softwarecatalog.application.model.DeploymentHistory;
import kr.co.mcmp.softwarecatalog.application.model.InfraSpecSnapshot;
import kr.co.mcmp.softwarecatalog.application.model.OperationProfileAnalysis;
import kr.co.mcmp.softwarecatalog.application.model.PolicyRecommendation;
import kr.co.mcmp.softwarecatalog.application.repository.AbnormalEventRepository;
import kr.co.mcmp.softwarecatalog.application.repository.DailyMetricsSummaryRepository;
import kr.co.mcmp.softwarecatalog.application.repository.DeploymentHistoryRepository;
import kr.co.mcmp.softwarecatalog.application.repository.InfraSpecSnapshotRepository;
import kr.co.mcmp.softwarecatalog.application.repository.OperationProfileAnalysisRepository;
import kr.co.mcmp.softwarecatalog.application.repository.PolicyRecommendationRepository;
import kr.co.mcmp.softwarecatalog.application.repository.UnifiedLogRepository;

@ExtendWith(MockitoExtension.class)
class PolicyRecommendationServiceImplTest {

    @Mock
    private DailyMetricsSummaryRepository dailyMetricsSummaryRepository;

    @Mock
    private AbnormalEventRepository abnormalEventRepository;

    @Mock
    private InfraSpecSnapshotRepository infraSpecSnapshotRepository;

    @Mock
    private DeploymentHistoryRepository deploymentHistoryRepository;

    @Mock
    private OperationProfileAnalysisRepository operationProfileAnalysisRepository;

    @Mock
    private PolicyRecommendationRepository policyRecommendationRepository;

    @Mock
    private UnifiedLogRepository unifiedLogRepository;

    @Captor
    private ArgumentCaptor<PolicyRecommendation> recommendationCaptor;

    private PolicyRecommendationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PolicyRecommendationServiceImpl(
                dailyMetricsSummaryRepository,
                abnormalEventRepository,
                infraSpecSnapshotRepository,
                deploymentHistoryRepository,
                operationProfileAnalysisRepository,
                policyRecommendationRepository,
                unifiedLogRepository);

        when(operationProfileAnalysisRepository.save(any(OperationProfileAnalysis.class)))
                .thenAnswer(invocation -> {
                    OperationProfileAnalysis analysis = invocation.getArgument(0);
                    analysis.setId(100L);
                    return analysis;
                });
        when(policyRecommendationRepository.save(recommendationCaptor.capture()))
                .thenAnswer(invocation -> {
                    PolicyRecommendation recommendation = invocation.getArgument(0);
                    recommendation.setId(200L);
                    return recommendation;
                });
    }

    @Test
    void analyzeRecommendsCpuIntensiveForTomcatCpuPressureSeed() {
        long deploymentId = 1L;
        mockDeployment(deploymentId, DeploymentType.VM, "GENERAL_PURPOSE");
        mockSpec(deploymentId, "GENERAL_PURPOSE", "VM");
        mockSummaries(deploymentId, summaries("GENERAL_PURPOSE", 7, 90.0, 96.0, 42.0, 48.0, 0, 0, 0, 144));
        mockEventCounts(0, 0, 0);

        OperationProfileAnalysisDTO analysis = service.analyze(deploymentId, 7);
        PolicyRecommendation recommendation = recommendationCaptor.getValue();

        assertThat(analysis.getRecommendedResourceType()).isEqualTo("CPU_INTENSIVE");
        assertThat(analysis.getDataStatus()).isEqualTo("SUFFICIENT");
        assertThat(recommendation.getRecommendedResourceType()).isEqualTo("CPU_INTENSIVE");
        assertThat(recommendation.getActions()).contains("INCREASE_CPU").contains("CHANGE_RESOURCE_TYPE");
        assertThat(recommendation.getConfidence()).isGreaterThanOrEqualTo(0.70);
    }

    @Test
    void analyzeRecommendsMemoryIntensiveForRedisMemoryPressureAndOomSeed() {
        long deploymentId = 2L;
        mockDeployment(deploymentId, DeploymentType.VM, "GENERAL_PURPOSE");
        mockSpec(deploymentId, "GENERAL_PURPOSE", "VM");
        mockSummaries(deploymentId, summaries("GENERAL_PURPOSE", 7, 44.0, 52.0, 92.0, 98.0, 1, 2, 0, 144));
        mockEventCounts(1, 0, 0);

        OperationProfileAnalysisDTO analysis = service.analyze(deploymentId, 7);
        PolicyRecommendation recommendation = recommendationCaptor.getValue();

        assertThat(analysis.getRecommendedResourceType()).isEqualTo("MEMORY_INTENSIVE");
        assertThat(analysis.getOomCount()).isGreaterThan(0);
        assertThat(recommendation.getRecommendedResourceType()).isEqualTo("MEMORY_INTENSIVE");
        assertThat(recommendation.getActions()).contains("INCREASE_MEMORY").contains("CHANGE_RESOURCE_TYPE");
        assertThat(recommendation.getConfidence()).isGreaterThanOrEqualTo(0.80);
    }

    @Test
    void analyzeCapsK8sMismatchConfidenceForElasticsearchMemoryPressureSeed() {
        long deploymentId = 3L;
        mockDeployment(deploymentId, DeploymentType.K8S, "CPU_INTENSIVE");
        mockSpec(deploymentId, "CPU_INTENSIVE", "K8S");
        mockSummaries(deploymentId, summaries("CPU_INTENSIVE", 7, 50.0, 58.0, 91.0, 97.0, 1, 2, 0, 144));
        mockEventCounts(1, 0, 0);

        OperationProfileAnalysisDTO analysis = service.analyze(deploymentId, 7);
        PolicyRecommendation recommendation = recommendationCaptor.getValue();

        assertThat(analysis.getSelectedResourceType()).isEqualTo("CPU_INTENSIVE");
        assertThat(analysis.getRecommendedResourceType()).isEqualTo("MEMORY_INTENSIVE");
        assertThat(recommendation.getMismatch()).isTrue();
        assertThat(recommendation.getConfidence()).isEqualTo(0.60);
    }

    @Test
    void analyzeStoresNoActionForGrafanaInsufficientDataSeed() {
        long deploymentId = 4L;
        mockDeployment(deploymentId, DeploymentType.K8S, "GENERAL_PURPOSE");
        mockSpec(deploymentId, "GENERAL_PURPOSE", "K8S");
        mockSummaries(deploymentId, summaries("GENERAL_PURPOSE", 3, 45.0, 52.0, 44.0, 49.0, 0, 0, 0, 45));
        mockEventCounts(0, 0, 0);

        OperationProfileAnalysisDTO analysis = service.analyze(deploymentId, 7);
        PolicyRecommendation recommendation = recommendationCaptor.getValue();

        assertThat(analysis.getDataStatus()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(analysis.getConfidence()).isEqualTo(0.0);
        assertThat(recommendation.getActions()).isEqualTo("NO_ACTION");
        assertThat(recommendation.getConfidence()).isEqualTo(0.0);
    }

    @ParameterizedTest
    @CsvSource({
            "1, 87.48, 89.0, UNDER_PROVISIONED, CPU_INTENSIVE, 0.78",
            "2, 43.74, 44.5, RIGHT_SIZED, GENERAL_PURPOSE, 0.75",
            "4, 21.87, 22.25, OVER_PROVISIONED, GENERAL_PURPOSE, 0.75"
    })
    void analyzeUsesArchivedNormalizedPercentagesWithoutDividingByCurrentCpuCount(
            int availableCpuCount, double normalizedP95, double normalizedMax,
            String sizingStatus, String resourceType, double confidence) {
        long deploymentId = 5L;
        mockDeployment(deploymentId, DeploymentType.VM, "GENERAL_PURPOSE");
        // Collection already normalized the same 87.48% Docker measurement.
        // The policy layer must not divide it again, even if a current spec exists.
        assertThat(normalizedP95).isEqualTo(87.48 / availableCpuCount);
        mockSpec(deploymentId, "GENERAL_PURPOSE", "VM").setVmCpuCores(8);
        mockSummaries(deploymentId, summaries("GENERAL_PURPOSE", 7,
                normalizedP95, normalizedMax, 48.91, 53.0, 0, 0, 0, 144));
        mockEventCounts(0, 0, 0);

        OperationProfileAnalysisDTO analysis = service.analyze(deploymentId, 7);

        assertThat(analysis.getCpuPressureP95()).isCloseTo(normalizedP95, org.assertj.core.data.Offset.offset(0.00001));
        assertThat(analysis.getCpuSizingStatus()).isEqualTo(sizingStatus);
        assertThat(analysis.getMemorySizingStatus()).isEqualTo("RIGHT_SIZED");
        assertThat(analysis.getRecommendedResourceType()).isEqualTo(resourceType);
        assertThat(analysis.getDataStatus()).isEqualTo("SUFFICIENT");
        assertThat(analysis.getConfidence()).isEqualTo(confidence);
        assertThat(recommendationCaptor.getValue().getActions()).doesNotContain("DOWNSIZE");
        assertThat(recommendationCaptor.getValue().getMismatch()).isEqualTo(!"GENERAL_PURPOSE".equals(resourceType));
    }

    @Test
    void analyzeDoesNotInterpretLegacyVmCpuAsCapacityPercentageOrZeroLoad() {
        long deploymentId = 6L;
        mockDeployment(deploymentId, DeploymentType.VM, "GENERAL_PURPOSE");
        mockSpec(deploymentId, "GENERAL_PURPOSE", "VM");
        List<DailyMetricsSummary> rows = summaries("GENERAL_PURPOSE", 7, 187.0, 200.0, 20.0, 30.0, 0, 0, 0, 144);
        rows.forEach(row -> row.setCpuMetricsVersion(null));
        mockSummaries(deploymentId, rows);
        mockEventCounts(0, 0, 0);

        OperationProfileAnalysisDTO analysis = service.analyze(deploymentId, 7);

        assertThat(analysis.getCpuSizingStatus()).isEqualTo("UNKNOWN");
        assertThat(analysis.getCpuPressureP95()).isNull();
        assertThat(analysis.getMaxCpuPressure()).isNull();
        assertThat(analysis.getDataStatus()).isEqualTo("SUFFICIENT");
        assertThat(analysis.getRecommendedResourceType()).isEqualTo("GENERAL_PURPOSE");
        assertThat(analysis.getConfidence()).isEqualTo(0.60);
        assertThat(analysis.getReasons()).contains("CPU 정규화 이력 0일", "기존 단위");
        assertThat(recommendationCaptor.getValue().getActions()).isEqualTo("NO_ACTION");
        assertThat(recommendationCaptor.getValue().getMessage()).contains("CPU 정책 판단을 보류");
    }

    @Test
    void analyzeRequiresSevenNormalizedCpuDaysEvenWhenOverallCoverageIsSufficient() {
        long deploymentId = 7L;
        mockDeployment(deploymentId, DeploymentType.VM, "GENERAL_PURPOSE");
        mockSpec(deploymentId, "GENERAL_PURPOSE", "VM");
        List<DailyMetricsSummary> rows = summaries("GENERAL_PURPOSE", 7, 88.0, 93.0, 20.0, 30.0, 0, 0, 0, 144);
        rows.get(0).setCpuMetricsVersion(null);
        mockSummaries(deploymentId, rows);
        mockEventCounts(0, 0, 0);

        OperationProfileAnalysisDTO analysis = service.analyze(deploymentId, 7);

        assertThat(analysis.getValidDays()).isEqualTo(7);
        assertThat(analysis.getCpuSizingStatus()).isEqualTo("UNKNOWN");
        assertThat(analysis.getCpuPressureP95()).isNull();
        assertThat(analysis.getConfidence()).isEqualTo(0.60);
        assertThat(recommendationCaptor.getValue().getActions()).isEqualTo("NO_ACTION");
    }

    @Test
    void analyzeIgnoresLegacyOutlierWithSevenKnownDaysAndCapsPartialCpuConfidence() {
        long deploymentId = 8L;
        mockDeployment(deploymentId, DeploymentType.VM, "GENERAL_PURPOSE");
        mockSpec(deploymentId, "GENERAL_PURPOSE", "VM");
        List<DailyMetricsSummary> rows = summaries("GENERAL_PURPOSE", 8, 43.74, 49.0, 48.91, 53.0, 0, 0, 0, 144);
        rows.get(0).setCpuMetricsVersion(null);
        rows.get(0).setP95CpuPct(190.0);
        rows.get(0).setMaxCpuPct(200.0);
        mockSummaries(deploymentId, rows);
        mockEventCounts(0, 0, 0);

        OperationProfileAnalysisDTO analysis = service.analyze(deploymentId, 8);

        assertThat(analysis.getDataStatus()).isEqualTo("SUFFICIENT");
        assertThat(analysis.getCpuPressureP95()).isCloseTo(43.74, org.assertj.core.data.Offset.offset(0.00001));
        assertThat(analysis.getMaxCpuPressure()).isEqualTo(49.0);
        assertThat(analysis.getCpuSizingStatus()).isEqualTo("RIGHT_SIZED");
        assertThat(analysis.getRecommendedResourceType()).isEqualTo("GENERAL_PURPOSE");
        assertThat(analysis.getConfidence()).isEqualTo(0.60);
        assertThat(analysis.getReasons()).contains("CPU 정규화 이력 7일 / 전체 유효 이력 8일");
    }

    @Test
    void analyzeStillRecommendsMemoryWhenVmCpuBasisIsUnknown() {
        long deploymentId = 9L;
        mockDeployment(deploymentId, DeploymentType.VM, "GENERAL_PURPOSE");
        mockSpec(deploymentId, "GENERAL_PURPOSE", "VM");
        List<DailyMetricsSummary> rows = summaries("GENERAL_PURPOSE", 7, 190.0, 200.0, 92.0, 98.0, 0, 0, 0, 144);
        rows.forEach(row -> row.setCpuMetricsVersion(null));
        mockSummaries(deploymentId, rows);
        mockEventCounts(0, 0, 0);

        OperationProfileAnalysisDTO analysis = service.analyze(deploymentId, 7);

        assertThat(analysis.getCpuSizingStatus()).isEqualTo("UNKNOWN");
        assertThat(analysis.getMemorySizingStatus()).isEqualTo("UNDER_PROVISIONED");
        assertThat(analysis.getRecommendedResourceType()).isEqualTo("MEMORY_INTENSIVE");
        assertThat(analysis.getConfidence()).isEqualTo(0.60);
        assertThat(recommendationCaptor.getValue().getActions()).contains("INCREASE_MEMORY").doesNotContain("INCREASE_CPU");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(doubles = {-1.0, 101.0, Double.NaN, Double.POSITIVE_INFINITY})
    void analyzeDoesNotTreatMissingOrInvalidNormalizedCpuAsZeroLoad(Double cpuP95) {
        long deploymentId = 12L;
        mockDeployment(deploymentId, DeploymentType.VM, "GENERAL_PURPOSE");
        mockSpec(deploymentId, "GENERAL_PURPOSE", "VM");
        List<DailyMetricsSummary> rows = summaries("GENERAL_PURPOSE", 7, 20.0, 30.0, 20.0, 30.0, 0, 0, 0, 144);
        rows.forEach(row -> row.setP95CpuPct(cpuP95));
        mockSummaries(deploymentId, rows);
        mockEventCounts(0, 0, 0);

        OperationProfileAnalysisDTO analysis = service.analyze(deploymentId, 7);

        assertThat(analysis.getCpuSizingStatus()).isEqualTo("UNKNOWN");
        assertThat(analysis.getCpuPressureP95()).isNull();
        assertThat(analysis.getConfidence()).isEqualTo(0.60);
        assertThat(recommendationCaptor.getValue().getActions()).isEqualTo("NO_ACTION");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void analyzeRequiresExplicitSupportedCpuMetricsVersion(int version) {
        long deploymentId = 13L;
        mockDeployment(deploymentId, DeploymentType.VM, "GENERAL_PURPOSE");
        mockSpec(deploymentId, "GENERAL_PURPOSE", "VM");
        List<DailyMetricsSummary> rows = summaries("GENERAL_PURPOSE", 7, 88.0, 93.0, 48.91, 53.0, 0, 0, 0, 144);
        rows.forEach(row -> row.setCpuMetricsVersion(version));
        mockSummaries(deploymentId, rows);
        mockEventCounts(0, 0, 0);

        OperationProfileAnalysisDTO analysis = service.analyze(deploymentId, 7);

        assertThat(analysis.getCpuSizingStatus()).isEqualTo("UNKNOWN");
        assertThat(analysis.getRecommendedResourceType()).isEqualTo("GENERAL_PURPOSE");
        assertThat(recommendationCaptor.getValue().getActions()).isEqualTo("NO_ACTION");
    }

    @Test
    void analyzeDoesNotRequireVmNormalizationMarkerForK8s() {
        long deploymentId = 10L;
        mockDeployment(deploymentId, DeploymentType.K8S, "GENERAL_PURPOSE");
        mockSpec(deploymentId, "GENERAL_PURPOSE", "K8S");
        List<DailyMetricsSummary> rows = summaries("GENERAL_PURPOSE", 7, 88.0, 93.0, 48.91, 53.0, 0, 0, 0, 144);
        rows.forEach(row -> row.setCpuMetricsVersion(null));
        mockSummaries(deploymentId, rows);
        mockEventCounts(0, 0, 0);

        OperationProfileAnalysisDTO analysis = service.analyze(deploymentId, 7);

        assertThat(analysis.getCpuSizingStatus()).isEqualTo("UNDER_PROVISIONED");
        assertThat(analysis.getCpuPressureP95()).isEqualTo(88.0);
        assertThat(analysis.getRecommendedResourceType()).isEqualTo("CPU_INTENSIVE");
        assertThat(analysis.getConfidence()).isEqualTo(0.60);
        assertThat(analysis.getReasons()).doesNotContain("VM CPU 정규화 이력");
    }

    @ParameterizedTest
    @CsvSource({"79.99, 89.99, RIGHT_SIZED", "80.0, 89.99, UNDER_PROVISIONED",
            "39.99, 59.99, OVER_PROVISIONED", "39.99, 90.0, UNDER_PROVISIONED"})
    void analyzeAppliesExistingThresholdsToNormalizedVmPercentages(double p95, double max, String expected) {
        long deploymentId = 11L;
        mockDeployment(deploymentId, DeploymentType.VM, "GENERAL_PURPOSE");
        mockSpec(deploymentId, "GENERAL_PURPOSE", "VM");
        mockSummaries(deploymentId, summaries("GENERAL_PURPOSE", 7, p95, max, 48.91, 53.0, 0, 0, 0, 144));
        mockEventCounts(0, 0, 0);

        assertThat(service.analyze(deploymentId, 7).getCpuSizingStatus()).isEqualTo(expected);
    }

    private void mockDeployment(long deploymentId, DeploymentType deploymentType, String resourceType) {
        DeploymentHistory deployment = DeploymentHistory.builder()
                .id(deploymentId)
                .deploymentType(deploymentType)
                .resourceType(resourceType)
                .build();
        when(deploymentHistoryRepository.findById(deploymentId)).thenReturn(Optional.of(deployment));
    }

    private InfraSpecSnapshot mockSpec(long deploymentId, String resourceType, String deploymentType) {
        InfraSpecSnapshot spec = InfraSpecSnapshot.builder()
                .deploymentId(deploymentId)
                .resourceType(resourceType)
                .deploymentType(deploymentType)
                .capturedAt(LocalDateTime.now())
                .build();
        when(infraSpecSnapshotRepository.findByDeploymentId(deploymentId)).thenReturn(Optional.of(spec));
        return spec;
    }

    private void mockSummaries(long deploymentId, List<DailyMetricsSummary> summaries) {
        when(dailyMetricsSummaryRepository.findByDeploymentIdAndSummaryDateBetweenOrderBySummaryDateAsc(
                eq(deploymentId), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(summaries);
    }

    private void mockEventCounts(long oomCount, long restartCount, long crashLoopCount) {
        when(abnormalEventRepository.countByDeploymentIdAndEventTypeAndOccurredAtBetween(
                any(Long.class), anyString(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenAnswer(invocation -> {
                    String eventType = invocation.getArgument(1);
                    if ("OOM_KILLED".equals(eventType)) {
                        return oomCount;
                    }
                    if ("RESTART".equals(eventType)) {
                        return restartCount;
                    }
                    if ("CRASH_LOOP".equals(eventType)) {
                        return crashLoopCount;
                    }
                    return 0L;
                });
    }

    private List<DailyMetricsSummary> summaries(
            String resourceType,
            int days,
            double p95Cpu,
            double maxCpu,
            double p95Memory,
            double maxMemory,
            int oomCount,
            int restartCount,
            int crashLoopCount,
            int sampleCount) {
        LocalDate start = LocalDate.now().minusDays(days);
        return java.util.stream.IntStream.range(0, days)
                .mapToObj(i -> DailyMetricsSummary.builder()
                        .deploymentId(1L)
                        .summaryDate(start.plusDays(i))
                        .avgCpuPct(Math.max(p95Cpu - 12.0, 0.0))
                        .maxCpuPct(maxCpu)
                        .p95CpuPct(p95Cpu)
                        .cpuMetricsVersion(1)
                        .avgMemoryPct(Math.max(p95Memory - 12.0, 0.0))
                        .maxMemoryPct(maxMemory)
                        .p95MemoryPct(p95Memory)
                        .oomCount(oomCount)
                        .restartCount(restartCount)
                        .crashLoopCount(crashLoopCount)
                        .runningMinutes(sampleCount >= 72 ? 1440 : 360)
                        .totalMinutes(1440)
                        .sampleCount(sampleCount)
                        .resourceType(resourceType)
                        .createdAt(LocalDateTime.now())
                        .build())
                .toList();
    }
}
