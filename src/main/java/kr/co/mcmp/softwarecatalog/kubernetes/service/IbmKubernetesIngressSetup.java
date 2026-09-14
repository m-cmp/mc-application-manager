package kr.co.mcmp.softwarecatalog.kubernetes.service;

import com.fasterxml.jackson.databind.JsonNode;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import kr.co.mcmp.ape.cbtumblebug.api.CbtumblebugRestApi;
import kr.co.mcmp.ape.cbtumblebug.dto.K8sClusterDto;
import kr.co.mcmp.softwarecatalog.application.dto.DeploymentConfigDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Uses the selected cluster's existing Kubernetes credentials, never raw IBM account keys. */
@Component @RequiredArgsConstructor
class IbmKubernetesIngressSetup {
    static final String CONFIG = "ibm-k8s-controller-config";
    static final String JOURNAL = "am-ibm-kubernetes-ingress-setup";
    static final String OUTBOUND = "service.kubernetes.io/ibm-load-balancer-cloud-provider-vpc-allow-outbound-traffic";
    static final String SUBNETS = "service.kubernetes.io/ibm-load-balancer-cloud-provider-vpc-subnets";
    static final String RECONCILE = "am.mcmp.io/ibm-proxy-reconcile";
    private final CbtumblebugRestApi tumblebug;
    private final IbmIngressAutomationProperties properties;

    record Plan(List<Service> services, ConfigMap controller, List<String> cidrs, boolean ready) { }

    Plan plan(KubernetesClient client, String ns, K8sClusterDto cluster, DeploymentConfigDTO config) {
        if (!IbmIngressSupport.isIbm(cluster) || !IbmIngressSupport.managed(config.getIngressClass()))
            throw new IllegalArgumentException("Kubernetes Ingress preparation requires the selected IBM cluster.");
        IbmIngressSupport.verifyBase(client, config.getIngressClass());
        var services = new ArrayList<Service>();
        services.addAll(IbmIngressSupport.managedServices(client, "public-"));
        services.addAll(IbmIngressSupport.managedServices(client, "private-"));
        var cm = client.configMaps().inNamespace("kube-system").withName(CONFIG).get();
        try {
            IbmIngressSupport.verify(client, config);
            var marker = client.configMaps().inNamespace("kube-system").withName(JOURNAL).get();
            rejectUnresolved(marker);
            return new Plan(services, cm, List.of(), true);
        } catch (IbmIngressSupport.MissingProxyException | IbmIngressSupport.MissingPortException ignored) { }

        rejectUnresolved(client.configMaps().inNamespace("kube-system").withName(JOURNAL).get());
        // One controller ConfigMap affects every ALB. Never silently repair an operator's mixed configuration.
        boolean proxy = "true".equalsIgnoreCase(data(cm).get("use-proxy-protocol"));
        if (services.stream().anyMatch(s -> hasProxy(s) != proxy))
            throw new IllegalArgumentException("IBM ALBs and controller have mixed PROXY settings. Operator review is required before shared configuration changes.");
        for (Service s : services) {
            String lbName = annotations(s).get("service.kubernetes.io/ibm-load-balancer-cloud-provider-vpc-lb-name");
            if (cluster.getCspResourceId() == null || lbName == null || !lbName.startsWith("kube-" + cluster.getCspResourceId() + "-ingress-"))
                throw new IllegalArgumentException("Cannot establish that every managed IBM LB belongs to the selected cluster.");
            if (s.getSpec().getPorts() == null || s.getSpec().getPorts().stream().anyMatch(p -> !Set.of(80,443).contains(p.getPort())))
                throw new IllegalArgumentException("Automatic IBM preparation supports existing HTTP/HTTPS listeners only; custom listeners require operator review.");
            String prefix = s.getMetadata().getName().startsWith("public-") ? "public-" : "private-";
            String clazz = prefix.equals("public-") ? IbmIngressSupport.PUBLIC_CLASS : IbmIngressSupport.PRIVATE_CLASS;
            IbmIngressSupport.verifyBase(client, clazz);
            var controllers = client.apps().deployments().inNamespace("kube-system").withLabel("ingress-class", clazz).list().getItems();
            if (controllers.stream().anyMatch(d -> d.getSpec() == null || d.getSpec().getTemplate().getSpec().getContainers().stream()
                    .noneMatch(c -> c.getArgs() != null && c.getArgs().contains("--configmap=kube-system/" + CONFIG)
                            && c.getArgs().contains("--http-port=80") && c.getArgs().contains("--https-port=443"))))
                throw new IllegalArgumentException("IBM controllers must already listen on ports 80/443 and use the managed ConfigMap. AM will not replace controllers.");
        }
        var cidrs = trustedCidrs(client, ns, cluster, services);
        IngressAutomationPermissions.lock(client);
        for (String verb : List.of("get", "update")) IngressAutomationPermissions.require(client, "", "services", "kube-system", verb);
        IngressAutomationPermissions.require(client, "", "events", "kube-system", "list");
        return new Plan(services, cm, cidrs, false);
    }

