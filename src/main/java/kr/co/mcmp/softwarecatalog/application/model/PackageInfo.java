package kr.co.mcmp.softwarecatalog.application.model;

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
import javax.persistence.OneToOne;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonBackReference;

import kr.co.mcmp.softwarecatalog.SoftwareCatalog;
import kr.co.mcmp.softwarecatalog.application.constants.PackageType;
import kr.co.mcmp.softwarecatalog.application.dto.PackageInfoDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
@Entity
@Table(name = "PACKAGE_INFO")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PackageInfo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "catalog_id", unique = true)
    @JsonBackReference
    private SoftwareCatalog catalog;

    @Enumerated(EnumType.STRING)
    @Column(name = "package_type", nullable = false)
    private PackageType packageType;

    @Column(name = "package_name")
    private String packageName;

    @Column(name = "package_version")
    private String packageVersion;

    @Column(name = "repository_url")
    private String repositoryUrl;

    @Column(name = "docker_image_id")
    private String dockerImageId;

    @Column(name = "docker_publisher")
    private String dockerPublisher;

    @Column(name = "docker_created_at")
    private LocalDateTime dockerCreatedAt;

    @Column(name = "docker_updated_at")
    private LocalDateTime dockerUpdatedAt;

    @Column(name = "docker_short_description")
    private String dockerShortDescription;

    @Column(name = "docker_source")
    private String dockerSource;

    @Column(name = "star_count")
    private Integer starCount;

    @Column(name = "pull_count")
    private String pullCount;

    @Column(name = "is_official")
    private Boolean isOfficial;

    @Column(name = "is_automated")
    private Boolean isAutomated;

    @Column(name = "last_pulled_at")
    private LocalDateTime lastPulledAt;

    @Column(name = "is_archived")
    private Boolean isArchived;

    @Column(name = "operating_systems")
    private String operatingSystems;

    @Column(name = "architectures")
    private String architectures;

    @Column(name = "categories")
    private String categories;

    public void updateFromDTO(PackageInfoDTO dto) {
        // 기존 필드 업데이트
        this.packageType = dto.getPackageType();
        this.packageName = dto.getPackageName();
        this.packageVersion = dto.getPackageVersion();
        this.repositoryUrl = dto.getRepositoryUrl();
        this.dockerImageId = dto.getDockerImageId();
        this.dockerPublisher = dto.getDockerPublisher();
        this.dockerCreatedAt = dto.getDockerCreatedAt();
        this.dockerUpdatedAt = dto.getDockerUpdatedAt();
        this.dockerShortDescription = dto.getDockerShortDescription();
        this.dockerSource = dto.getDockerSource();

        // 새로운 필드 업데이트
        this.starCount = dto.getStarCount();
        this.pullCount = dto.getPullCount();
        this.isOfficial = dto.getIsOfficial();
        this.isAutomated = dto.getIsAutomated();
        this.lastPulledAt = dto.getLastPulledAt();
        this.isArchived = dto.getIsArchived();
        this.operatingSystems = dto.getOperatingSystems();
        this.architectures = dto.getArchitectures();
        this.categories = dto.getCategories();
    }
}
