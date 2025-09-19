package kr.co.mcmp.softwarecatalog;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonManagedReference;

import kr.co.mcmp.softwarecatalog.Ref.CatalogRefEntity;
import kr.co.mcmp.softwarecatalog.application.model.HelmChart;
import kr.co.mcmp.softwarecatalog.application.model.PackageInfo;
// import kr.co.mcmp.softwarecatalog.model.SoftwareSourceMapping;
import kr.co.mcmp.softwarecatalog.users.Entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name="SOFTWARE_CATALOG")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SoftwareCatalog {

    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    @Column(columnDefinition="INT", name="ID")
    private Long id;         // 소프트웨어 카탈로그 고유 식별자

    @Column(columnDefinition="VARCHAR(200) NOT NULL DEFAULT ''", name="TITLE")
    private String name;       // 소프트웨어 이름

    @Column(columnDefinition="VARCHAR(255) NOT NULL DEFAULT ''", name="CATEGORY")
    private String category;    // 소프트웨어 카탈로그 (예 : DB, WAS 등)

    // source_type은 SOFTWARE_SOURCE_MAPPING에서 관리

    @Column(columnDefinition="VARCHAR(5000) NOT NULL DEFAULT ''", name="DESCRIPTION")
    private String description; // 상세 설명

    @Column(name = "default_port")
    private Integer defaultPort;

    @Column(name="logo_url_large")
    private String logoUrlLarge; // 소프트웨어 로그 큰 이미지 URL

    @Column(name="logo_url_small")
    private String logoUrlSmall;    // 소프트웨어 로그 작은 이미지 URL

    @Column(columnDefinition="VARCHAR(200) NOT NULL DEFAULT ''", name="SUMMARY")
    private String summary;
    
    // 추가 필드들
    @Column(name = "version")
    private String version;
    
    @Column(name = "license")
    private String license;
    
    @Column(name = "homepage")
    private String homepage;
    
    @Column(name = "repository_url")
    private String repositoryUrl;
    
    @Column(name = "documentation_url")
    private String documentationUrl;
      
    @ManyToOne
    @JoinColumn(name = "registered_by")
    private User registeredBy; // 이 소프트웨어를 등록한 사용자

    @Column(name = "created_at")
    private LocalDateTime createdAt; // 소프트웨어 카탈로그 항목 생성 시간

    @Column(name = "updated_at")
    private LocalDateTime updatedAt; // 소프트웨어 카탈로그 항목 최종 수정 시간

    @Column(name = "min_cpu")
    private Double minCpu; // 최소 CPU 요구사항

    @Column(name = "recommended_cpu")
    private Double recommendedCpu; // 권장 CPU 사양

    @Column(name = "min_memory")
    private Long minMemory; // 최소 메모리 요구사항

    @Column(name = "recommended_memory")
    private Long recommendedMemory; // 권장 메모리 사양

    @Column(name = "min_disk")
    private Long minDisk; // 최소 디스크 요구사항

    @Column(name = "recommended_disk")
    private Long recommendedDisk; // 권장 디스크 사양

    @Column(name ="hpa_enabled", nullable = true)
    private Boolean hpaEnabled;

    @Column(name = "cpu_threshold")
    private Double cpuThreshold; // CPU 임계값

    @Column(name = "memory_threshold")
    private Double memoryThreshold; // 메모리 임계값

    @Column(name = "min_replicas")
    private Integer minReplicas; // 최소 복제 수

    @Column(name = "max_replicas")
    private Integer maxReplicas; // 최대 복제 수

    // Ingress 관련 필드들
    @Column(name = "ingress_enabled")
    private Boolean ingressEnabled; // Ingress 활성화 여부

    @Column(name = "ingress_host")
    private String ingressHost; // Ingress 호스트 (예: app.example.com)

    @Column(name = "ingress_path")
    private String ingressPath; // Ingress 경로 (예: /app)

    @Column(name = "ingress_class")
    private String ingressClass; // Ingress 클래스 (예: nginx, traefik)

    @Column(name = "ingress_tls_enabled")
    private Boolean ingressTlsEnabled; // TLS 활성화 여부

    @Column(name = "ingress_tls_secret")
    private String ingressTlsSecret; // TLS 시크릿 이름

    @OneToMany(mappedBy = "catalog", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PortMapping> ports = new ArrayList<>();

    @OneToMany(mappedBy = "catalog", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    @Builder.Default
    private List<CatalogRefEntity> catalogRefs = new ArrayList<>();
    
    @OneToOne(mappedBy = "catalog", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private PackageInfo packageInfo;

    @OneToOne(mappedBy = "catalog", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private HelmChart helmChart;
    
    // @OneToMany(mappedBy = "catalog", cascade = CascadeType.ALL, orphanRemoval = true)
    // @JsonManagedReference
    // @Builder.Default
    // private List<SoftwareSourceMapping> sourceMappings = new ArrayList<>();
    
    public void addCatalogRef(CatalogRefEntity catalogRef) {
        this.catalogRefs.add(catalogRef);
        catalogRef.setCatalog(this);
    }

    public void removeCatalogRef(CatalogRefEntity catalogRef) {
        this.catalogRefs.remove(catalogRef);
        catalogRef.setCatalog(null);
    }
    
    // public void addSourceMapping(SoftwareSourceMapping sourceMapping) {
    //     this.sourceMappings.add(sourceMapping);
    //     sourceMapping.setCatalog(this);
    // }
    
    // public void removeSourceMapping(SoftwareSourceMapping sourceMapping) {
    //     this.sourceMappings.remove(sourceMapping);
    //     sourceMapping.setCatalog(null);
    // }

    public void addPort(PortMapping port) {
        this.ports.add(port);
        port.setCatalog(this);
    }

    public void removePort(PortMapping port) {
        this.ports.remove(port);
        port.setCatalog(null);
    }
    
    public void updateFromDTO(SoftwareCatalogDTO dto) {
        if (dto.getName() != null) this.name = dto.getName();
        if (dto.getDescription() != null) this.description = dto.getDescription();
        if (dto.getVersion() != null) this.version = dto.getVersion();
        if (dto.getCategory() != null) this.category = dto.getCategory();
        if (dto.getLicense() != null) this.license = dto.getLicense();
        if (dto.getHomepage() != null) this.homepage = dto.getHomepage();
        if (dto.getRepositoryUrl() != null) this.repositoryUrl = dto.getRepositoryUrl();
        if (dto.getDocumentationUrl() != null) this.documentationUrl = dto.getDocumentationUrl();
    }
}