    List<String> check(KubernetesClient client, String ns, K8sClusterDto cluster, DeploymentConfigDTO config) {
        Plan plan = plan(client, ns, cluster, config);
        return plan.ready ? IbmIngressSupport.verify(client, config) : List.of(
                "Deploy will prepare HTTP and source-IP preservation on this cluster's existing shared IBM load balancers. Existing connections may be interrupted during reconciliation; no new load balancer or controller is created.");
    }

    /** Caller holds the cluster lock. Backup is durable, including if AM stops during reconciliation. */
    void prepare(KubernetesClient client, String ns, K8sClusterDto cluster, DeploymentConfigDTO config, Consumer<String> progress) {
        Plan plan = plan(client, ns, cluster, config);
        if (plan.ready) return;
        var maps = client.configMaps().inNamespace("kube-system");
        var previous = maps.withName(JOURNAL).get();
        var backup = new LinkedHashMap<String,String>();
        backup.put("clusterId", cluster.getCspResourceId()); backup.put("state", "PREPARING");
        backup.put("startedAt", Instant.now().toString());
        backup.put("controller.json", client.getKubernetesSerialization().asJson(plan.controller));
        for (Service s : plan.services) backup.put(s.getMetadata().getName() + ".json", client.getKubernetesSerialization().asJson(s));
        var journal = new ConfigMapBuilder().withNewMetadata().withName(JOURNAL)
                .withLabels(Map.of(IbmCertificateManager.OWNER,"true")).endMetadata().withData(backup).build();
        if (previous == null) maps.resource(journal).create();
        else {
            journal.getMetadata().setResourceVersion(previous.getMetadata().getResourceVersion());
            maps.resource(journal).lockResourceVersion(previous.getMetadata().getResourceVersion()).replace();
        }
        Map<String,Service> submitted = new LinkedHashMap<>();
        ConfigMap updated = null;
        try {
            progress.accept("Preparing existing IBM LB listeners and PROXY protocol using Kubernetes credentials…");
            // Record intended objects before each call: a request timeout can still mean the server accepted it.
            for (Service s : plan.services) {
                Service desired = desired(s, config);
                submitted.put(s.getMetadata().getName(), desired);
                client.services().inNamespace("kube-system").resource(desired)
                        .lockResourceVersion(s.getMetadata().getResourceVersion()).replace();
            }
            updated = desiredController(plan.controller, plan.cidrs);
            if (plan.controller == null) maps.resource(updated).create();
            else maps.resource(updated).lockResourceVersion(plan.controller.getMetadata().getResourceVersion()).replace();
            awaitReady(client, config, plan.services, Instant.parse(backup.get("startedAt")));
            state(client, "READY");
            progress.accept("IBM HTTP entry and source-IP preservation are ready.");
        } catch (RuntimeException error) {
            progress.accept("IBM preparation failed; restoring AM's changes while preserving unrelated settings…");
            boolean restored = rollback(client, plan, submitted, updated);
            state(client, restored ? "ROLLED_BACK" : "REVIEW_REQUIRED");
            throw new IllegalArgumentException(restored
                    ? "IBM Ingress preparation did not become ready. AM restored its Kubernetes settings; the IBM LB may take time to reconcile. No application was deployed."
                    : "IBM Ingress preparation failed and shared settings changed concurrently. No application was deployed. Operator review of " + JOURNAL + " is required.", error);
        }
    }

