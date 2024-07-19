package kr.co.strato.catalog;

import lombok.*;

import javax.persistence.*;

@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name="SOFTWARE_CATALOG_REF")
@ToString(exclude = {"SOFTWARE_CATALOG_REF"})
public class CatalogRefEntity {

    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    @Column(columnDefinition="INT", name="ID")
    private Integer id;

    // software catalog pk
    @Column(columnDefinition="INT DEFAULT 1 NOT NULL", name="CATALOG_ID")
    private Integer catalogId;

    @Column(columnDefinition="INT DEFAULT 0 NOT NULL", name="REF_IDX")
    private Integer refIdx;

    @Column(columnDefinition="VARCHAR(200) DEFAULT '' NOT NULL", name="REF_VALUE")
    private String refUrl;

//    @Column(columnDefinition="VARCHAR(50) DEFAULT '' NOT NULL", name="REF_URL_TITLE")
//    private String refUrlTitle;

    @Column(columnDefinition="VARCHAR(200) DEFAULT ''", name="REF_DESC")
    private String refDesc;

    @Column(columnDefinition="VARCHAR(10) DEFAULT ''", name="REF_TYPE")
    private String refType;

/*
    INSERT INTO SOFTWARE_CATALOG_REF(CATALOG_ID, REF_IDX, REF_VALUE, REF_DESC, REF_TYPE)
    VALUES
        ((SELECT ID FROM SOFTWARE_CATALOG WHERE TITLE = 'APACHE TOMCAT'), 0, 'https://tomcat.apache.org/', '', 'HOMEPAGE'),
        ((SELECT ID FROM SOFTWARE_CATALOG WHERE TITLE = 'REDIS'), 0, 'https://redis.io/', '', 'HOMEPAGE'),
        ((SELECT ID FROM SOFTWARE_CATALOG WHERE TITLE = 'NGINX'), 0, 'https://nginx.org/en/', '', 'HOMEPAGE')

*/


}
