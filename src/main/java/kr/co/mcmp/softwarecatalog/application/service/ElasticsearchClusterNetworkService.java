package kr.co.mcmp.softwarecatalog.application.service;

import java.time.LocalDateTime;
import java.util.*;
import org.springframework.stereotype.Service;
import kr.co.mcmp.ape.cbtumblebug.api.CbtumblebugRestApi;
import kr.co.mcmp.ape.cbtumblebug.dto.MciDto;
import kr.co.mcmp.ape.cbtumblebug.dto.VmAccessInfo;
import kr.co.mcmp.softwarecatalog.application.constants.VmDeploymentMode;
import kr.co.mcmp.softwarecatalog.application.dto.DeploymentRequest;
import kr.co.mcmp.softwarecatalog.application.model.DeploymentHistory;
import kr.co.mcmp.softwarecatalog.application.model.VmClusterFirewallRule;
import kr.co.mcmp.softwarecatalog.application.repository.VmClusterFirewallRuleRepository;
import kr.co.mcmp.softwarecatalog.application.repository.VmSecurityGroupExposureRepository;
import lombok.RequiredArgsConstructor;

/** Additive, peer-only TCP 9300 rules. Never replace a Security Group's rule set. */
@Service
@RequiredArgsConstructor
public class ElasticsearchClusterNetworkService {
    private static final int TRANSPORT_PORT = 9300;
    private final CbtumblebugRestApi tumblebug;
    private final VmClusterFirewallRuleRepository repository;
    private final VmSecurityGroupExposureRepository serviceExposures;

