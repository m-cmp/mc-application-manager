package kr.co.mcmp.catalog.Ref;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CatalogRefDTO {

    private Integer catalogRefIdx;
    private Integer catalogIdx;
    private Integer referncetIdx;
    private String referenceValue; // ref url, ref value, etc...
    private String referenceDescription;
    private String referenceType; // homepage, manifest, workflow, image, etc...

    public CatalogRefDTO(CatalogRefEntity crEntity){
        this.catalogRefIdx = crEntity.getId();
        this.catalogIdx = crEntity.getCatalogId();
        this.referncetIdx = crEntity.getRefIdx();
        this.referenceValue = crEntity.getRefValue();
        this.referenceDescription = crEntity.getRefDesc();
        this.referenceType = crEntity.getRefType();
    }

    public CatalogRefDTO(){

    }
}
