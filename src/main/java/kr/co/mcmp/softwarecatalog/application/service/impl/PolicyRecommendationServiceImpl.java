package kr.co.mcmp.softwarecatalog.application.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.co.mcmp.softwarecatalog.application.dto.OperationProfileAnalysisDTO;
import kr.co.mcmp.softwarecatalog.application.dto.PolicyRecommendationDTO;
import kr.co.mcmp.softwarecatalog.application.model.DailyMetricsSummary;
import kr.co.mcmp.softwarecatalog.application.model.DeploymentHistory;
import kr.co.mcmp.softwarecatalog.application.model.InfraSpecSnapshot;
import kr.co.mcmp.softwarecatalog.application.model.OperationProfileAnalysis;
import kr.co.mcmp.softwarecatalog.application.model.PolicyRecommendation;
import kr.co.mcmp.softwarecatalog.application.model.UnifiedLog;
import kr.co.mcmp.softwarecatalog.application.repository.AbnormalEventRepository;
import kr.co.mcmp.softwarecatalog.application.repository.DailyMetricsSummaryRepository;
import kr.co.mcmp.softwarecatalog.application.repository.DeploymentHistoryRepository;
import kr.co.mcmp.softwarecatalog.application.repository.InfraSpecSnapshotRepository;
import kr.co.mcmp.softwarecatalog.application.repository.OperationProfileAnalysisRepository;
import kr.co.mcmp.softwarecatalog.application.repository.PolicyRecommendationRepository;
import kr.co.mcmp.softwarecatalog.application.repository.UnifiedLogRepository;
import kr.co.mcmp.softwarecatalog.application.service.PolicyRecommendationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PolicyRecommendationServiceImpl implements PolicyRecommendationService {

    private static final int DEFAULT_DAYS = 14;
    private static final int MIN_VALID_DAYS = 7;
    private static final int VALID_SAMPLE_COUNT = 72;

    private static final String RESOURCE_CPU = "CPU_INTENSIVE";
    private static final String RESOURCE_MEMORY = "MEMORY_INTENSIVE";
    private static final String RESOURCE_CPU_MEMORY = "CPU_MEMORY_INTENSIVE";
    private static final String RESOURCE_GENERAL = "GENERAL_PURPOSE";

    private static final String RECOMMENDATION_STATUS = "RECOMMENDED";
    private static final List<Integer> STANDARD_ANALYSIS_DAYS = List.of(90, 30, 7);

    private final DailyMetricsSummaryRepository dailyMetricsSummaryRepository;
    private final AbnormalEventRepository abnormalEventRepository;
    private final InfraSpecSnapshotRepository infraSpecSnapshotRepository;
    private final DeploymentHistoryRepository deploymentHistoryRepository;
    private final OperationProfileAnalysisRepository operationProfileAnalysisRepository;
    private final PolicyRecommendationRepository policyRecommendationRepository;
    private final UnifiedLogRepository unifiedLogRepository;

    @Override
    @Transactional
    public OperationProfileAnalysisDTO analyze(Long deploymentId, Integer days) {
        int analysisDays = normalizeDays(days);
        LocalDate endDate = LocalDate.now().minusDays(1);
        LocalDate startDate = endDate.minusDays(analysisDays - 1L);

        DeploymentHistory deployment = deploymentHistoryRepository.findById(deploymentId)
                .orElseThrow(() -> new IllegalArgumentException("Deployment not found: " + deploymentId));
        Optional<InfraSpecSnapshot> spec = infraSpecSnapshotRepository.findByDeploymentId(deploymentId);
        List<DailyMetricsSummary> summaries = dailyMetricsSummaryRepository
                .findByDeploymentIdAndSummaryDateBetweenOrderBySummaryDateAsc(deploymentId, startDate, endDate);

        AnalysisResult result = buildAnalysis(deploymentId, deployment, spec.orElse(null), summaries, startDate, endDate, analysisDays);

        OperationProfileAnalysis analysis = operationProfileAnalysisRepository.save(OperationProfileAnalysis.builder()
                .deploymentId(deploymentId)
                .analysisStartDate(startDate)
                .analysisEndDate(endDate)
                .selectedResourceType(result.selectedResourceType)
                .recommendedResourceType(result.recommendedResourceType)
                .cpuSizingStatus(result.cpuSizingStatus)
                .memorySizingStatus(result.memorySizingStatus)
                .dataStatus(result.dataStatus)
                .validDays(result.validDays)
                .missingDays(result.missingDays)
                .sampleCount(result.sampleCount)
                .cpuPressureP95(result.cpuPressureP95)
                .memoryPressureP95(result.memoryPressureP95)
                .maxCpuPressure(result.maxCpuPressure)
                .maxMemoryPressure(result.maxMemoryPressure)
                .oomCount(result.oomCount)
                .restartCount(result.restartCount)
                .crashLoopCount(result.crashLoopCount)
                .confidence(result.confidence)
                .reasons(toJsonArray(result.reasons))
                .createdAt(LocalDateTime.now())
                .build());

        policyRecommendationRepository.save(PolicyRecommendation.builder()
                .deploymentId(deploymentId)
                .analysisId(analysis.getId())
                .selectedResourceType(result.selectedResourceType)
                .recommendedResourceType(result.recommendedResourceType)
                .mismatch(!safeEquals(result.selectedResourceType, result.recommendedResourceType))
                .actions(String.join(",", result.actions))
                .confidence(result.confidence)
                .message(result.message)
                .recommendationStatus(RECOMMENDATION_STATUS)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        log.info("Policy recommendation analysis saved: deploymentId={}, analysisId={}, dataStatus={}, recommended={}",
                deploymentId, analysis.getId(), result.dataStatus, result.recommendedResourceType);

        return OperationProfileAnalysisDTO.from(analysis);
    }

    @Override
    @Transactional
    public List<OperationProfileAnalysisDTO> analyzeStandardPeriods(Long deploymentId) {
        List<OperationProfileAnalysisDTO> results = new ArrayList<>();
        for (Integer days : STANDARD_ANALYSIS_DAYS) {
            results.add(analyze(deploymentId, days));
        }
        return results;
    }

    @Override
    @Transactional(readOnly = true)
    public OperationProfileAnalysisDTO getLatestAnalysis(Long deploymentId) {
        return operationProfileAnalysisRepository.findTopByDeploymentIdOrderByCreatedAtDescIdDesc(deploymentId)
                .map(OperationProfileAnalysisDTO::from)
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public OperationProfileAnalysisDTO getAnalysis(Long deploymentId, Integer days) {
        int analysisDays = normalizeDays(days);
        LocalDate endDate = LocalDate.now().minusDays(1);
        LocalDate startDate = endDate.minusDays(analysisDays - 1L);
        return operationProfileAnalysisRepository
                .findTopByDeploymentIdAndAnalysisStartDateAndAnalysisEndDateOrderByCreatedAtDescIdDesc(
                        deploymentId, startDate, endDate)
                .map(OperationProfileAnalysisDTO::from)
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public PolicyRecommendationDTO getLatestRecommendation(Long deploymentId) {
        return policyRecommendationRepository.findTopByDeploymentIdOrderByCreatedAtDescIdDesc(deploymentId)
                .map(PolicyRecommendationDTO::from)
                .orElse(null);
    }

    private AnalysisResult buildAnalysis(
            Long deploymentId,
            DeploymentHistory deployment,
            InfraSpecSnapshot spec,
            List<DailyMetricsSummary> summaries,
            LocalDate startDate,
            LocalDate endDate,
            int analysisDays) {
        AnalysisResult result = new AnalysisResult();
        result.selectedResourceType = resolveSelectedResourceType(deployment, spec, summaries);
        result.validDays = (int) summaries.stream().filter(this::isValidDay).count();
        result.missingDays = Math.max(analysisDays - result.validDays, 0);
        result.sampleCount = summaries.stream().mapToInt(s -> value(s.getSampleCount())).sum();

        result.cpuPressureP95 = average(summaries.stream()
                .map(DailyMetricsSummary::getP95CpuPct)
                .collect(Collectors.toList()));
        result.memoryPressureP95 = average(summaries.stream()
                .map(DailyMetricsSummary::getP95MemoryPct)
                .collect(Collectors.toList()));
        result.maxCpuPressure = max(summaries.stream()
                .map(DailyMetricsSummary::getMaxCpuPct)
                .collect(Collectors.toList()));
        result.maxMemoryPressure = max(summaries.stream()
                .map(DailyMetricsSummary::getMaxMemoryPct)
                .collect(Collectors.toList()));

        LocalDateTime eventStart = startDate.atStartOfDay();
        LocalDateTime eventEnd = endDate.atTime(23, 59, 59);
        int summaryOom = summaries.stream().mapToInt(s -> value(s.getOomCount())).sum();
        int summaryRestart = summaries.stream().mapToInt(s -> value(s.getRestartCount())).sum();
        int summaryCrashLoop = summaries.stream().mapToInt(s -> value(s.getCrashLoopCount())).sum();
        result.oomCount = Math.max(summaryOom, (int) abnormalEventRepository
                .countByDeploymentIdAndEventTypeAndOccurredAtBetween(deploymentId, "OOM_KILLED", eventStart, eventEnd));
        result.restartCount = Math.max(summaryRestart, (int) abnormalEventRepository
                .countByDeploymentIdAndEventTypeAndOccurredAtBetween(deploymentId, "RESTART", eventStart, eventEnd));
        result.crashLoopCount = Math.max(summaryCrashLoop, (int) abnormalEventRepository
                .countByDeploymentIdAndEventTypeAndOccurredAtBetween(deploymentId, "CRASH_LOOP", eventStart, eventEnd));
        result.maxNetworkInBytes = maxLong(summaries.stream()
                .map(DailyMetricsSummary::getMaxNetworkInBytes)
                .collect(Collectors.toList()));
        result.maxNetworkOutBytes = maxLong(summaries.stream()
                .map(DailyMetricsSummary::getMaxNetworkOutBytes)
                .collect(Collectors.toList()));
        result.errorLogCount = toInt(unifiedLogRepository.countByDeploymentIdAndSeverityAndLoggedAtBetween(
                deploymentId, UnifiedLog.LogSeverity.ERROR, eventStart, eventEnd));
        result.oomLogCount = toInt(
                unifiedLogRepository.countByDeploymentIdAndMessageContainingBetween(deploymentId, "outofmemory", eventStart, eventEnd)
                        + unifiedLogRepository.countByDeploymentIdAndMessageContainingBetween(deploymentId, "oom", eventStart, eventEnd)
                        + unifiedLogRepository.countByDeploymentIdAndMessageContainingBetween(deploymentId, "heap", eventStart, eventEnd));
        result.timeoutLogCount = toInt(
                unifiedLogRepository.countByDeploymentIdAndMessageContainingBetween(deploymentId, "timeout", eventStart, eventEnd)
                        + unifiedLogRepository.countByDeploymentIdAndMessageContainingBetween(deploymentId, "refused", eventStart, eventEnd)
                        + unifiedLogRepository.countByDeploymentIdAndMessageContainingBetween(deploymentId, "dns", eventStart, eventEnd));

        result.dataStatus = resolveDataStatus(result.validDays, result.missingDays, analysisDays);
        result.cpuSizingStatus = resolveSizingStatus(result.cpuPressureP95, result.maxCpuPressure, false);
        result.memorySizingStatus = resolveSizingStatus(result.memoryPressureP95, result.maxMemoryPressure, result.oomCount > 0);
        applyRecommendation(result, deployment);
        appendSupportingEvidence(result);
        return result;
    }

    private void applyRecommendation(AnalysisResult result, DeploymentHistory deployment) {
        boolean insufficient = "INSUFFICIENT_DATA".equals(result.dataStatus);
        boolean memoryUnder = "UNDER_PROVISIONED".equals(result.memorySizingStatus);
        boolean cpuUnder = "UNDER_PROVISIONED".equals(result.cpuSizingStatus);
        boolean lowLoad = isBelow(result.cpuPressureP95, 40.0) && isBelow(result.memoryPressureP95, 40.0)
                && result.oomCount == 0 && result.crashLoopCount == 0;

        if (insufficient) {
            result.recommendedResourceType = result.selectedResourceType;
            result.actions.add("NO_ACTION");
            result.confidence = 0.0;
            result.message = "데이터가 부족하여 정책추천을 보류합니다.";
            result.reasons.add("validDays=" + result.validDays + " is below " + MIN_VALID_DAYS);
            return;
        }

        if (cpuUnder && memoryUnder) {
            result.recommendedResourceType = RESOURCE_CPU_MEMORY;
            result.actions.add("REVIEW_CPU_MEMORY_POLICY");
            result.actions.add("CHANGE_RESOURCE_TYPE");
            result.message = "CPU와 Memory 사용 특성이 모두 강해 복합 운영 정책 검토를 권고합니다.";
            result.reasons.add("cpuPressureP95=" + format(result.cpuPressureP95));
            result.reasons.add("memoryPressureP95=" + format(result.memoryPressureP95));
            if (result.oomCount > 0) {
                result.reasons.add("oomCount=" + result.oomCount);
            }
        } else if (memoryUnder && (result.oomCount > 0 || result.oomLogCount > 0
                || safeDouble(result.memoryPressureP95) >= safeDouble(result.cpuPressureP95))) {
            result.recommendedResourceType = RESOURCE_MEMORY;
            result.actions.add("INCREASE_MEMORY");
            result.actions.add("CHANGE_RESOURCE_TYPE");
            result.message = "Memory 사용률 또는 OOM 이벤트 기준으로 Memory 중심 유형을 권고합니다.";
            result.reasons.add("memoryPressureP95=" + format(result.memoryPressureP95));
            if (result.oomCount > 0) {
                result.reasons.add("oomCount=" + result.oomCount);
            }
        } else if (cpuUnder) {
            result.recommendedResourceType = RESOURCE_CPU;
            result.actions.add("INCREASE_CPU");
            result.actions.add("CHANGE_RESOURCE_TYPE");
            result.message = "CPU 사용률 기준으로 CPU 중심 유형을 권고합니다.";
            result.reasons.add("cpuPressureP95=" + format(result.cpuPressureP95));
        } else if (lowLoad) {
            result.recommendedResourceType = RESOURCE_GENERAL;
            result.actions.add("DOWNSIZE");
            result.message = "CPU/Memory 사용률이 낮아 범용 유형 또는 하향 조정을 권고합니다.";
            result.reasons.add("cpuPressureP95 and memoryPressureP95 are below 40");
        } else {
            result.recommendedResourceType = result.selectedResourceType != null ? result.selectedResourceType : RESOURCE_GENERAL;
            result.actions.add("NO_ACTION");
            result.message = "현재 resourceType 유지가 적절합니다.";
            result.reasons.add("CPU/Memory pressure is within right-sized range");
        }

        if (result.restartCount >= 5 || result.crashLoopCount > 0) {
            result.actions.add("INVESTIGATE_STABILITY");
            result.reasons.add("restartCount=" + result.restartCount + ", crashLoopCount=" + result.crashLoopCount);
        }

        result.confidence = resolveConfidence(result, deployment);
    }

    private void appendSupportingEvidence(AnalysisResult result) {
        if (safeLong(result.maxNetworkInBytes) > 0 || safeLong(result.maxNetworkOutBytes) > 0) {
            result.reasons.add("networkEvidence=maxInBytes=" + safeLong(result.maxNetworkInBytes)
                    + ", maxOutBytes=" + safeLong(result.maxNetworkOutBytes));
        }
        if (result.errorLogCount > 0) {
            result.reasons.add("errorLogCount=" + result.errorLogCount);
        }
        if (result.oomLogCount > 0) {
            result.reasons.add("oomRelatedLogCount=" + result.oomLogCount);
        }
        if (result.timeoutLogCount > 0) {
            result.reasons.add("networkOrTimeoutLogCount=" + result.timeoutLogCount);
            if (!result.actions.contains("INVESTIGATE_STABILITY")) {
                result.actions.add("INVESTIGATE_STABILITY");
            }
        }
    }

    private String resolveSelectedResourceType(
            DeploymentHistory deployment,
            InfraSpecSnapshot spec,
            List<DailyMetricsSummary> summaries) {
        if (spec != null && isNotBlank(spec.getResourceType())) {
            return spec.getResourceType();
        }
        if (isNotBlank(deployment.getResourceType())) {
            return deployment.getResourceType();
        }
        for (int i = summaries.size() - 1; i >= 0; i--) {
            if (isNotBlank(summaries.get(i).getResourceType())) {
                return summaries.get(i).getResourceType();
            }
        }
        return RESOURCE_GENERAL;
    }

    private String resolveDataStatus(int validDays, int missingDays, int analysisDays) {
        if (validDays < MIN_VALID_DAYS) {
            return "INSUFFICIENT_DATA";
        }
        if (missingDays == 0 && validDays >= analysisDays) {
            return "SUFFICIENT";
        }
        return "PARTIAL_DATA";
    }

    private String resolveSizingStatus(Double p95, Double max, boolean hasCriticalEvent) {
        if (hasCriticalEvent || safeDouble(p95) >= 80.0 || safeDouble(max) >= 90.0) {
            return "UNDER_PROVISIONED";
        }
        if (safeDouble(p95) < 40.0 && safeDouble(max) < 60.0) {
            return "OVER_PROVISIONED";
        }
        return "RIGHT_SIZED";
    }

    private double resolveConfidence(AnalysisResult result, DeploymentHistory deployment) {
        double confidence;
        double maxP95 = Math.max(safeDouble(result.cpuPressureP95), safeDouble(result.memoryPressureP95));
        double maxPeak = Math.max(safeDouble(result.maxCpuPressure), safeDouble(result.maxMemoryPressure));

        if ("PARTIAL_DATA".equals(result.dataStatus)) {
            confidence = 0.60;
        } else if (result.oomCount > 0 || maxP95 >= 90.0 || maxPeak >= 95.0) {
            confidence = 0.90;
        } else if (maxP95 >= 80.0 || maxPeak >= 90.0) {
            confidence = 0.78;
        } else {
            confidence = 0.75;
        }

        if (deployment.getDeploymentType() != null && "K8S".equals(deployment.getDeploymentType().name())) {
            confidence = Math.min(confidence, 0.60);
            result.reasons.add("K8s percentage metrics require request/limit normalization; confidence capped at 0.60");
        }
        return confidence;
    }

    private boolean isValidDay(DailyMetricsSummary summary) {
        if (value(summary.getSampleCount()) < VALID_SAMPLE_COUNT) {
            return false;
        }
        if (value(summary.getRunningMinutes()) < 720) {
            return false;
        }
        return summary.getP95CpuPct() != null || summary.getP95MemoryPct() != null;
    }

    private int normalizeDays(Integer days) {
        if (days == null || days <= 0) {
            return DEFAULT_DAYS;
        }
        return Math.max(1, Math.min(days, 90));
    }

    private static int value(Integer value) {
        return value != null ? value : 0;
    }

    private static double safeDouble(Double value) {
        return value != null ? value : 0.0;
    }

    private static long safeLong(Long value) {
        return value != null ? value : 0L;
    }

    private static int toInt(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private static boolean isBelow(Double value, double threshold) {
        return value != null && value < threshold;
    }

    private static Double average(List<Double> values) {
        return values.stream()
                .filter(v -> v != null)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
    }

    private static Double max(List<Double> values) {
        return values.stream()
                .filter(v -> v != null)
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0.0);
    }

    private static Long maxLong(List<Long> values) {
        return values.stream()
                .filter(v -> v != null)
                .mapToLong(Long::longValue)
                .max()
                .orElse(0L);
    }

    private static boolean safeEquals(String left, String right) {
        if (left == null) {
            return right == null;
        }
        return left.equals(right);
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String format(Double value) {
        return String.format(Locale.ROOT, "%.2f", safeDouble(value));
    }

    private static String toJsonArray(List<String> reasons) {
        return reasons.stream()
                .map(reason -> "\"" + reason.replace("\"", "\\\"") + "\"")
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static class AnalysisResult {
        String selectedResourceType;
        String recommendedResourceType;
        String cpuSizingStatus;
        String memorySizingStatus;
        String dataStatus;
        Integer validDays;
        Integer missingDays;
        Integer sampleCount;
        Double cpuPressureP95;
        Double memoryPressureP95;
        Double maxCpuPressure;
        Double maxMemoryPressure;
        Integer oomCount;
        Integer restartCount;
        Integer crashLoopCount;
        Long maxNetworkInBytes;
        Long maxNetworkOutBytes;
        Integer errorLogCount;
        Integer oomLogCount;
        Integer timeoutLogCount;
        Double confidence;
        String message;
        List<String> actions = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
    }
}
