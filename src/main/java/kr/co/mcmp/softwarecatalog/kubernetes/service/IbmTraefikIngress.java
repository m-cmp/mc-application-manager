package kr.co.mcmp.softwarecatalog.kubernetes.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import kr.co.mcmp.softwarecatalog.application.dto.DeploymentConfigDTO;
import kr.co.mcmp.softwarecatalog.kubernetes.config.KubernetesNamespaces;

/** Native Traefik routes: never rely on disabled-by-default NGINX annotation compatibility. */
final class IbmTraefikIngress {
    static final String MIDDLEWARES = "traefik.ingress.kubernetes.io/router.middlewares";
    static final String OWNER = "am.mcmp.io/traefik-policy";
    static final ResourceDefinitionContext CONTEXT = new ResourceDefinitionContext.Builder()
            .withGroup("traefik.io").withVersion("v1alpha1").withKind("Middleware")
            .withPlural("middlewares").withNamespaced(true).build();
    private static final ObjectMapper JSON = new ObjectMapper();
    private IbmTraefikIngress() {}

    static String name(String host, String clazz, String cidr) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest((host + "\n" + clazz + "\n" + cidr).getBytes(StandardCharsets.UTF_8));
            return "am-ingress-" + HexFormat.of().formatHex(hash).substring(0, 24);
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    static String reference(String namespace, String host, String clazz, String cidr) {
        return namespace + "-" + name(host, clazz, cidr) + "@kubernetescrd";
    }

    static Map<String, String> annotations(DeploymentConfigDTO config, String cidr) {
        var result = new LinkedHashMap<String, String>();
        result.put(MIDDLEWARES, reference(KubernetesNamespaces.APPLICATION_WORKLOAD, config.getIngressHost(), config.getIngressClass(), cidr));
        result.put("traefik.ingress.kubernetes.io/router.entrypoints", config.isTlsEnabled() ? "websecure" : "web");
        result.put("traefik.ingress.kubernetes.io/router.tls", String.valueOf(config.isTlsEnabled()));
        return result;
    }

    static GenericKubernetesResource middleware(String namespace, DeploymentConfigDTO config, String cidr) {
        K8sIngressAccessService.validateCidr(cidr);
        return JSON.convertValue(Map.of("apiVersion", "traefik.io/v1alpha1", "kind", "Middleware",
                "metadata", Map.of("name", name(config.getIngressHost(), config.getIngressClass(), cidr),
                        "namespace", namespace, "labels", Map.of(OWNER, "true"),
                        "annotations", Map.of("kubernetes.io/ingress.class", config.getIngressClass())),
                "spec", Map.of("ipAllowList", Map.of("sourceRange", List.of(cidr)))), GenericKubernetesResource.class);
    }

