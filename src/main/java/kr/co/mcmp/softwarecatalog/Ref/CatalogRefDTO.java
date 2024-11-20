package kr.co.mcmp.softwarecatalog.Ref;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CatalogRefDTO {
    private Long id;
    private Long catalogId; // SoftwareCatalog의 ID를 저장
    private Integer refId;
    private String refValue;
    private String refDesc;
    private String refType;

    public static CatalogRefDTO fromEntity(CatalogRefEntity entity) {
        return CatalogRefDTO.builder()
                .id(entity.getId())
                .catalogId(entity.getCatalog() != null ? entity.getCatalog().getId() : null)
                .refId(entity.getRefId())
                .refValue(entity.getRefValue())
                .refDesc(entity.getRefDesc())
                .refType(entity.getRefType())
                .build();
    }

    public CatalogRefEntity toEntity() {
        return CatalogRefEntity.builder()
                .id(this.id)
                .refId(this.refId)
                .refValue(this.refValue)
                .refDesc(this.refDesc)
                .refType(this.refType)
                .build();
    }
}