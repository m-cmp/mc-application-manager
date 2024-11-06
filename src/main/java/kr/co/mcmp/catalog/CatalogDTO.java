package kr.co.mcmp.catalog;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

import javax.persistence.Column;

import kr.co.mcmp.catalog.Ref.CatalogRefDTO;

@Getter
@Setter
@NoArgsConstructor
public class CatalogDTO {

    private Integer catalogIdx;
    private String catalogTitle;
    private String catalogDescription;
    private String catalogSummary;
    private String catalogIcon;
    private String catalogCategory;
    private List<CatalogRefDTO> catalogRefData;
    
    private Integer recommendedCpu;
    private Integer recommendedMemory;
    private Integer recommendedDisk;

    private Boolean enableHpa;
    private Integer hpaMinReplicas;
    private Integer hpaMaxReplicas;
    private Integer hpaCpuUtilization;
    private Integer hpaMemoryUtilization;         

    public CatalogDTO(CatalogEntity cEntity){
        if(cEntity.getId() != null){ this.catalogIdx = cEntity.getId(); }
        this.catalogTitle = cEntity.getTitle();
        this.catalogDescription = cEntity.getDescription();
        this.catalogSummary = cEntity.getSummary();
        this.catalogIcon = cEntity.getIcon();
        this.catalogCategory = cEntity.getCategory();
        this.recommendedCpu = cEntity.getRecommendedCpu();
        this.recommendedMemory = cEntity.getRecommendedMemory();
        this.recommendedDisk = cEntity.getRecommendedDisk();
        this.enableHpa = cEntity.getEnableHpa();
        this.hpaMinReplicas = cEntity.getHpaMinReplicas();
        this.hpaMaxReplicas = cEntity.getHpaMaxReplicas();
        this.hpaCpuUtilization = cEntity.getHpaCpuUtilization();
        this.hpaMemoryUtilization = cEntity.getHpaMemoryUtilization();
    }


}