    static Service desired(Service original, DeploymentConfigDTO config) {
        var s = new ServiceBuilder(original).build();
        var annotations = new LinkedHashMap<>(annotations(s));
        annotations.put(IbmIngressSupport.PROXY_FEATURE, append(annotations.get(IbmIngressSupport.PROXY_FEATURE), "proxy-protocol"));
        boolean selected = s.getMetadata().getName().startsWith(IbmIngressSupport.PUBLIC_CLASS.equals(config.getIngressClass()) ? "public-" : "private-");
        if (selected && !config.isTlsEnabled() && s.getSpec().getPorts().stream().noneMatch(p -> Integer.valueOf(80).equals(p.getPort()))) {
            if (s.getSpec().getPorts().stream().anyMatch(p -> "http".equals(p.getName())))
                throw new IllegalArgumentException("The existing http Service port has a nonstandard number.");
            s.getSpec().getPorts().add(new ServicePortBuilder().withName("http").withPort(80).withTargetPort(new IntOrString(80)).withProtocol("TCP").build());
            if (annotations.containsKey(OUTBOUND)) annotations.put(OUTBOUND, append(annotations.get(OUTBOUND), "80"));
        }
        s.getMetadata().setAnnotations(annotations);
        return s;
    }

    static ConfigMap desiredController(ConfigMap original, List<String> cidrs) {
        var cm = original == null ? new ConfigMapBuilder().withNewMetadata().withName(CONFIG).endMetadata().build() : new ConfigMapBuilder(original).build();
        var data = new LinkedHashMap<>(data(cm));
        data.put("use-proxy-protocol", "true"); data.put("enable-real-ip", "true");
        data.put("use-forwarded-headers", "false"); data.put("proxy-real-ip-cidr", String.join(",", cidrs));
        cm.setData(data); return cm;
    }

    List<String> trustedCidrs(KubernetesClient client, String ns, K8sClusterDto cluster, List<Service> services) {
        if (cluster.getNetwork() == null || cluster.getNetwork().getVNetId() == null)
            throw new IllegalArgumentException("Selected IBM cluster has no registered VPC metadata.");
        JsonNode vnet = tumblebug.getVNetSecurityMetadata(ns, cluster.getNetwork().getVNetId());
        if (vnet == null || !Objects.equals(cluster.getConnectionName(), vnet.path("connectionName").asText()) || vnet.path("cspResourceId").asText().isBlank())
            throw new IllegalArgumentException("IBM VPC metadata does not match the selected connection.");
        Map<String,String> registered = new HashMap<>();
        for (JsonNode subnet : vnet.path("subnetInfoList")) {
            if (!Objects.equals(vnet.path("cspResourceId").asText(), subnet.path("cspVNetId").asText())
                    || !Objects.equals(cluster.getConnectionName(), subnet.path("connectionName").asText())) continue;
            String id = subnet.path("cspResourceId").asText(), cidr = subnet.path("ipv4_CIDR").asText();
            if (!id.isBlank()) registered.put(id, K8sIngressAccessService.validateCidr(cidr));
        }
        Set<String> subnets = new TreeSet<>();
        var nodes = client.nodes().list().getItems();
        if (nodes.isEmpty()) throw new IllegalArgumentException("No IBM worker nodes available for subnet verification.");
        for (Node node : nodes) {
            String id = node.getMetadata().getLabels() == null ? null : node.getMetadata().getLabels().get("ibm-cloud.kubernetes.io/subnet-id");
            if (!registered.containsKey(id) || node.getStatus() == null || node.getStatus().getAddresses() == null
                    || node.getStatus().getAddresses().stream().filter(a -> "InternalIP".equals(a.getType())).noneMatch(a -> contains(registered.get(id), a.getAddress())))
                throw new IllegalArgumentException("Cannot match every IBM worker subnet and InternalIP to MCMP's registered VPC. Refresh network metadata before preparing Ingress.");
            subnets.add(id);
        }
        for (Service s : services) {
            String explicit = annotations(s).get(SUBNETS);
            if (explicit != null) for (String id : explicit.split(",", -1)) {
                if (!registered.containsKey(id.trim())) throw new IllegalArgumentException("An explicit IBM LB subnet is not registered in the selected VPC.");
                subnets.add(id.trim());
            }
        }
        return subnets.stream().map(registered::get).distinct().sorted().toList();
    }

