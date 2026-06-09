package kr.co.mcmp.softwarecatalog.application.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import kr.co.mcmp.softwarecatalog.application.model.OperationProfileAnalysis;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class OperationProfileAnalysisDTO {

    private Long id;
    private Long deploymentId;
    private LocalDate analysisStartDate;
    private LocalDate analysisEndDate;
    private String selectedResourceType;
    private String recommendedResourceType;
    private String cpuSizingStatus;
    private String memorySizingStatus;
    private String dataStatus;
    private Integer validDays;
    private Integer missingDays;
    private Integer sampleCount;
    private Double cpuPressureP95;
    private Double memoryPressureP95;
    private Double maxCpuPressure;
    private Double maxMemoryPressure;
    private Integer oomCount;
    private Integer restartCount;
    private Integer crashLoopCount;
    private Double confidence;
    private String reasons;
    private LocalDateTime createdAt;

    public static OperationProfileAnalysisDTO from(OperationProfileAnalysis analysis) {
        if (analysis == null) {
            return null;
        }
        return OperationProfileAnalysisDTO.builder()
                .id(analysis.getId())
                .deploymentId(analysis.getDeploymentId())
                .analysisStartDate(analysis.getAnalysisStartDate())
                .analysisEndDate(analysis.getAnalysisEndDate())
                .selectedResourceType(analysis.getSelectedResourceType())
                .recommendedResourceType(analysis.getRecommendedResourceType())
                .cpuSizingStatus(analysis.getCpuSizingStatus())
                .memorySizingStatus(analysis.getMemorySizingStatus())
                .dataStatus(analysis.getDataStatus())
                .validDays(analysis.getValidDays())
                .missingDays(analysis.getMissingDays())
                .sampleCount(analysis.getSampleCount())
                .cpuPressureP95(analysis.getCpuPressureP95())
                .memoryPressureP95(analysis.getMemoryPressureP95())
                .maxCpuPressure(analysis.getMaxCpuPressure())
                .maxMemoryPressure(analysis.getMaxMemoryPressure())
                .oomCount(analysis.getOomCount())
                .restartCount(analysis.getRestartCount())
                .crashLoopCount(analysis.getCrashLoopCount())
                .confidence(analysis.getConfidence())
                .reasons(analysis.getReasons())
                .createdAt(analysis.getCreatedAt())
                .build();
    }
}
