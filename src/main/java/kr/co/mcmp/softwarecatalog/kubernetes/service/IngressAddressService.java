package kr.co.mcmp.softwarecatalog.kubernetes.service;

import java.util.List;
import java.util.TreeSet;
import java.net.InetAddress;

import io.fabric8.kubernetes.client.KubernetesClient;
import kr.co.mcmp.softwarecatalog.application.constants.DeploymentType;
import kr.co.mcmp.softwarecatalog.application.model.DeploymentHistory;
import kr.co.mcmp.softwarecatalog.kubernetes.config.KubernetesClientFactory;
import kr.co.mcmp.ape.cbtumblebug.api.CbtumblebugRestApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Resolves the public addresses of the ingress controller installed by AM. */
@Service
@RequiredArgsConstructor
@Slf4j
public class IngressAddressService {
    private final KubernetesClientFactory clientFactory;
    private final CbtumblebugRestApi tumblebug;

    public record Endpoint(List<String> publicIps, List<Integer> accessPorts) {}

    public List<String> getPublicIps(DeploymentHistory history, Boolean enabled, String ingressClass) {
        return getEndpoint(history, enabled, ingressClass, false).publicIps();
    }

    public Endpoint getEndpoint(DeploymentHistory history, Boolean enabled, String ingressClass, Boolean tlsEnabled) {
        if (history.getDeploymentType() != DeploymentType.K8S || !Boolean.TRUE.equals(enabled)) {
            return new Endpoint(List.of(), List.of());
        }
        try (KubernetesClient client = clientFactory.getClient(history.getNamespace(), history.getClusterName())) {
            String provider = history.getCloudProvider();
            if ((provider == null || provider.isBlank()) && !IbmIngressSupport.managed(ingressClass)) {
                var cluster = tumblebug.getK8sClusterByName(history.getNamespace(), history.getClusterName());
                if (cluster != null && cluster.getConnectionConfig() != null) provider = cluster.getConnectionConfig().getProviderName();
            }
            List<String> ips = resolve(client, history.getNamespace(), provider, ingressClass);
            List<Integer> ports = List.of();
            try {
                ports = resolvePorts(client, history.getNamespace(), provider, ingressClass, Boolean.TRUE.equals(tlsEnabled));
            } catch (Exception e) {
                log.warn("Cannot resolve ingress ports for deployment {}: {}", history.getId(), e.getClass().getSimpleName());
            }
            return new Endpoint(ips, ports);
        } catch (Exception e) {
            // A missing cluster or denied Kubernetes permission must not break the detail dialog.
            log.warn("Cannot resolve ingress endpoint for deployment {}: {}", history.getId(), e.getClass().getSimpleName());
            return new Endpoint(List.of(), List.of());
        }
    }

    List<Integer> resolvePorts(KubernetesClient client, String namespace, String provider, String ingressClass, boolean tls) {
        boolean ibm = IbmIngressSupport.isIbm(provider) || IbmIngressSupport.managed(ingressClass);
        if (ibm && IbmIngressSupport.privateClass(ingressClass)) return List.of();
        var services = ibm ? IbmIngressSupport.servicesForClass(client, ingressClass)
                : client.services().inNamespace("default")
                    .withLabel("app.kubernetes.io/instance", "nginx-ingress-" + namespace)
                    .withLabel("app.kubernetes.io/component", "controller").list().getItems();
        var ports = new TreeSet<Integer>();
        for (var service : services) {
            if (service.getMetadata() == null || service.getMetadata().getDeletionTimestamp() != null
                    || service.getSpec() == null || service.getSpec().getPorts() == null) continue;
            for (var port : service.getSpec().getPorts()) {
                if (port.getProtocol() != null && !"TCP".equals(port.getProtocol())) continue;
                if (!(tls ? "https" : "http").equals(port.getName())
                        && !Integer.valueOf(tls ? 443 : 80).equals(port.getPort())) continue;
                // A worker address uses NodePort; IBM's public LB uses its service listener port.
                Integer accessPort = ibm ? port.getPort() : port.getNodePort();
                if (accessPort != null && accessPort > 0 && accessPort <= 65535) ports.add(accessPort);
            }
        }
        return List.copyOf(ports);
    }

    List<String> resolve(KubernetesClient client, String namespace, String provider, String ingressClass) {
        String release = "nginx-ingress-" + namespace;
        var addresses = new TreeSet<String>();
        if (IbmIngressSupport.isIbm(provider) || IbmIngressSupport.managed(ingressClass)) {
            // Private IBM ingress has no public entry point.
            if (IbmIngressSupport.privateClass(ingressClass)) return List.of();
            for (var service : IbmIngressSupport.servicesForClass(client, ingressClass)) {
                if (service.getStatus() != null && service.getStatus().getLoadBalancer() != null
                        && service.getStatus().getLoadBalancer().getIngress() != null) {
                    for (var entry : service.getStatus().getLoadBalancer().getIngress()) {
                        addPublicAddresses(addresses, entry.getIp());
                        if (entry.getIp() == null || entry.getIp().isBlank()) addPublicAddresses(addresses, entry.getHostname());
                    }
                }
            }
        } else {
            var pods = client.pods().inNamespace("default")
                    .withLabel("app.kubernetes.io/instance", release)
                    .withLabel("app.kubernetes.io/component", "controller").list().getItems();
            var nodeNames = new TreeSet<String>();
            for (var pod : pods) {
                if (pod.getMetadata().getDeletionTimestamp() == null && pod.getSpec() != null
                        && pod.getStatus() != null && "Running".equals(pod.getStatus().getPhase())) {
                    add(nodeNames, pod.getSpec().getNodeName());
                }
            }
            for (String name : nodeNames) {
                var node = client.nodes().withName(name).get();
                if (node != null && node.getStatus() != null && node.getStatus().getAddresses() != null) {
                    node.getStatus().getAddresses().stream()
                            .filter(address -> "ExternalIP".equals(address.getType()))
                            .forEach(address -> addPublicAddresses(addresses, address.getAddress()));
                }
            }
        }
        return List.copyOf(addresses);
    }

    private static void add(java.util.Set<String> values, String value) {
        if (value != null && !value.isBlank()) values.add(value.trim());
    }

    void addPublicAddresses(java.util.Set<String> values, String endpoint) {
        if (endpoint == null || endpoint.isBlank()) return;
        try {
            for (var address : lookup(endpoint.trim())) {
                byte[] bytes = address.getAddress();
                boolean privateV6 = bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
                boolean sharedV4 = bytes.length == 4 && (bytes[0] & 255) == 100 && (bytes[1] & 255) >= 64 && (bytes[1] & 255) <= 127;
                if (!address.isAnyLocalAddress() && !address.isLoopbackAddress() && !address.isLinkLocalAddress()
                        && !address.isSiteLocalAddress() && !address.isMulticastAddress() && !privateV6 && !sharedV4) {
                    values.add(address.getHostAddress());
                }
            }
        } catch (java.net.UnknownHostException e) {
            log.debug("Ingress endpoint DNS resolution failed");
        }
    }

    InetAddress[] lookup(String endpoint) throws java.net.UnknownHostException {
        return InetAddress.getAllByName(endpoint);
    }
}
