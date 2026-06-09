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

    private void mockDeployment(long deploymentId, DeploymentType deploymentType, String resourceType) {
        DeploymentHistory deployment = DeploymentHistory.builder()
                .id(deploymentId)
                .deploymentType(deploymentType)
                .resourceType(resourceType)
                .build();
        when(deploymentHistoryRepository.findById(deploymentId)).thenReturn(Optional.of(deployment));
    }

    private void mockSpec(long deploymentId, String resourceType, String deploymentType) {
        InfraSpecSnapshot spec = InfraSpecSnapshot.builder()
                .deploymentId(deploymentId)
                .resourceType(resourceType)
                .deploymentType(deploymentType)
                .capturedAt(LocalDateTime.now())
                .build();
        when(infraSpecSnapshotRepository.findByDeploymentId(deploymentId)).thenReturn(Optional.of(spec));
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
