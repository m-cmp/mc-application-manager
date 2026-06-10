package kr.co.mcmp.softwarecatalog.application.dto;

import java.time.LocalDateTime;

import kr.co.mcmp.softwarecatalog.application.constants.ActionType;
import kr.co.mcmp.softwarecatalog.application.constants.DeploymentType;
import kr.co.mcmp.softwarecatalog.application.model.DeploymentHistory;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class DeploymentHistoryDTO {

    private Long id;
    private Long catalogId;
    private String catalogName;
    private DeploymentType deploymentType;
    private String namespace;
    private String mciId;
    private String vmId;
    private String clusterName;
    private String nodeGroupName;
    private String publicIp;
    private ActionType actionType;
    private String status;
    private LocalDateTime executedAt;
    private LocalDateTime updatedAt;
    private Long executedById;
    private String cloudProvider;
    private String uid;
    private String cloudRegion;
    private Integer servicePort;
    private String podStatus;
    private String releaseName;
    private Boolean hpaEnabled;
    private Integer minReplicas;
    private Integer maxReplicas;
    private Double cpuThreshold;
    private Double memoryThreshold;
    private Boolean ingressEnabled;
    private String ingressHost;
    private String ingressPath;
    private String ingressClass;
    private Boolean ingressTlsEnabled;
    private String ingressTlsSecret;
    private String resourceType;

    public DeploymentHistoryDTO(DeploymentHistory entity) {
        this.id = entity.getId();
        this.catalogId = entity.getCatalog() != null ? entity.getCatalog().getId() : null;
        this.catalogName = entity.getCatalog() != null ? entity.getCatalog().getName() : null;
        this.deploymentType = entity.getDeploymentType();
        this.namespace = entity.getNamespace();
        this.mciId = entity.getMciId();
        this.vmId   = entity.getVmId();
        this.clusterName = entity.getClusterName();
        this.nodeGroupName = entity.getNodeGroupName();
        this.publicIp = entity.getPublicIp();
        this.actionType = entity.getActionType();
        this.status = entity.getStatus();
        this.executedAt = entity.getExecutedAt();
        this.updatedAt = entity.getUpdatedAt();
        this.executedById = entity.getExecutedBy() != null ? entity.getExecutedBy().getId() : null;
        this.cloudProvider = entity.getCloudProvider();
        this.uid = entity.getUid();
        this.cloudRegion = entity.getCloudRegion();
        this.servicePort = entity.getServicePort();
        this.podStatus = entity.getPodStatus();
        this.releaseName = entity.getReleaseName();
        this.hpaEnabled = entity.getHpaEnabled();
        this.minReplicas = entity.getMinReplicas();
        this.maxReplicas = entity.getMaxReplicas();
        this.cpuThreshold = entity.getCpuThreshold();
        this.memoryThreshold = entity.getMemoryThreshold();
        this.ingressEnabled = entity.getIngressEnabled();
        this.ingressHost = entity.getIngressHost();
        this.ingressPath = entity.getIngressPath();
        this.ingressClass = entity.getIngressClass();
        this.ingressTlsEnabled = entity.getIngressTlsEnabled();
        this.ingressTlsSecret = entity.getIngressTlsSecret();
        this.resourceType = entity.getResourceType();
    }
}
