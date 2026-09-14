package kr.co.mcmp.softwarecatalog.kubernetes.service;

import java.util.List;
import org.springframework.stereotype.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import kr.co.mcmp.ape.cbtumblebug.api.CbtumblebugRestApi;
import kr.co.mcmp.ape.cbtumblebug.dto.VmAccessInfo;
import kr.co.mcmp.softwarecatalog.application.dto.DeploymentRequest;
import kr.co.mcmp.softwarecatalog.application.model.DeploymentHistory;
import kr.co.mcmp.softwarecatalog.application.service.VmSecurityGroupExposureService;
import lombok.RequiredArgsConstructor;

/** Shared NodePort firewall ownership uses the same ledger as VM applications. */
@Service
@RequiredArgsConstructor
public class K8sIngressAccessService {
    public static final int HTTP_NODE_PORT = 30880;
    public static final String CIDR_ANNOTATION = "nginx.ingress.kubernetes.io/whitelist-source-range";
    private final CbtumblebugRestApi tumblebug;
    private final VmSecurityGroupExposureService exposure;
    private final K8sWorkerSecurityGroupResolver workerGroups;

    public static String validateCidr(String cidr) {
        return VmSecurityGroupExposureService.validateRestrictedIpv4Cidr(cidr);
    }

    public void resolveTarget(DeploymentRequest request, kr.co.mcmp.softwarecatalog.SoftwareCatalog catalog) {
        var config = kr.co.mcmp.softwarecatalog.application.dto.DeploymentConfigDTO.from(request, catalog);
        if (!config.isIngressEnabled()) return;
        var cluster = tumblebug.getK8sClusterByName(request.getNamespace(), request.getClusterName());
        if (cluster == null) throw new IllegalArgumentException("Kubernetes cluster was not found.");
        request.setIngressClass(IbmIngressSupport.resolve(cluster, config).getIngressClass());
    }

    public void open(DeploymentRequest request, DeploymentHistory history) {
        if (!Boolean.TRUE.equals(request.getOpenServicePort())) return;
        var cluster = tumblebug.getK8sClusterByName(request.getNamespace(), request.getClusterName());
        validateCidr(request.getServicePortCidr());
        if (IbmIngressSupport.isIbm(cluster)) return; // IBM owns shared LB/worker firewall rules.
        String id = workerGroups.resolve(request.getNamespace(), cluster);
        // The existing exposure service refuses ambiguous SGs and preserves shared/operator rules.
        VmAccessInfo target = new VmAccessInfo();
        target.setId("k8s:" + request.getClusterName());
        target.setSecurityGroupIds(List.of(id));
        var firewall = DeploymentRequest.builder().namespace(request.getNamespace())
                .openServicePort(true).servicePort(HTTP_NODE_PORT)
                .servicePortCidr(validateCidr(request.getServicePortCidr())).build();
        exposure.addRestrictedInboundRule(firewall, target, history);
    }

    public void release(Long deploymentId) { exposure.releaseRestrictedInboundRule(deploymentId); }

    public void verifyController(KubernetesClient client, String namespace) {
        var services = client.services().inAnyNamespace().list().getItems().stream()
                .filter(s -> s.getSpec() != null && s.getSpec().getPorts() != null)
                .filter(s -> s.getSpec().getPorts().stream().anyMatch(p -> Integer.valueOf(HTTP_NODE_PORT).equals(p.getNodePort())))
                .toList();
        if (services.size() != 1) throw new IllegalStateException("Expected one Ingress Controller Service on NodePort 30880.");
        var service = services.get(0);
        if (service.getMetadata().getLabels() == null || !"ingress-nginx".equals(service.getMetadata().getLabels().get("app.kubernetes.io/name")))
            throw new IllegalStateException("NodePort 30880 is not owned by ingress-nginx.");
        if (!"Local".equals(service.getSpec().getExternalTrafficPolicy()))
            throw new IllegalStateException("Ingress Controller needs externalTrafficPolicy=Local to enforce client CIDRs. Update the shared controller configuration first.");
    }
}