    static List<String> verify(KubernetesClient client, String clazz, List<Service> services) {
        var deployments = client.apps().deployments().inNamespace("kube-system").withLabel("ingress-class", clazz).list().getItems();
        if (deployments.stream().anyMatch(d -> d.getSpec().getTemplate().getSpec().getContainers().stream()
                .noneMatch(c -> c.getImage() != null && c.getImage().contains("traefik"))))
            throw new IllegalArgumentException("Cannot identify the managed IBM Traefik controller.");
        for (var deployment : deployments) for (var container : deployment.getSpec().getTemplate().getSpec().getContainers()) {
            if (container.getImage() == null || !container.getImage().contains("traefik")) continue;
            Map<String,String> args = arguments(container);
            if (!"true".equals(args.get("providers.kubernetesingress")) || !"true".equals(args.get("providers.kubernetescrd"))
                    || "true".equals(args.get("providers.kubernetesingressnginx")))
                throw new IllegalArgumentException("IBM Traefik must enable native Ingress and Middleware processing for CIDR access.");
            for (String entry : List.of("web", "websecure")) {
                String prefix = "entrypoints." + entry + ".proxyprotocol.";
                if ("true".equalsIgnoreCase(args.get(prefix + "insecure")))
                    throw new IllegalArgumentException("IBM Traefik must restrict trusted PROXY protocol sources.");
                String trusted = args.getOrDefault(prefix + "trustedips", "");
                if (trusted.isBlank()) throw new IbmIngressSupport.MissingProxyException("IBM Traefik source IP preservation is disabled. Enable PROXY protocol before deploying CIDR-restricted applications.");
                for (String cidr : trusted.split(",", -1)) K8sIngressAccessService.validateCidr(cidr.trim());
            }
        }
        var endpoints = new ArrayList<String>();
        for (var service : services) {
            String features = service.getMetadata().getAnnotations() == null ? "" : service.getMetadata().getAnnotations().getOrDefault(IbmIngressSupport.PROXY_FEATURE, "");
            if (Arrays.stream(features.split(",")).map(String::trim).noneMatch("proxy-protocol"::equals))
                throw new IbmIngressSupport.MissingProxyException("IBM Traefik ALB Service does not enable PROXY protocol.");
            if (service.getStatus() == null || service.getStatus().getLoadBalancer() == null || service.getStatus().getLoadBalancer().getIngress() == null
                    || service.getStatus().getLoadBalancer().getIngress().isEmpty()) throw new IllegalArgumentException("IBM Traefik ALB has no ready external endpoint yet.");
            for (var address : service.getStatus().getLoadBalancer().getIngress()) {
                String endpoint = address.getHostname() != null ? address.getHostname() : address.getIp();
                if (endpoint == null || endpoint.isBlank()) throw new IllegalArgumentException("IBM Traefik ALB endpoint is empty.");
                endpoints.add(endpoint);
            }
        }
        // An API read verifies that the Middleware CRD exists and is accessible without changing shared ALB configuration.
        client.genericKubernetesResources(CONTEXT).inNamespace(KubernetesNamespaces.APPLICATION_WORKLOAD).list();
        return List.of("IBM managed Traefik ALB endpoint: " + String.join(", ", endpoints));
    }

    static Map<String,String> arguments(Container container) {
        Map<String,String> result = new HashMap<>();
        for (String arg : container.getArgs() == null ? List.<String>of() : container.getArgs()) {
            int split = arg.indexOf('=');
            if (arg.startsWith("--")) result.put(arg.substring(2, split < 0 ? arg.length() : split).toLowerCase(Locale.ROOT), split < 0 ? "true" : arg.substring(split + 1));
        }
        return result;
    }

    /** A dedicated immutable policy for one route; never overwrite operator-owned resources. */
    static GenericKubernetesResource create(KubernetesClient client, DeploymentConfigDTO config, String cidr) {
        if (cidr == null || !IbmIngressSupport.traefik(config.getIngressClass())) return null;
        var desired = middleware(KubernetesNamespaces.APPLICATION_WORKLOAD, config, cidr);
        var ops = client.genericKubernetesResources(CONTEXT).inNamespace(KubernetesNamespaces.APPLICATION_WORKLOAD);
        var existing = ops.withName(desired.getMetadata().getName()).get();
        if (existing != null) throw new IllegalArgumentException("A Traefik policy already exists for this route. Inspect the existing installation before retrying.");
        return ops.resource(desired).create();
    }

    static void bind(KubernetesClient client, GenericKubernetesResource middleware, String release) {
        if (middleware == null) return;
        var ingresses = client.network().v1().ingresses().inNamespace(KubernetesNamespaces.APPLICATION_WORKLOAD).list().getItems().stream()
                .filter(i -> i.getMetadata().getAnnotations() != null && Objects.equals(i.getMetadata().getAnnotations().get(MIDDLEWARES),
                        KubernetesNamespaces.APPLICATION_WORKLOAD + "-" + middleware.getMetadata().getName() + "@kubernetescrd"))
                .filter(i -> i.getMetadata().getAnnotations() != null && release.equals(i.getMetadata().getAnnotations().get("meta.helm.sh/release-name"))).toList();
        if (ingresses.isEmpty()) throw new IllegalArgumentException("The installed Helm release has no protected Traefik Ingress.");
        middleware.getMetadata().setOwnerReferences(ingresses.stream().map(i -> new OwnerReferenceBuilder().withApiVersion(i.getApiVersion())
                .withKind("Ingress").withName(i.getMetadata().getName()).withUid(i.getMetadata().getUid()).withBlockOwnerDeletion(false).build()).toList());
        client.genericKubernetesResources(CONTEXT).inNamespace(KubernetesNamespaces.APPLICATION_WORKLOAD).resource(middleware).update();
    }
}
