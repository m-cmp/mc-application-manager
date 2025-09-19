package kr.co.mcmp.softwarecatalog.application.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import kr.co.mcmp.softwarecatalog.SoftwareCatalog;
import kr.co.mcmp.softwarecatalog.application.constants.DeploymentType;
import kr.co.mcmp.softwarecatalog.users.Entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "APPLICATION_STATUS")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationStatus {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "namespace")
    private String namespace;

    @Column(name = "mci_id")
    private String mciId;

    @Column(name = "cluster_name")
    private String clusterName;

    @Column(name ="vm_id")
    private String vmId;

    @ManyToOne
    @JoinColumn(name = "catalog_id")
    private SoftwareCatalog catalog;

    @Column(name = "deployment_history_id")
    private Long deploymentHistoryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "deployment_type")
    private DeploymentType deploymentType; // VM or K8S

    @Column(name = "service_port")
    private Integer servicePort;

    @Column(name="is_port_accessible")
    private Boolean isPortAccessible;

    @Column(name = "is_health_check")
    private Boolean isHealthCheck;


    @Column(name = "status")
    private String status;

    @Column(name = "cpu_usage")
    private Double cpuUsage;

    @Column(name = "memory_usage")
    private Double memoryUsage;
    
    @Column(name = "network_in")
    private Double networkIn;

    @Column(name="public_ip")
    private String publicIp;

    @Column(name = "network_out")
    private Double networkOut;

    @Column(name = "pod_status")
    private String podStatus; // For K8S deployments
    
    @Column(name = "checked_at")
    private LocalDateTime checkedAt;
    
    @ManyToOne
    @JoinColumn(name = "executed_by")
    private User executedBy;

    @ElementCollection
    @CollectionTable(name = "APPLICATION_ERROR_LOGS", joinColumns = @JoinColumn(name = "application_status_id"))
    @Column(name = "error_log", length = 2000)
    private List<String> errorLogs = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "APPLICATION_INFO_LOGS", joinColumns = @JoinColumn(name = "application_status_id"))
    @Column(name = "info_log", length = 2000)
    private List<String> infoLogs = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "APPLICATION_DEBUG_LOGS", joinColumns = @JoinColumn(name = "application_status_id"))
    @Column(name = "debug_log", length = 2000)
    private List<String> debugLogs = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "APPLICATION_POD_LOGS", joinColumns = @JoinColumn(name = "application_status_id"))
    @Column(name = "pod_log", length = 2000)
    private List<String> podLogs = new ArrayList<>();

    // 로그 중복 방지를 위한 해시 필드들
    @Column(name = "error_logs_hash")
    private String errorLogsHash;
    
    @Column(name = "info_logs_hash")
    private String infoLogsHash;
    
    @Column(name = "debug_logs_hash")
    private String debugLogsHash;
    
    @Column(name = "pod_logs_hash")
    private String podLogsHash;

}
