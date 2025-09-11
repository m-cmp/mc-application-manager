package kr.co.mcmp.softwarecatalog.category.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import kr.co.mcmp.softwarecatalog.PortMapping;
import kr.co.mcmp.softwarecatalog.Ref.CatalogRefEntity;
import kr.co.mcmp.softwarecatalog.SoftwareCatalog;
import kr.co.mcmp.softwarecatalog.application.model.HelmChart;
import kr.co.mcmp.softwarecatalog.application.model.PackageInfo;
import kr.co.mcmp.softwarecatalog.users.Entity.User;
import lombok.*;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name="INGRESS_CONFIG")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngressConfig {

    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    @Column(columnDefinition="INT", name="ID")
    private Long id;

    @Column(columnDefinition="VARCHAR(255) NOT NULL DEFAULT ''", name="PATH")
    private String path;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CATALOG_ID")
    @JsonBackReference
    private SoftwareCatalog catalog; // 이 CatalogRef가 속한 소프트웨어 카탈로그
}
