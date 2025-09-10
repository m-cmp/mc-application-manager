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

    // 화면 기준
    // 0. Common
    private Long id;

    // 1. Package
    private String sourceType;  // EX) DOCKERHUB / ARTIFACTHUB / ...ETC (FIXED_VALUE)
    private String category;
    private PackageInfoDTO packageInfo;
    private HelmChartDTO helmChart;

    // 2. General
    private String name;
    private String summary;
    private String description;
    private String logoUrlLarge;
    private String logoUrlSmall;
    private List<CatalogRefDTO> catalogRefs;
    
    // 추가 필드들
    private String version;
    private String license;
    private String homepage;
    private String repositoryUrl;
    private String documentationUrl;

    // 3. Resource Requeirements
    private Double minCpu;
    private Double recommendedCpu;
    private Long minMemory;
    private Long recommendedMemory;
    private Long minDisk;
    private Long recommendedDisk;

    private Boolean hpaEnabled;
    private Integer minReplicas;
    private Integer maxReplicas;
    private Double cpuThreshold;
    private Double memoryThreshold;

    // 4. Network
    private List<PortMapping> ports;
    private Boolean ingressEnabled;
    private String ingressUrl;
    // 임시
    private Integer defaultPort;

    // 5. etc
    private Long registeredById;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static SoftwareCatalogDTO fromEntity(SoftwareCatalog entity) {
        if (entity == null) {
            return null;
        }

        SoftwareCatalogDTO.SoftwareCatalogDTOBuilder builder = SoftwareCatalogDTO.builder()
                // 0. Common
                .id(entity.getId())

                // 1. Package
                .sourceType(entity.getSourceType())
                .category(entity.getCategory())
                .packageInfo(entity.getPackageInfo() != null ? PackageInfoDTO.fromEntity(entity.getPackageInfo()) : null)
                .helmChart(entity.getHelmChart() != null ? HelmChartDTO.fromEntity(entity.getHelmChart()) : null)

                // 2. General
                .name(entity.getName())
                .summary(entity.getSummary())
                .description(entity.getDescription())
                .logoUrlLarge(entity.getLogoUrlLarge())
                .logoUrlSmall(entity.getLogoUrlSmall())
                .version(entity.getVersion())
                .license(entity.getLicense())
                .homepage(entity.getHomepage())
                .repositoryUrl(entity.getRepositoryUrl())
                .documentationUrl(entity.getDocumentationUrl())

                // 3. Resource Requirements
                .minCpu(entity.getMinCpu())
                .recommendedCpu(entity.getRecommendedCpu())
                .minMemory(entity.getMinMemory())
                .recommendedMemory(entity.getRecommendedMemory())
                .minDisk(entity.getMinDisk())
                .recommendedDisk(entity.getRecommendedDisk())
                .hpaEnabled(entity.getHpaEnabled())
                .minReplicas(entity.getMinReplicas())
                .maxReplicas(entity.getMaxReplicas())
                .cpuThreshold(entity.getCpuThreshold())
                .memoryThreshold(entity.getMemoryThreshold())

                .defaultPort(entity.getDefaultPort())

                // 5. etc
                .registeredById(entity.getRegisteredBy() != null ? entity.getRegisteredBy().getId() : null)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt());

        SoftwareCatalogDTO dto = builder.build();

        if (entity.getCatalogRefs() != null) {
            dto.setCatalogRefs(entity.getCatalogRefs().stream()
                    .map(CatalogRefDTO::fromEntity)
                    .collect(Collectors.toList()));
        }

        if (entity.getPorts() != null) {
            dto.setPorts(entity.getPorts().stream()
                    .map(port -> {
                        PortMapping mapping = new PortMapping();
                        mapping.setTargetPort(port.getTargetPort());
                        mapping.setHostPort(port.getHostPort());
                        mapping.setProtocol(port.getProtocol());
                        return mapping;
                    })
                    .collect(Collectors.toList()));
        }

        return dto;
    }

    public SoftwareCatalog toEntity() {
        SoftwareCatalog.SoftwareCatalogBuilder builder = SoftwareCatalog.builder()
                // 0. Common
                .id(this.id)

                // 1. Package
                .sourceType(this.sourceType)
                .category(this.category)
                .packageInfo(this.packageInfo != null ? this.packageInfo.toEntity() : null)
                .helmChart(this.helmChart != null ? this.helmChart.toEntity() : null)

                // 2. General
                .name(this.name)
                .summary(this.summary)
                .description(this.description)
                .logoUrlLarge(this.logoUrlLarge)
                .logoUrlSmall(this.logoUrlSmall)

                // 3. Resource Requirements
                .minCpu(this.minCpu)
                .recommendedCpu(this.recommendedCpu)
                .minMemory(this.minMemory)
                .recommendedMemory(this.recommendedMemory)
                .minDisk(this.minDisk)
                .recommendedDisk(this.recommendedDisk)
                .hpaEnabled(this.hpaEnabled)
                .minReplicas(this.minReplicas)
                .maxReplicas(this.maxReplicas)
                .cpuThreshold(this.cpuThreshold)
                .memoryThreshold(this.memoryThreshold)

                // 5. etc
                .createdAt(this.createdAt)
                .updatedAt(this.updatedAt);

        SoftwareCatalog entity = builder.build();

        if (this.catalogRefs != null) {
            entity.setCatalogRefs(this.catalogRefs.stream()
                    .map(CatalogRefDTO::toEntity)
                    .collect(Collectors.toList()));
        }

        if (this.ports != null) {
            entity.setPorts(this.ports.stream()
                    .map(p -> PortMapping.builder()
                            .targetPort(p.getTargetPort())
                            .hostPort(p.getHostPort())
                            .protocol(p.getProtocol())
                            .catalog(entity) // 연관관계 설정 권장
                            .build())
                    .collect(Collectors.toList()));
        }

        return entity;
    }


}
