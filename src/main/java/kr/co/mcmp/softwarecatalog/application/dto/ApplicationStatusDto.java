package kr.co.mcmp.softwarecatalog.application.dto;

import java.time.LocalDateTime;

import kr.co.mcmp.softwarecatalog.SoftwareCatalog;
import kr.co.mcmp.softwarecatalog.application.constants.DeploymentType;
import kr.co.mcmp.softwarecatalog.application.model.ApplicationStatus;
import kr.co.mcmp.softwarecatalog.users.Entity.User;
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
public class ApplicationStatusDto {
    private Long id;
    private Long catalogId;
    private Long deploymentHistoryId;
    private String applicationName;
    private DeploymentType deploymentType;
    private String namespace;
    private String mciId;
    private String vmId;
    private String clusterName;
    private String status;
    private Double cpuUsage;
    private Double memoryUsage;
    private Double networkIn;
    private Boolean isPortAccessible;
    private Boolean isHealthCheck;
    private String publicIp;
    private Double networkOut;
    private Integer servicePort;
    private String podStatus;
    private LocalDateTime checkedAt;
    private Long executedById;

    public static ApplicationStatusDto fromEntity(ApplicationStatus entity) {
        return ApplicationStatusDto.builder()
                .id(entity.getId())
                .catalogId(entity.getCatalog() != null ? entity.getCatalog().getId() : null)
                .deploymentHistoryId(entity.getDeploymentHistoryId())
                .applicationName(entity.getCatalog() != null ? entity.getCatalog().getName() : null)
                .deploymentType(entity.getDeploymentType())
                .namespace(entity.getNamespace())
                .mciId(entity.getMciId())
                .vmId(entity.getVmId())
                .clusterName(entity.getClusterName())
                .isPortAccessible(entity.getIsPortAccessible())
                .isHealthCheck(entity.getIsHealthCheck())
                .status(entity.getStatus())
                .cpuUsage(entity.getCpuUsage())
                .memoryUsage(entity.getMemoryUsage())
                .networkIn(entity.getNetworkIn())
                .publicIp(entity.getPublicIp())
                .networkOut(entity.getNetworkOut())
                .servicePort(entity.getServicePort())
                .podStatus(entity.getPodStatus())
                .checkedAt(entity.getCheckedAt())
                .executedById(entity.getExecutedBy() != null ? entity.getExecutedBy().getId() : null)
                .build();
    }

    public ApplicationStatus toEntity() {
        ApplicationStatus entity = new ApplicationStatus();
        entity.setId(this.id);
        entity.setDeploymentType(this.deploymentType);
        entity.setStatus(this.status);
        entity.setCpuUsage(this.cpuUsage);
        entity.setMemoryUsage(this.memoryUsage);
        entity.setNetworkIn(this.networkIn);
        entity.setPublicIp(this.publicIp);
        entity.setNetworkOut(this.networkOut);
        entity.setServicePort(this.servicePort);
        entity.setPodStatus(this.podStatus);
        entity.setCheckedAt(this.checkedAt);

        if (this.catalogId != null) {
            SoftwareCatalog catalog = new SoftwareCatalog();
            catalog.setId(this.catalogId);
            entity.setCatalog(catalog);
        }
        if (this.executedById != null) {
            User executedBy = new User();
            executedBy.setId(this.executedById);
            entity.setExecutedBy(executedBy);
        }

        return entity;
    }
}