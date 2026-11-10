package kr.co.mcmp.softwarecatalog.application.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import kr.co.mcmp.ape.cbtumblebug.api.CbtumblebugRestApi;
import kr.co.mcmp.ape.cbtumblebug.dto.VmAccessInfo;
import kr.co.mcmp.softwarecatalog.application.dto.DeploymentRequest;
import kr.co.mcmp.softwarecatalog.application.exception.ApplicationException;
import kr.co.mcmp.softwarecatalog.application.model.DeploymentHistory;
import kr.co.mcmp.softwarecatalog.application.model.VmSecurityGroupExposure;
import kr.co.mcmp.softwarecatalog.application.repository.VmSecurityGroupExposureRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Owns the lifecycle of VM application firewall rules added through
 * CB-Tumblebug's additive and exact-delete endpoints.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VmSecurityGroupExposureService {

    private final CbtumblebugRestApi cbtumblebugRestApi;
    private final VmSecurityGroupExposureRepository repository;
    private final ElasticsearchClusterNetworkService clusterNetworkService;

    /**
     * Adds and records one restricted inbound rule for a deployment. A rule
     * that existed before the deployment is recorded as operator-owned and is
     * therefore never removed by Application Manager.
     */
    public synchronized void addRestrictedInboundRule(
            DeploymentRequest request,
            VmAccessInfo vmAccessInfo,
            DeploymentHistory deploymentHistory) {
        if (!Boolean.TRUE.equals(request.getOpenServicePort())) {
            return;
        }
        if (deploymentHistory == null || deploymentHistory.getId() == null) {
            throw new ApplicationException("Deployment history is required to track direct access");
        }
        if (repository.findByDeploymentId(deploymentHistory.getId()).isPresent()) {
            log.info("VM Security Group exposure is already tracked for deploymentId={}",
                    deploymentHistory.getId());
            return;
        }

        int port = validatePort(request.getServicePort());
        String cidr = validateRestrictedIpv4Cidr(request.getServicePortCidr());
        String securityGroupId = resolveSingleAttachedSecurityGroup(vmAccessInfo);
        String namespace = requiredNamespace(request.getNamespace());

        List<VmSecurityGroupExposure> sharedExposures = repository
                .findByNamespaceAndSecurityGroupIdAndServicePortAndAllowedCidrAndReleasedAtIsNull(
                        namespace,
                        securityGroupId,
                        port,
                        cidr);
        boolean ruleAlreadyExists = cbtumblebugRestApi.hasInboundTcpFirewallRule(
                namespace,
                securityGroupId,
                port,
                cidr);
        boolean managedByApplicationManager = !ruleAlreadyExists
                || sharedExposures.stream()
                        .anyMatch(exposure -> Boolean.TRUE.equals(exposure.getManagedByApplicationManager()));

        VmSecurityGroupExposure exposure = repository.saveAndFlush(VmSecurityGroupExposure.builder()
                .deploymentId(deploymentHistory.getId())
                .vmId(vmAccessInfo.getId())
                .namespace(namespace)
                .securityGroupId(securityGroupId)
                .servicePort(port)
                .allowedCidr(cidr)
                .managedByApplicationManager(managedByApplicationManager)
                .createdAt(LocalDateTime.now())
                .build());

        try {
            if (!ruleAlreadyExists) {
                cbtumblebugRestApi.addInboundTcpFirewallRule(
                        namespace,
                        securityGroupId,
                        port,
                        cidr);
            }
            exposure.setProvisionedAt(LocalDateTime.now());
            repository.saveAndFlush(exposure);
        } catch (RuntimeException e) {
            repository.delete(exposure);
            throw e;
        }

        log.info(
                "Tracked restricted inbound TCP rule: deploymentId={}, vmId={}, securityGroupId={}, port={}, cidr={}, managedByApplicationManager={}",
                deploymentHistory.getId(),
                vmAccessInfo.getId(),
                securityGroupId,
                port,
                cidr,
                managedByApplicationManager);
    }

    /**
     * Releases the rule binding for one deployment. Shared rules remain until
     * the last dependent deployment is removed. Ownership is transferred to
     * the remaining bindings so deletion order cannot leak an AM-created rule.
     */
    public synchronized void releaseRestrictedInboundRule(Long deploymentId) {
        if (deploymentId == null) {
            return;
        }
        clusterNetworkService.release(deploymentId);

        VmSecurityGroupExposure exposure = repository
                .findByDeploymentIdAndReleasedAtIsNull(deploymentId)
                .orElse(null);
        if (exposure == null) {
            return;
        }

        List<VmSecurityGroupExposure> remaining = repository
                .findByNamespaceAndSecurityGroupIdAndServicePortAndAllowedCidrAndReleasedAtIsNull(
                        exposure.getNamespace(),
                        exposure.getSecurityGroupId(),
                        exposure.getServicePort(),
                        exposure.getAllowedCidr())
                .stream()
                .filter(candidate -> !candidate.getId().equals(exposure.getId()))
                .toList();

        LocalDateTime now = LocalDateTime.now();
        if (!remaining.isEmpty()) {
            if (Boolean.TRUE.equals(exposure.getManagedByApplicationManager())) {
                remaining.forEach(candidate -> candidate.setManagedByApplicationManager(true));
                repository.saveAllAndFlush(remaining);
            }
            exposure.setReleasedAt(now);
            repository.saveAndFlush(exposure);
            log.info("Kept shared inbound rule for {} remaining deployment(s): securityGroupId={}, port={}, cidr={}",
                    remaining.size(),
                    exposure.getSecurityGroupId(),
                    exposure.getServicePort(),
                    exposure.getAllowedCidr());
            return;
        }

        if (Boolean.TRUE.equals(exposure.getManagedByApplicationManager())
                && !clusterNetworkService.retainIfRequired(exposure.getNamespace(), exposure.getSecurityGroupId(),
                        exposure.getServicePort(), exposure.getAllowedCidr())) {
            cbtumblebugRestApi.deleteInboundTcpFirewallRule(
                    exposure.getNamespace(),
                    exposure.getSecurityGroupId(),
                    exposure.getServicePort(),
                    exposure.getAllowedCidr());
            exposure.setRuleRemovedAt(now);
            log.info("Removed Application Manager inbound rule: securityGroupId={}, port={}, cidr={}",
                    exposure.getSecurityGroupId(),
                    exposure.getServicePort(),
                    exposure.getAllowedCidr());
        } else {
            log.info("Preserved pre-existing inbound rule: securityGroupId={}, port={}, cidr={}",
                    exposure.getSecurityGroupId(),
                    exposure.getServicePort(),
                    exposure.getAllowedCidr());
        }

        exposure.setReleasedAt(now);
        repository.saveAndFlush(exposure);
    }

    private int validatePort(Integer port) {
        if (port == null || port < 1 || port > 65535) {
            throw new ApplicationException("A service port between 1 and 65535 is required for direct access");
        }
        return port;
    }

    public static String validateRestrictedIpv4Cidr(String cidr) {
        if (cidr == null || cidr.isBlank()) {
            throw new ApplicationException("A restricted IPv4 CIDR is required for direct access");
        }

        String normalized = cidr.trim();
        String[] cidrParts = normalized.split("/", -1);
        if (cidrParts.length != 2) {
            throw new ApplicationException("Service port CIDR must use IPv4 CIDR notation, for example 203.0.113.10/32");
        }

        String[] octets = cidrParts[0].split("\\.", -1);
        if (octets.length != 4) {
            throw new ApplicationException("Service port CIDR must be a valid IPv4 CIDR");
        }
        for (String octet : octets) {
            try {
                int value = Integer.parseInt(octet);
                if (value < 0 || value > 255 || !String.valueOf(value).equals(octet)) {
                    throw new NumberFormatException("invalid IPv4 octet");
                }
            } catch (NumberFormatException e) {
                throw new ApplicationException("Service port CIDR must be a valid IPv4 CIDR");
            }
        }

        try {
            int prefixLength = Integer.parseInt(cidrParts[1]);
            if (prefixLength < 1 || prefixLength > 32) {
                throw new ApplicationException("Public any CIDR is not allowed; use a prefix between /1 and /32");
            }
        } catch (NumberFormatException e) {
            throw new ApplicationException("Service port CIDR must contain a valid prefix length");
        }
        return normalized;
    }

    private String resolveSingleAttachedSecurityGroup(VmAccessInfo vmAccessInfo) {
        if (vmAccessInfo == null) {
            throw new ApplicationException("VM information is required to configure direct access");
        }

        List<String> securityGroupIds = vmAccessInfo.getSecurityGroupIds() == null
                ? List.of()
                : vmAccessInfo.getSecurityGroupIds().stream()
                        .filter(id -> id != null && !id.isBlank())
                        .map(String::trim)
                        .distinct()
                        .toList();

        if (securityGroupIds.isEmpty()) {
            throw new ApplicationException("The target VM has no attached Security Group");
        }
        if (securityGroupIds.size() != 1) {
            throw new ApplicationException(
                    "The target VM has multiple Security Groups; Application Manager will not choose one automatically");
        }
        return securityGroupIds.get(0);
    }

    private String requiredNamespace(String namespace) {
        if (namespace == null || namespace.isBlank()) {
            throw new ApplicationException("Namespace is required to configure direct access");
        }
        return namespace.trim();
    }
}
