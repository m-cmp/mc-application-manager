package kr.co.mcmp.softwarecatalog.Ref;

import kr.co.mcmp.softwarecatalog.SoftwareCatalog;
import lombok.*;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonBackReference;

@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name="SOFTWARE_CATALOG_REF")
public class CatalogRefEntity {

    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    @Column(columnDefinition="INT", name="ID")
    private Long id; // CatalogRef의 고유 식별자

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CATALOG_ID")
    @JsonBackReference
    private SoftwareCatalog catalog; // 이 CatalogRef가 속한 소프트웨어 카탈로그

    @Column(columnDefinition="INT DEFAULT 0 NOT NULL", name="REF_IDX")
    private Integer refId; // 참조 인덱스

    @Column(columnDefinition="VARCHAR(200) DEFAULT '' NOT NULL", name="REF_VALUE")
    private String refValue; // 참조 값 (URL 등)

    @Column(columnDefinition="VARCHAR(200) DEFAULT ''", name="REF_DESC")
    private String refDesc; // 참조 설명

    @Column(columnDefinition="VARCHAR(10) DEFAULT ''", name="REF_TYPE")
    private String refType; // 참조 유형 (예: WORKFLOW, URL 등)
}