    /** Service status alone can predate a change: require a fresh provider completion event and stable HTTP responses. */
    void awaitReady(KubernetesClient client, DeploymentConfigDTO config, List<Service> before, Instant started) {
        long deadline = System.nanoTime() + Math.min(1200, Math.max(1, properties.getReadyTimeoutSeconds())) * 1_000_000_000L;
        int stable = 0;
        boolean synchronizedAddedListeners = false;
        Map<String,Instant> requiredEvents = new HashMap<>();
        before.forEach(s -> requiredEvents.put(s.getMetadata().getName(), started));
        while (System.nanoTime() < deadline) {
            try {
                IbmIngressSupport.verify(client, config);
                var events = client.v1().events().inNamespace("kube-system").list().getItems();
                boolean reconciled = before.stream().allMatch(s -> events.stream().anyMatch(e ->
                        "EnsuredLoadBalancer".equals(e.getReason()) && e.getInvolvedObject() != null
                        && Objects.equals(s.getMetadata().getUid(), e.getInvolvedObject().getUid()) && after(e, requiredEvents.get(s.getMetadata().getName()))));
                if (reconciled && !synchronizedAddedListeners) {
                    // IBM Provider 1.35 CreateLoadBalancerPool omits ProxyProtocol on added ports.
                    // A second reconciliation runs UpdateLoadBalancerPool, which applies it.
                    // Change only an AM-owned annotation, never toggle PROXY off or recreate the LB.
                    var requested = reconcileAddedListeners(client, before);
                    requiredEvents.putAll(requested);
                    synchronizedAddedListeners = true;
                    if (!requested.isEmpty()) { stable = 0; continue; }
                }
                boolean responding = IbmIngressSupport.managedServices(client, IbmIngressSupport.PUBLIC_CLASS.equals(config.getIngressClass()) ? "public-" : "private-")
                        .stream().allMatch(IbmKubernetesIngressSetup::httpResponding);
                if (reconciled && responding) { if (++stable >= 3) return; } else stable = 0;
            } catch (RuntimeException ignored) { stable = 0; }
            try { Thread.sleep(Math.max(1, properties.getPollSeconds()) * 1000L); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalArgumentException("IBM preparation interrupted."); }
        }
        throw new IllegalArgumentException("Timed out waiting for IBM provider reconciliation and HTTP readiness.");
    }

    static Map<String,Instant> reconcileAddedListeners(KubernetesClient client, List<Service> before) {
        Map<String,Instant> requested = new HashMap<>();
        for (Service original : before) {
            if (original.getSpec().getPorts().stream().anyMatch(p -> Integer.valueOf(80).equals(p.getPort()))) continue;
            var ops = client.services().inNamespace("kube-system");
            Service current = ops.withName(original.getMetadata().getName()).get();
            if (current == null || !Objects.equals(original.getMetadata().getUid(), current.getMetadata().getUid()) || !hasProxy(current))
                throw new IllegalArgumentException("IBM LB changed during listener reconciliation.");
            if (current.getSpec().getPorts().stream().noneMatch(p -> Integer.valueOf(80).equals(p.getPort()))) continue;
            Instant now = Instant.now();
            var annotations = new LinkedHashMap<>(annotations(current));
            annotations.put(RECONCILE, now + "/" + UUID.randomUUID());
            current.getMetadata().setAnnotations(annotations);
            ops.resource(current).lockResourceVersion(current.getMetadata().getResourceVersion()).replace();
            requested.put(current.getMetadata().getName(), now);
        }
        return requested;
    }

