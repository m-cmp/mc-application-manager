package kr.co.mcmp.catalog;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

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

    public CatalogDTO(CatalogEntity cEntity){
        if(cEntity.getId() != null){ this.catalogIdx = cEntity.getId(); }
        this.catalogTitle = cEntity.getTitle();
        this.catalogDescription = cEntity.getDescription();
        this.catalogSummary = cEntity.getSummary();
        this.catalogIcon = cEntity.getIcon();
        this.catalogCategory = cEntity.getCategory();
    }


}
