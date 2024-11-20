package kr.co.mcmp.softwarecatalog.application.dto;

import kr.co.mcmp.softwarecatalog.application.model.HelmChart;
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
public class HelmChartDTO {
    private Long id;
    private Long catalogId;
    private String chartName;
    private String chartVersion;
    private String chartRepositoryUrl;
    private String valuesFile;
    private String packageId;
    private String normalizedName;
    private Boolean hasValuesSchema;
    private String repositoryName;
    private Boolean repositoryOfficial;
    private String repositoryDisplayName;

    public HelmChart toEntity() {
        return HelmChart.builder()
                .chartName(this.chartName)
                .chartVersion(this.chartVersion)
                .chartRepositoryUrl(this.chartRepositoryUrl)
                .valuesFile(this.valuesFile)
                .hasValuesSchema(this.hasValuesSchema)
                .repositoryName(this.repositoryName)
                .repositoryOfficial(this.repositoryOfficial)
                .repositoryDisplayName(this.repositoryDisplayName)
                .build();
    }

    public static HelmChartDTO fromEntity(HelmChart helmChart) {
        return HelmChartDTO.builder()
                .id(helmChart.getId())
                .catalogId(helmChart.getCatalog() != null ? helmChart.getCatalog().getId() : null)
                .chartName(helmChart.getChartName())
                .chartVersion(helmChart.getChartVersion())
                .chartRepositoryUrl(helmChart.getChartRepositoryUrl())
                .valuesFile(helmChart.getValuesFile())
                .hasValuesSchema(helmChart.getHasValuesSchema())
                .repositoryName(helmChart.getRepositoryName())
                .repositoryOfficial(helmChart.getRepositoryOfficial())
                .repositoryDisplayName(helmChart.getRepositoryDisplayName())
                .build();
    }
}