    static boolean httpResponding(Service service) {
        if (service.getStatus() == null || service.getStatus().getLoadBalancer() == null || service.getStatus().getLoadBalancer().getIngress() == null) return false;
        for (var endpoint : service.getStatus().getLoadBalancer().getIngress()) {
            String host = endpoint.getHostname();
            if (host == null || !host.endsWith(".lb.appdomain.cloud")) return false;
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host,80), 4000); socket.setSoTimeout(4000);
                String probeHost = "am-readiness-" + UUID.randomUUID() + ".invalid";
                socket.getOutputStream().write(("GET / HTTP/1.1\r\nHost: " + probeHost + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                String first = new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.US_ASCII)).readLine();
                if (first == null || !first.startsWith("HTTP/1.1 404")) return false;
            } catch (IOException e) { return false; }
        }
        return !service.getStatus().getLoadBalancer().getIngress().isEmpty();
    }

    private boolean rollback(KubernetesClient client, Plan plan, Map<String,Service> submitted, ConfigMap updated) {
        boolean ok = true;
        for (Service original : plan.services) {
            Service desired = submitted.get(original.getMetadata().getName());
            if (desired == null) continue;
            try {
                var ops = client.services().inNamespace("kube-system");
                Service current = ops.withName(original.getMetadata().getName()).get();
                if (current == null || !Objects.equals(original.getMetadata().getUid(), current.getMetadata().getUid())) { ok = false; continue; }
                var annotations = new LinkedHashMap<>(annotations(current));
                for (String key : List.of(IbmIngressSupport.PROXY_FEATURE, OUTBOUND)) {
                    if (!Objects.equals(annotations.get(key), annotations(desired).get(key)) && !Objects.equals(annotations.get(key), annotations(original).get(key))) throw new IllegalArgumentException();
                    if (annotations(original).containsKey(key)) annotations.put(key, annotations(original).get(key)); else annotations.remove(key);
                }
                current.getMetadata().setAnnotations(annotations);
                if (original.getSpec().getPorts().stream().noneMatch(p -> Integer.valueOf(80).equals(p.getPort())))
                    current.getSpec().getPorts().removeIf(p -> Integer.valueOf(80).equals(p.getPort()) && "http".equals(p.getName()) && new IntOrString(80).equals(p.getTargetPort()));
                ops.resource(current).lockResourceVersion(current.getMetadata().getResourceVersion()).replace();
            } catch (RuntimeException e) { ok = false; }
        }
        if (updated != null) try {
            var ops = client.configMaps().inNamespace("kube-system"); var current = ops.withName(CONFIG).get();
            var restored = new LinkedHashMap<>(data(current));
            for (String key : List.of("use-proxy-protocol","proxy-real-ip-cidr","enable-real-ip","use-forwarded-headers")) {
                if (!Objects.equals(restored.get(key), data(updated).get(key)) && !Objects.equals(restored.get(key), data(plan.controller).get(key))) throw new IllegalArgumentException();
                if (data(plan.controller).containsKey(key)) restored.put(key,data(plan.controller).get(key)); else restored.remove(key);
            }
            current.setData(restored); ops.resource(current).lockResourceVersion(current.getMetadata().getResourceVersion()).replace();
        } catch (RuntimeException e) { ok = false; }
        return ok;
    }

    private static void rejectUnresolved(ConfigMap marker) {
        if (marker != null && (!IbmCertificateManager.owned(marker) || !Set.of("READY","ROLLED_BACK").contains(data(marker).getOrDefault("state",""))))
            throw new IllegalArgumentException("An IBM Kubernetes preparation has an unresolved outcome. Inspect " + JOURNAL + " before retrying.");
    }
    private static void state(KubernetesClient client, String value) {
        var ops = client.configMaps().inNamespace("kube-system"); var marker = ops.withName(JOURNAL).get();
        marker.getData().put("state",value); ops.resource(marker).lockResourceVersion(marker.getMetadata().getResourceVersion()).replace();
    }
    private static Map<String,String> data(ConfigMap cm) { return cm == null || cm.getData() == null ? Map.of() : cm.getData(); }
    private static Map<String,String> annotations(Service s) { return s.getMetadata().getAnnotations() == null ? Map.of() : s.getMetadata().getAnnotations(); }
    private static String append(String value, String item) { var result = new LinkedHashSet<String>(); if(value != null) for(String part : value.split(",")) if(!part.isBlank()) result.add(part.trim()); result.add(item); return String.join(",",result); }
    private static boolean hasProxy(Service s) { return Arrays.stream(annotations(s).getOrDefault(IbmIngressSupport.PROXY_FEATURE,"").split(",")).map(String::trim).anyMatch("proxy-protocol"::equals); }
    private static boolean after(Event e, Instant time) {
        try {
            String stamp = e.getSeries() != null && e.getSeries().getLastObservedTime() != null
                    ? e.getSeries().getLastObservedTime().getTime() : e.getLastTimestamp();
            if (stamp == null && e.getEventTime() != null) stamp = e.getEventTime().getTime();
            return stamp != null && !Instant.parse(stamp).isBefore(time.minusSeconds(1));
        } catch (RuntimeException ignored) { return false; }
    }
    private static boolean contains(String cidr, String address) { try { String[] c = cidr.split("/"); long mask = (0xffffffffL << (32-Integer.parseInt(c[1]))) & 0xffffffffL; return (ipv4(c[0]) & mask) == (ipv4(address) & mask); } catch(RuntimeException ignored) { return false; } }
    private static long ipv4(String value) { String[] bytes=value.split("\\.",-1); if(bytes.length!=4) throw new IllegalArgumentException(); long result=0; for(String b:bytes) { int n=Integer.parseInt(b); if(n<0||n>255) throw new IllegalArgumentException(); result=result*256+n; } return result; }
}
