package kr.co.mcmp.catalog;

import lombok.*;

import javax.persistence.*;

@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name="SOFTWARE_CATALOG_REF")
//@ToString(exclude = {"SOFTWARE_CATALOG_REF"})
public class CatalogRefEntity {

    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    @Column(columnDefinition="INT", name="ID")
    private Integer id;

    // software catalog pk
    @Column(columnDefinition="INT DEFAULT 1 NOT NULL", name="CATALOG_ID")
    private Integer catalogId;

    // workflow등 idx, 타 catalog idx등 index reference일때
    @Column(columnDefinition="INT DEFAULT 0 NOT NULL", name="REF_IDX")
    private Integer refIdx;

    // info url, 설정값 등
    @Column(columnDefinition="VARCHAR(200) DEFAULT '' NOT NULL", name="REF_VALUE")
    private String refValue; // ref url, ref value, etc...

    // 짧은 description
    @Column(columnDefinition="VARCHAR(200) DEFAULT ''", name="REF_DESC")
    private String refDesc;

    // workflow, url, catalog
    @Column(columnDefinition="VARCHAR(10) DEFAULT ''", name="REF_TYPE")
    private String refType;

    public CatalogRefEntity(CatalogRefDTO crDto){
        this.catalogId = crDto.getCatalogIdx();
        this.refIdx = crDto.getRefernectIdx();
        this.refValue = crDto.getReferenceValue();
        this.refDesc = crDto.getReferenceDescription();
        this.refType = crDto.getReferenceType();
    }

/*
    INSERT INTO SOFTWARE_CATALOG_REF(CATALOG_ID, REF_IDX, REF_VALUE, REF_DESC, REF_TYPE)
    VALUES
        ((SELECT ID FROM SOFTWARE_CATALOG WHERE TITLE = 'APACHE TOMCAT'), 0, 'https://tomcat.apache.org/', '', 'HOMEPAGE'),
        ((SELECT ID FROM SOFTWARE_CATALOG WHERE TITLE = 'REDIS'), 0, 'https://redis.io/', '', 'HOMEPAGE'),
        ((SELECT ID FROM SOFTWARE_CATALOG WHERE TITLE = 'NGINX'), 0, 'https://nginx.org/en/', '', 'HOMEPAGE')

*/


}
