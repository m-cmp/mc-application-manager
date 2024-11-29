package kr.co.mcmp.softwarecatalog.application.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonBackReference;

import kr.co.mcmp.softwarecatalog.SoftwareCatalog;
import kr.co.mcmp.softwarecatalog.application.dto.HelmChartDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "HELM_CHART")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class HelmChart {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // Helm 차트 정보의 고유 식별자

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "catalog_id", unique = true)
    @JsonBackReference
    private SoftwareCatalog catalog; // 이 Helm 차트가 속한 소프트웨어 카탈로그

    @Column(name = "chart_name", nullable = false)
    private String chartName; // Helm 차트의 이름

    @Column(name = "chart_version")
    private String chartVersion; // Helm 차트의 버전

    @Column(name = "chart_repository_url")
    private String chartRepositoryUrl; // Helm 차트가 저장된 저장소의 URL

    @Column(name = "values_file", columnDefinition = "TEXT")
    private String valuesFile; // Helm 차트의 values.yaml 파일 내용

    @Column(name = "package_id")
    private String packageId; // ArtifactHub 패키지 ID (ArtifactHub 전용)

    // IMAGE_REPOSITORY
    @Column(name= "image_repository")
    private String imageRepository;
    
    @Column(name = "normalized_name")
    private String normalizedName; // ArtifactHub 정규화된 이름 (ArtifactHub 전용)

    @Column(name = "has_values_schema")
    private Boolean hasValuesSchema; // ArtifactHub values 스키마 존재 여부 (ArtifactHub 전용)

    @Column(name = "repository_name")
    private String repositoryName; // ArtifactHub 저장소 이름 (ArtifactHub 전용)

    @Column(name = "repository_official")
    private Boolean repositoryOfficial; // ArtifactHub 공식 저장소 여부 (ArtifactHub 전용)

    @Column(name = "repository_display_name")
    private String repositoryDisplayName; // ArtifactHub 저장소 표시 이름 (ArtifactHub 전용)

    

    public void updateFromDTO(HelmChartDTO helmChart) {
        helmChart.setChartName(this.chartName);
        helmChart.setChartVersion(this.chartVersion);
        helmChart.setChartRepositoryUrl(this.chartRepositoryUrl);
        helmChart.setValuesFile(this.valuesFile);
        helmChart.setHasValuesSchema(this.hasValuesSchema);
        helmChart.setRepositoryName(this.repositoryName);
        helmChart.setRepositoryOfficial(this.repositoryOfficial);
        helmChart.setRepositoryDisplayName(this.repositoryDisplayName);
    }
}
