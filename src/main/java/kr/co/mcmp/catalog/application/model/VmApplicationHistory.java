package kr.co.mcmp.catalog.application.model;

import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import kr.co.mcmp.catalog.CatalogEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "vm_application_history")
public class VmApplicationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String namespace;
    @Column(name="mci_name", nullable = false)
    private String mciName;
    @Column(name="vm_name", nullable = false)
    private String vmName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "catalog_id", nullable = false)
    private CatalogEntity catalog;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ApplicationStatus status; 
        
    @Column(name = "installed_at")
    private LocalDateTime installedAt;
    @Column(name = "uninstalled_at")
    private LocalDateTime uninstalledAt;
    @Column(name="updated_at")
    private LocalDateTime updatedAt;
}
