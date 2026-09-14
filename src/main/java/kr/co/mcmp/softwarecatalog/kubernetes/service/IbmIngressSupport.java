package kr.co.mcmp.softwarecatalog.kubernetes.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import io.fabric8.kubernetes.api.model.Service;

import io.fabric8.kubernetes.client.KubernetesClient;
import kr.co.mcmp.ape.cbtumblebug.dto.K8sClusterDto;
import kr.co.mcmp.softwarecatalog.application.dto.DeploymentConfigDTO;

/** Read-only verification. Opt-in provisioning is isolated in IbmIngressAutomationService. */
final class IbmIngressSupport {
    static final String PUBLIC_CLASS = "public-iks-k8s-nginx";
    static final String PRIVATE_CLASS = "private-iks-k8s-nginx";
    static final String PROXY_FEATURE = "service.kubernetes.io/ibm-load-balancer-cloud-provider-enable-features";

    private IbmIngressSupport() {}

    static boolean isIbm(K8sClusterDto cluster) {
        return cluster != null && cluster.getConnectionConfig() != null
                && isIbm(cluster.getConnectionConfig().getProviderName());
    }

    static boolean isIbm(String provider) {
        return provider != null && Set.of("ibm", "ibmcloud", "ibm-cloud", "ibm-vpc", "ibmvpc")
                .contains(provider.toLowerCase(Locale.ROOT));
    }

    static boolean managed(String ingressClass) {
        return PUBLIC_CLASS.equals(ingressClass) || PRIVATE_CLASS.equals(ingressClass);
    }

    static DeploymentConfigDTO resolve(K8sClusterDto cluster, DeploymentConfigDTO config) {
        if (!config.isIngressEnabled()) return config;
        String clazz = config.getIngressClass();
        if (isIbm(cluster)) {
            // Existing catalogs and API clients use nginx; select IBM's public managed entry by default.
            if (clazz == null || "nginx".equals(clazz)) clazz = PUBLIC_CLASS;
            if (!managed(clazz)) throw new IllegalArgumentException("IBM requires a managed NGINX Ingress class: " + PUBLIC_CLASS + " or " + PRIVATE_CLASS);
        } else if (managed(clazz)) {
            throw new IllegalArgumentException("IBM managed Ingress classes require an IBM cluster.");
        }
        return config.toBuilder().ingressClass(clazz).build();
    }

    /** Read-only, fail closed before deploying a CIDR-restricted route. */
    static List<String> verify(KubernetesClient client, DeploymentConfigDTO config) {
        var warnings = verify(client, config.getIngressClass());
        String prefix = PUBLIC_CLASS.equals(config.getIngressClass()) ? "public-" : "private-";
        int port = config.isTlsEnabled() ? 443 : 80;
        boolean available = managedServices(client, prefix).stream()
                .allMatch(s -> s.getSpec().getPorts() != null && s.getSpec().getPorts().stream().anyMatch(p -> Integer.valueOf(port).equals(p.getPort())));
        if (!available) throw new MissingPortException("IBM managed ALB does not expose port " + port
                + ". Ask the cluster administrator to check the managed ALB listener configuration.");
        return warnings;
    }

    static List<String> verify(KubernetesClient client, String clazz) {
        verifyBase(client, clazz);
        String prefix = PUBLIC_CLASS.equals(clazz) ? "public-" : "private-";
        var services = managedServices(client, prefix);
        var cm = client.configMaps().inNamespace("kube-system").withName("ibm-k8s-controller-config").get();
        Map<String,String> data = cm == null || cm.getData() == null ? Map.of() : cm.getData();
        if (!"true".equalsIgnoreCase(data.get("use-proxy-protocol"))) {
            throw new MissingProxyException("IBM ALB source IP preservation is disabled. Configure an authorized AM automation profile or ask the cluster operator to enable PROXY protocol.");
        }
        String trusted = data.getOrDefault("proxy-real-ip-cidr", "");
        try {
            for (String cidr : trusted.split(",", -1)) K8sIngressAccessService.validateCidr(cidr);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("IBM ALB proxy-real-ip-cidr must contain valid restricted IPv4 LB/VPC subnet CIDRs, not all sources.");
        }
        var endpoints = new java.util.ArrayList<String>();
        for (var service : services) {
            var annotations = service.getMetadata().getAnnotations();
            String features = annotations == null ? "" : annotations.getOrDefault(PROXY_FEATURE, "");
            if (!List.of(features.split(",")).stream().map(String::trim).anyMatch("proxy-protocol"::equals)) {
                throw new MissingProxyException("IBM ALB Service " + service.getMetadata().getName() + " does not enable PROXY protocol; CIDR enforcement cannot be guaranteed.");
            }
            int previousSize = endpoints.size();
            if (service.getStatus() != null && service.getStatus().getLoadBalancer() != null
                    && service.getStatus().getLoadBalancer().getIngress() != null) {
                for (var address : service.getStatus().getLoadBalancer().getIngress()) {
                    String endpoint = address.getHostname() != null ? address.getHostname() : address.getIp();
                    if (endpoint != null && !endpoint.isBlank()) endpoints.add(endpoint);
                }
            }
            if (endpoints.size() == previousSize) throw new IllegalArgumentException("IBM ALB Service "
                    + service.getMetadata().getName() + " has no ready external endpoint yet.");
        }
        return List.of("IBM managed ALB endpoint: " + String.join(", ", endpoints));
    }

    static void verifyBase(KubernetesClient client, String clazz) {
        var ingressClass = client.network().v1().ingressClasses().withName(clazz).get();
        if (ingressClass == null || ingressClass.getSpec() == null
                || !("cloud.ibm.com/" + clazz).equals(ingressClass.getSpec().getController())) {
            throw new IllegalArgumentException("IBM managed NGINX IngressClass " + clazz + " is unavailable. Enable the IBM NGINX ALB first.");
        }
        String prefix = PUBLIC_CLASS.equals(clazz) ? "public-" : "private-";
        var services = managedServices(client, prefix);
        if (services.isEmpty()) throw new IllegalArgumentException("No active IBM " + prefix + "ALB LoadBalancer Service was found.");
        var deployments = client.apps().deployments().inNamespace("kube-system")
                .withLabel("ingress-class", clazz).list().getItems();
        if (deployments.isEmpty() || deployments.stream().anyMatch(d -> d.getStatus() == null
                || d.getStatus().getAvailableReplicas() == null || d.getStatus().getAvailableReplicas() < 1)) {
            throw new IllegalArgumentException("IBM managed Ingress Controller is not ready for " + clazz + ".");
        }
    }

    static List<Service> managedServices(KubernetesClient client, String prefix) {
        return client.services().inNamespace("kube-system").list().getItems().stream()
                .filter(s -> s.getMetadata() != null && s.getMetadata().getName() != null
                        && s.getMetadata().getName().startsWith(prefix)
                        && "managed-ingress".equals(s.getMetadata().getLabels() == null ? null
                                : s.getMetadata().getLabels().get("app.kubernetes.io/part-of"))
                        && s.getSpec() != null && "LoadBalancer".equals(s.getSpec().getType())).toList();
    }

    static final class MissingProxyException extends IllegalArgumentException {
        MissingProxyException(String message) { super(message); }
    }
    static final class MissingPortException extends IllegalArgumentException {
        MissingPortException(String message) { super(message); }
    }
}
