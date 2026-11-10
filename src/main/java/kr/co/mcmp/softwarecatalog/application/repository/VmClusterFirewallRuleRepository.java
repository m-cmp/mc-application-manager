package kr.co.mcmp.softwarecatalog.application.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import kr.co.mcmp.softwarecatalog.application.model.VmClusterFirewallRule;

public interface VmClusterFirewallRuleRepository extends JpaRepository<VmClusterFirewallRule, Long> {
    List<VmClusterFirewallRule> findByDeploymentIdAndReleasedAtIsNull(Long deploymentId);
    List<VmClusterFirewallRule> findByNamespaceAndSecurityGroupIdAndAllowedCidrAndReleasedAtIsNull(
            String namespace, String securityGroupId, String allowedCidr);
}
