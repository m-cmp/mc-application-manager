package kr.co.mcmp.softwarecatalog.model;

// import lombok.AllArgsConstructor;
// import lombok.Builder;
// import lombok.Data;
// import lombok.NoArgsConstructor;

// import javax.persistence.*;
// import java.time.LocalDateTime;
// import kr.co.mcmp.softwarecatalog.SoftwareCatalog;
// import com.fasterxml.jackson.annotation.JsonBackReference;

// @Entity
// @Table(name = "SOFTWARE_SOURCE_MAPPING")
// @Data
// @Builder
// @NoArgsConstructor
// @AllArgsConstructor
// public class SoftwareSourceMapping {
    
//     @Id
//     @GeneratedValue(strategy = GenerationType.IDENTITY)
//     @Column(name = "ID")
//     private Long id;
    
//     @Column(name = "CATALOG_ID", nullable = false)
//     private Long catalogId;
    
//     @Column(name = "SOURCE_TYPE", nullable = false, length = 50)
//     private String sourceType; // 'DOCKERHUB', 'ARTIFACTHUB', 'GITHUB', etc.
    
//     @Column(name = "SOURCE_ID", nullable = false)
//     private Long sourceId; // PACKAGE_INFO.ID 또는 HELM_CHART.ID
    
//     @Column(name = "IS_PRIMARY", nullable = false)
//     private Boolean isPrimary = false;
    
//     @Column(name = "PRIORITY", nullable = false)
//     private Integer priority = 0;
    
//     @Column(name = "CREATED_AT")
//     private LocalDateTime createdAt;
    
//     @Column(name = "UPDATED_AT")
//     private LocalDateTime updatedAt;
    
//     // 관계 매핑
//     @ManyToOne(fetch = FetchType.LAZY)
//     @JoinColumn(name = "CATALOG_ID", insertable = false, updatable = false)
//     @JsonBackReference
//     private SoftwareCatalog catalog;
    
//     @PrePersist
//     protected void onCreate() {
//         createdAt = LocalDateTime.now();
//         updatedAt = LocalDateTime.now();
//     }
    
//     @PreUpdate
//     protected void onUpdate() {
//         updatedAt = LocalDateTime.now();
//     }
// }