    public synchronized void prepare(DeploymentRequest request, DeploymentHistory history) {
        if (request.getVmDeploymentMode() != VmDeploymentMode.CLUSTERING
                || request.getVmIds() == null || request.getVmIds().size() < 2) return;
        if (history.getId() == null || request.getNamespace() == null || request.getNamespace().isBlank())
            throw new IllegalArgumentException("Deployment history and namespace are required for cluster access");
        Set<String> selected = new LinkedHashSet<>(request.getVmIds());
        if (selected.size() != request.getVmIds().size() || selected.contains(null) || selected.contains(""))
            throw new IllegalArgumentException("Cluster targets must be distinct VM IDs");
        List<MciDto> infras = tumblebug.getMcisByNamespace(request.getNamespace());
        if (infras == null) throw new IllegalStateException("Cannot verify cluster Security Group membership");
        MciDto infra = infras.stream().filter(i -> request.getMciId().equals(i.getId())).findFirst()
                .orElseThrow(() -> new IllegalStateException("Cluster Infra was not found"));
        Map<String, VmAccessInfo> nodes = new HashMap<>();
        if (infra.getNode() != null) infra.getNode().forEach(vm -> nodes.put(vm.getId(), vm));
        // Validate every target and every shared group before changing any rule.
        Map<String, String> peers = new LinkedHashMap<>();
        Map<String, String> groups = new HashMap<>();
        for (String id : selected) {
            VmAccessInfo vm = nodes.get(id);
            if (vm == null) throw new IllegalStateException("Cluster VM was not found: " + id);
            String cidr = peerCidr(vm.getPublicIP()); // Same addresses as discovery.seed_hosts / network.publish_host.
            if (peers.containsValue(cidr)) throw new IllegalStateException("Cluster VMs must have distinct peer IPs");
            peers.put(id, cidr);
            List<String> securityGroups = vm.getSecurityGroupIds() == null ? List.of()
                    : vm.getSecurityGroupIds().stream().filter(Objects::nonNull).filter(s -> !s.isBlank()).distinct().toList();
            if (securityGroups.size() != 1)
                throw new IllegalStateException("Cluster VM " + id + " must have exactly one Security Group; refusing to choose or broaden access");
            groups.put(id, securityGroups.get(0));
        }
        for (MciDto other : infras) {
            if (other.getNode() == null) continue;
            for (VmAccessInfo vm : other.getNode()) {
                if (request.getMciId().equals(other.getId()) && selected.contains(vm.getId())) continue;
                if ("Terminated".equalsIgnoreCase(vm.getStatus())) continue;
                if (vm.getSecurityGroupIds() != null && vm.getSecurityGroupIds().stream().anyMatch(groups::containsValue))
                    throw new IllegalStateException("Cluster Security Group is shared with an unselected VM; use a dedicated group before deployment");
            }
        }
        String group = groups.get(history.getVmId());
        if (group == null) throw new IllegalStateException("Deployment VM is outside the selected cluster");
        try {
            for (Map.Entry<String, String> peer : peers.entrySet()) {
                if (peer.getKey().equals(history.getVmId())) continue;
                if (repository.findByDeploymentIdAndReleasedAtIsNull(history.getId()).stream()
                        .anyMatch(r -> group.equals(r.getSecurityGroupId()) && peer.getValue().equals(r.getAllowedCidr()))) continue;
                List<VmClusterFirewallRule> shared = repository
                        .findByNamespaceAndSecurityGroupIdAndAllowedCidrAndReleasedAtIsNull(request.getNamespace(), group, peer.getValue());
                boolean exists = tumblebug.hasInboundTcpFirewallRule(request.getNamespace(), group, TRANSPORT_PORT, peer.getValue());
                VmClusterFirewallRule rule = repository.saveAndFlush(VmClusterFirewallRule.builder()
                        .deploymentId(history.getId()).namespace(request.getNamespace()).securityGroupId(group)
                        .allowedCidr(peer.getValue()).managed(!exists || shared.stream().anyMatch(VmClusterFirewallRule::isManaged)).build());
                if (!exists) tumblebug.addInboundTcpFirewallRule(request.getNamespace(), group, TRANSPORT_PORT, rule.getAllowedCidr());
            }
        } catch (RuntimeException failure) {
            try { release(history.getId()); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    public synchronized void release(Long deploymentId) {
        if (deploymentId == null) return;
        for (VmClusterFirewallRule rule : repository.findByDeploymentIdAndReleasedAtIsNull(deploymentId)) {
            List<VmClusterFirewallRule> remaining = repository
                    .findByNamespaceAndSecurityGroupIdAndAllowedCidrAndReleasedAtIsNull(
                            rule.getNamespace(), rule.getSecurityGroupId(), rule.getAllowedCidr()).stream()
                    .filter(r -> !Objects.equals(r.getId(), rule.getId())).toList();
            var services = serviceExposures.findByNamespaceAndSecurityGroupIdAndServicePortAndAllowedCidrAndReleasedAtIsNull(
                    rule.getNamespace(), rule.getSecurityGroupId(), TRANSPORT_PORT, rule.getAllowedCidr());
            if (rule.isManaged() && !remaining.isEmpty()) {
                remaining.forEach(r -> r.setManaged(true));
                repository.saveAllAndFlush(remaining);
            } else if (rule.isManaged() && !services.isEmpty()) {
                services.forEach(r -> r.setManagedByApplicationManager(true));
                serviceExposures.saveAllAndFlush(services);
            } else if (rule.isManaged() && tumblebug.hasInboundTcpFirewallRule(
                    rule.getNamespace(), rule.getSecurityGroupId(), TRANSPORT_PORT, rule.getAllowedCidr())) {
                tumblebug.deleteInboundTcpFirewallRule(rule.getNamespace(), rule.getSecurityGroupId(), TRANSPORT_PORT, rule.getAllowedCidr());
            }
            rule.setReleasedAt(LocalDateTime.now());
            repository.saveAndFlush(rule);
        }
    }

    /** A user-selected service port may also be 9300; do not remove a cluster's active rule. */
    public synchronized boolean retainIfRequired(String namespace, String group, int port, String cidr) {
        if (port != TRANSPORT_PORT) return false;
        var active = repository.findByNamespaceAndSecurityGroupIdAndAllowedCidrAndReleasedAtIsNull(namespace, group, cidr);
        if (active.isEmpty()) return false;
        active.forEach(r -> r.setManaged(true));
        repository.saveAllAndFlush(active);
        return true;
    }

    private String peerCidr(String ip) {
        if (ip == null) throw new IllegalStateException("Cluster peer has no reachable IPv4 address");
        String cidr = VmSecurityGroupExposureService.validateRestrictedIpv4Cidr(ip + "/32");
        int first = Integer.parseInt(ip.split("\\.")[0]);
        if (first == 0 || first == 127 || first >= 224 || ip.startsWith("169.254."))
            throw new IllegalStateException("Invalid cluster peer IPv4 address");
        return cidr;
    }
}
