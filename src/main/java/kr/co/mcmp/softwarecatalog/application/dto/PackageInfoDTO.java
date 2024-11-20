package kr.co.mcmp.softwarecatalog.application.dto;

import java.time.LocalDateTime;


import kr.co.mcmp.softwarecatalog.application.constants.PackageType;
import kr.co.mcmp.softwarecatalog.application.model.PackageInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PackageInfoDTO {
    private Long id;
    private Long catalogId;
    private PackageType packageType;
    private String packageName;
    private String packageVersion;
    private String repositoryUrl;
    private String dockerImageId;
    private String dockerPublisher;
    private LocalDateTime dockerCreatedAt;
    private LocalDateTime dockerUpdatedAt;
    private String dockerShortDescription;
    private String dockerSource;
    private Integer starCount;
    private String pullCount;
    private Boolean isOfficial;
    private Boolean isAutomated;
    private LocalDateTime lastPulledAt;
    private Boolean isArchived;
    private String operatingSystems;
    private String architectures;
    private String categories;

    public PackageInfo toEntity() {
        return PackageInfo.builder()
                .packageType(this.packageType)
                .packageName(this.packageName)
                .packageVersion(this.packageVersion)
                .repositoryUrl(this.repositoryUrl)
                .dockerImageId(this.dockerImageId)
                .dockerPublisher(this.dockerPublisher)
                .dockerCreatedAt(this.dockerCreatedAt)
                .dockerUpdatedAt(this.dockerUpdatedAt)
                .dockerShortDescription(this.dockerShortDescription)
                .dockerSource(this.dockerSource)
                .starCount(this.starCount)
                .pullCount(this.pullCount)
                .isOfficial(this.isOfficial)
                .isAutomated(this.isAutomated)
                .lastPulledAt(this.lastPulledAt)
                .isArchived(this.isArchived)
                .operatingSystems(this.operatingSystems)
                .architectures(this.architectures)
                .categories(this.categories)
                .build();
    }

    public static PackageInfoDTO fromEntity(PackageInfo packageInfo) {
        return PackageInfoDTO.builder()
                .id(packageInfo.getId())
                .catalogId(packageInfo.getCatalog() != null ? packageInfo.getCatalog().getId() : null)
                .packageType(packageInfo.getPackageType())
                .packageName(packageInfo.getPackageName())
                .packageVersion(packageInfo.getPackageVersion())
                .repositoryUrl(packageInfo.getRepositoryUrl())
                .dockerImageId(packageInfo.getDockerImageId())
                .dockerPublisher(packageInfo.getDockerPublisher())
                .dockerCreatedAt(packageInfo.getDockerCreatedAt())
                .dockerUpdatedAt(packageInfo.getDockerUpdatedAt())
                .dockerShortDescription(packageInfo.getDockerShortDescription())
                .dockerSource(packageInfo.getDockerSource())
                .starCount(packageInfo.getStarCount())
                .pullCount(packageInfo.getPullCount())
                .isOfficial(packageInfo.getIsOfficial())
                .isAutomated(packageInfo.getIsAutomated())
                .lastPulledAt(packageInfo.getLastPulledAt())
                .isArchived(packageInfo.getIsArchived())
                .operatingSystems(packageInfo.getOperatingSystems())
                .architectures(packageInfo.getArchitectures())
                .categories(packageInfo.getCategories())
                .build();
    }
}