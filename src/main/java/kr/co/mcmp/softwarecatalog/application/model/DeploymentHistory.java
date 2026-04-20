package kr.co.mcmp.softwarecatalog.application.model;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import kr.co.mcmp.softwarecatalog.SoftwareCatalog;
import kr.co.mcmp.softwarecatalog.application.constants.ActionType;
import kr.co.mcmp.softwarecatalog.application.constants.DeploymentType;
import kr.co.mcmp.softwarecatalog.users.Entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "DEPLOYMENT_HISTORY")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeploymentHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // 배포 이력의 고유 식별자

    @ManyToOne
    @JoinColumn(name = "catalog_id")
    private SoftwareCatalog catalog; // 배포된 소프트웨어 카탈로그

    @Enumerated(EnumType.STRING)
    @Column(name = "deployment_type", nullable = false)
    private DeploymentType deploymentType; // 배포 유형 (예: K8S, VM)

    @Column(name="namespace")
    private String namespace;

    @Column(name = "mci_id")
    private String mciId; // VM 배포에 사용

    @Column(name = "vm_id")
    private String vmId; // VM 배포에 사용

    @Column(name = "cluster_name")
    private String clusterName; // K8s 배포에 사용
    
    @Column(name = "node_group_name")
    private String nodeGroupName; // K8s 노드 그룹 이름

    @Column(name = "public_ip")
    private String publicIp;   

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false)
    private ActionType actionType; // 수행된 작업 유형 (예: INSTALL, UNINSTALL, RUN, RESTART, STOP)

    @Column(nullable = false)
    private String status; // 배포 상태 (예: 성공, 실패, 진행 중)

    @Column(name = "executed_at", nullable = false)
    private LocalDateTime executedAt; // 배포 작업 실행 시간
    
    @Column(name = "updated_at", nullable = true)
    private LocalDateTime updatedAt; // 배포 작업 업데이트 시간

    @ManyToOne
    @JoinColumn(name = "executed_by")
    private User  executedBy; // 배포 작업을 실행한 사용자

    @Column(name = "cloud_provider")
    private String cloudProvider; // 클라우드 제공자

    @Column(name= "uid")
    private String uid; // uid;

    @Column(name = "cloud_region")
    private String cloudRegion; // 클라우드 리전

    @Column(name = "service_port")
    private Integer servicePort; // 서비스 포트 (VM 전용)

    @Column(name = "pod_status")
    private String podStatus; // Pod 상태 (K8S 전용)
    
    @Column(name = "release_name")
    private String releaseName; // Helm 릴리스 이름 (K8S 전용)

    /**
     * Resource type selected by the user at deploy time.
     * Values: CPU_INTENSIVE, MEMORY_INTENSIVE, GENERAL_PURPOSE
     * Used as the baseline for workload_profile_result.selected_type.
     */
    @Column(name = "resource_type", length = 50)
    private String resourceType;
}