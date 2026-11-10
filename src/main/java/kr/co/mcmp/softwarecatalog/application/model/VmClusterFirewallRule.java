package kr.co.mcmp.softwarecatalog.application.model;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.*;

/** Separate from the existing single service-port exposure per deployment. */
@Entity
@Table(name = "VM_CLUSTER_FIREWALL_RULE", indexes = {
        @Index(name = "idx_vm_cluster_rule_deployment", columnList = "deployment_id,released_at"),
        @Index(name = "idx_vm_cluster_rule_shared", columnList = "namespace_id,security_group_id,allowed_cidr,released_at")})
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class VmClusterFirewallRule {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "deployment_id", nullable = false)
    private Long deploymentId;
    @Column(name = "namespace_id", nullable = false)
    private String namespace;
    @Column(name = "security_group_id", nullable = false)
    private String securityGroupId;
    @Column(name = "allowed_cidr", nullable = false)
    private String allowedCidr;
    @Column(nullable = false)
    private boolean managed;
    @Column(name = "released_at")
    private LocalDateTime releasedAt;
}
