package kr.co.mcmp.catalog;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name="SOFTWARE_CATALOG")
//@ToString(exclude = {"SOFTWARE_CATALOG"})
public class CatalogEntity {

    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    @Column(columnDefinition="INT", name="ID")
    private Integer id;

    @Column(columnDefinition="VARCHAR(200) NOT NULL DEFAULT ''", name="TITLE")
    private String title;

    @Column(columnDefinition="VARCHAR(5000) NOT NULL DEFAULT ''", name="DESCRIPTION")
    private String description;

    @Column(columnDefinition="VARCHAR(200) NOT NULL DEFAULT ''", name="SUMMARY")
    private String summary;

    @Column(columnDefinition="VARCHAR(200) NOT NULL DEFAULT ''", name="ICON")
    private String icon;

    @Column(columnDefinition="VARCHAR(15) NOT NULL DEFAULT ''", name="CATEGORY")
    private String category;

    @Column(columnDefinition="INT", name = "RECOMMENDED_CPU", nullable = false)
    private Integer recommendedCpu;

    @Column(columnDefinition="INT", name = "RECOMMENDED_MEMORY", nullable = false)
    private Integer recommendedMemory;

    @Column(columnDefinition="INT", name = "RECOMMENDED_DISK", nullable = false)
    private Integer recommendedDisk;

    @Column(columnDefinition="BOOLEAN", name = "ENABLE_HPA", nullable = false)
    private Boolean enableHpa;

    @Column(columnDefinition="INT", name="HPA_MIN_REPLICAS", nullable = true)
    private Integer hpaMinReplicas;

    @Column(columnDefinition="INT", name="HPA_MAX_REPLICAS", nullable = true)
    private Integer hpaMaxReplicas;

    @Column(columnDefinition="INT", name="HPA_CPU_UTILIZATION", nullable = true)
    private Integer hpaCpuUtilization;

    @Column(columnDefinition="INT", name="HPA_MEMORY_UTILIZATION", nullable = true)
    private Integer hpaMemoryUtilization;         

    public CatalogEntity(CatalogDTO cDto){
        if(cDto.getCatalogIdx() != null) { this.id = cDto.getCatalogIdx(); }
        this.title = cDto.getCatalogTitle();
        this.description = cDto.getCatalogDescription();
        this.summary = cDto.getCatalogSummary();
        this.icon = cDto.getCatalogIcon();
        this.category = cDto.getCatalogCategory();
        this.recommendedCpu = cDto.getRecommendedCpu();
        this.recommendedMemory = cDto.getRecommendedMemory();
        this.recommendedDisk = cDto.getRecommendedDisk();
        this.enableHpa = cDto.getEnableHpa();
        this.hpaMinReplicas = cDto.getHpaMinReplicas();
        this.hpaMaxReplicas = cDto.getHpaMaxReplicas();
        this.hpaCpuUtilization = cDto.getHpaCpuUtilization();
        this.hpaMemoryUtilization = cDto.getHpaMemoryUtilization();
    }



}
