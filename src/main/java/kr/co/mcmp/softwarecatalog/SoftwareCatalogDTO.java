package kr.co.mcmp.softwarecatalog;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import kr.co.mcmp.softwarecatalog.Ref.CatalogRefDTO;
import kr.co.mcmp.softwarecatalog.application.dto.HelmChartDTO;
import kr.co.mcmp.softwarecatalog.application.dto.PackageInfoDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SoftwareCatalogDTO {

    private Long id;
    private String title;
    private String description;
    private String category;
    private String summary;
    private String sourceType;
    private String logoUrlLarge;
    private String logoUrlSmall;
    private Long registeredById;
    private Boolean hpaEnabled;
    private Integer defaultPort;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Double minCpu;
    private Double recommendedCpu;
    private Long minMemory;
    private Long recommendedMemory;
    private Long minDisk;
    private Long recommendedDisk;
    private Double cpuThreshold;
    private Double memoryThreshold;
    private Integer minReplicas;
    private Integer maxReplicas;
    private List<CatalogRefDTO> catalogRefs;
    private PackageInfoDTO packageInfo;
    private HelmChartDTO helmChart;
    
    public static SoftwareCatalogDTO fromEntity(SoftwareCatalog entity) {
        SoftwareCatalogDTO.SoftwareCatalogDTOBuilder builder  = SoftwareCatalogDTO.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .description(entity.getDescription())
                .category(entity.getCategory())
                .defaultPort(entity.getDefaultPort())
                .sourceType(entity.getSourceType())
                .logoUrlLarge(entity.getLogoUrlLarge())
                .logoUrlSmall(entity.getLogoUrlSmall())
                // .registeredById(entity.getRegisteredBy().getId())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .summary(entity.getSummary())
                .hpaEnabled(entity.getHpaEnabled())
                .minCpu(entity.getMinCpu())
                .recommendedCpu(entity.getRecommendedCpu())
                .minMemory(entity.getMinMemory())
                .recommendedMemory(entity.getRecommendedMemory())
                .minDisk(entity.getMinDisk())
                .recommendedDisk(entity.getRecommendedDisk())
                .cpuThreshold(entity.getCpuThreshold())
                .memoryThreshold(entity.getMemoryThreshold())
                .minReplicas(entity.getMinReplicas())
                .maxReplicas(entity.getMaxReplicas());

        if (entity.getRegisteredBy() != null) {
            builder.registeredById(entity.getRegisteredBy().getId());
        }

        SoftwareCatalogDTO dto = builder.build();
        if (entity.getCatalogRefs() != null) {
            dto.setCatalogRefs(entity.getCatalogRefs().stream()
                    .map(CatalogRefDTO::fromEntity)
                    .collect(Collectors.toList()));
        }
        return dto;
    }

    public SoftwareCatalog toEntity() {
        SoftwareCatalog entity = SoftwareCatalog.builder()
                .id(this.id)
                .title(this.title)
                .description(this.description)
                .category(this.category)
                .defaultPort(this.defaultPort)
                .sourceType(this.sourceType)
                .logoUrlLarge(this.logoUrlLarge)
                .logoUrlSmall(this.logoUrlSmall)
                .createdAt(this.createdAt)
                .updatedAt(this.updatedAt)
                .summary(this.summary)
                .minCpu(this.minCpu)
                .hpaEnabled(this.hpaEnabled)
                .recommendedCpu(this.recommendedCpu)
                .minMemory(this.minMemory)
                .recommendedMemory(this.recommendedMemory)
                .minDisk(this.minDisk)
                .recommendedDisk(this.recommendedDisk)
                .cpuThreshold(this.cpuThreshold)
                .memoryThreshold(this.memoryThreshold)
                .minReplicas(this.minReplicas)
                .maxReplicas(this.maxReplicas)
                .build();
        if (this.catalogRefs != null) {
            entity.setCatalogRefs(this.catalogRefs.stream()
                    .map(CatalogRefDTO::toEntity)
                    .collect(Collectors.toList()));
        }

        return entity;
    }

}
