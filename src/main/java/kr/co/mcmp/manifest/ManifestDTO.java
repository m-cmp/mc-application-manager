package kr.co.mcmp.manifest;

import lombok.Setter;
import lombok.Getter;

import javax.persistence.Column;

import kr.co.mcmp.catalog.Ref.CatalogRefEntity;

@Getter
@Setter
public class ManifestDTO {

    private Integer manifestIdx;
    private String manifestTitle;
    private String manifestContent;
    private String manifestType;
    private String manifestCategory;

    public ManifestDTO(ManifestEntity mEntity){
        this.manifestIdx = mEntity.getId();
        this.manifestTitle = mEntity.getTitle();
        this.manifestContent = mEntity.getManifest();
        this.manifestType = mEntity.getType();
        this.manifestCategory = mEntity.getCategory();
    }

}
