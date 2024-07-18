package kr.co.strato.catalog;

import lombok.*;

import javax.persistence.*;

@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name="SOFTWARE_CATALOG")
@ToString(exclude = {"SOFTWARE_CATALOG"})
public class CatalogEntity {

    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    @Column(columnDefinition="INT", name="IDX")
    private Integer id;

    @Column(columnDefinition="VARCHAR(200) NOT NULL DEFAULT ''", name="TITLE")
    private String title;

    @Column(columnDefinition="VARCHAR(5000) NOT NULL DEFAULT ''", name="DESCRIPTION")
    private String description;

    @Column(columnDefinition="VARCHAR(200) NOT NULL DEFAULT ''", name="SUMMARY")
    private String summary;

    @Column(columnDefinition="VARCHAR(200) NOT NULL DEFAULT ''", name="HOMEPAGE")
    private String homepage;

    @Column(columnDefinition="VARCHAR(200) NOT NULL DEFAULT ''", name="ICON")
    private String icon;

    @Column(columnDefinition="VARCHAR(10) NOT NULL DEFAULT ''", name="CATEGORY")
    private String category;






}
