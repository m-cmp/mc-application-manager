package kr.co.mcmp.softwarecatalog.kubernetes.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.Serialization;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/** IBM's managed Secret Provider authenticates using the selected cluster's own projected identity. */
@Component
public class IbmClusterIdentityTokens {
    static final String NS = "kube-system", OWNER = "am.mcmp.io/ibm-identity", CLUSTER = "am.mcmp.io/ibm-cluster";
    static final String PREFIX = "am-ibm-identity-";
    // Public AM runtime supplies Java/curl; the Spring application is never started in the helper.
    static final String RUNTIME = "cloudbaristaorg/mc-application-manager@sha256:75de362c40b6b718f06b719b648a8c0b64824fb709c2a1977af45f9953497e4f";
    private final ObjectMapper json = new ObjectMapper();
    private final Map<String, Token> cache = new HashMap<>();
    private final Clock clock;
    public IbmClusterIdentityTokens() { this(Clock.systemUTC()); }
    IbmClusterIdentityTokens(Clock clock) { this.clock = clock; }
    record Token(String value, Instant expires) {
        @Override public String toString() { return "IBM cluster identity token (withheld)"; }
    }

    /** SSAR and metadata reads only; Spec Check must not create a credential Pod. */
    void check(KubernetesClient client) {
        providerImage(client);
        for (String resource : List.of("serviceaccounts", "pods", "configmaps"))
            for (String verb : List.of("get", "list", "create", "delete"))
                IngressAutomationPermissions.require(client, "", resource, NS, verb);
        IngressAutomationPermissions.require(client, "", "pods/exec", NS, "create");
        for (String resource : List.of("roles", "rolebindings"))
            for (String verb : List.of("get", "create", "delete"))
                IngressAutomationPermissions.require(client, "rbac.authorization.k8s.io", resource, NS, verb);
    }

    public synchronized String token(KubernetesClient client, String clusterId) {
        validateClusterId(clusterId);
        String key = client.getMasterUrl().toString() + "|" + clusterId;
        cache.entrySet().removeIf(e -> !e.getValue().expires.isAfter(clock.instant()));
        var existing = cache.get(key);
        if (existing != null) return existing.value;
        String image = providerImage(client);
        removeExpired(client, clusterId);
        String name = PREFIX + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        ServiceAccount account = null;
        Token issued;
        try {
            account = create(client, "v1", "ServiceAccount", name, clusterId, null, Map.of());
            provision(client, name, clusterId, account, image);
            awaitReady(client, name, account.getMetadata().getUid());
            issued = parse(execute(client, name));
        } catch (Exception failure) {
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            // Kubernetes/exec/SDK exceptions may contain server bodies or private headers.
            throw new IllegalArgumentException("AM could not obtain an IBM token from the selected cluster. Check IBM Secret Provider readiness and AM kube-system permissions; no ALB change was submitted.");
        } finally {
            if (account != null) try { remove(client, account, clusterId); }
            catch (RuntimeException cleanup) {
                throw new IllegalArgumentException("AM could not clean up its temporary IBM identity helper. Inspect AM-owned kube-system resources before retrying; no ALB change was submitted.");
            }
        }
        if (cache.size() >= 128) cache.clear();
        cache.put(key, issued);
        return issued.value;
    }

    static void validateClusterId(String id) {
        if (id == null || !id.matches("[a-z0-9]{20}")) throw new IllegalArgumentException("Invalid registered IBM cluster identity.");
    }

    String providerImage(KubernetesClient client) {
        for (var pod : client.pods().inNamespace(NS).list().getItems()) {
            if (!pod.getMetadata().getName().startsWith("ibm-vpc-block-csi-controller-") || pod.getStatus() == null
                    || pod.getStatus().getContainerStatuses() == null) continue;
            for (var container : pod.getSpec().getContainers()) {
                if (!container.getImage().matches("icr\\.io/obs/armada-storage-secret(?::[^/]+|@sha256:[a-f0-9]{64})")) continue;
                for (var state : pod.getStatus().getContainerStatuses()) {
                    if (!container.getName().equals(state.getName()) || !Boolean.TRUE.equals(state.getReady())) continue;
                    String image = Objects.toString(state.getImageID(), "").replaceFirst("^docker-pullable://", "");
                    if (image.matches("icr\\.io/obs/armada-storage-secret@sha256:[a-f0-9]{64}")) return image;
                }
            }
        }
        throw new IllegalArgumentException("The selected IBM cluster needs a ready IBM VPC Block CSI Secret Provider before automatic Ingress authentication.");
    }

    <T extends HasMetadata> T create(KubernetesClient client, String api, String kind, String name, String cluster,
                                     ServiceAccount owner, Map<String, Object> fields) throws Exception {
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("name", name); metadata.put("namespace", NS);
        metadata.put("labels", Map.of(OWNER, "true", CLUSTER, cluster));
        if (owner != null) metadata.put("ownerReferences", List.of(Map.of("apiVersion", "v1", "kind", "ServiceAccount",
                "name", name, "uid", owner.getMetadata().getUid())));
        var resource = new LinkedHashMap<String, Object>();
        resource.put("apiVersion", api); resource.put("kind", kind); resource.put("metadata", metadata); resource.putAll(fields);
        HasMetadata object = Serialization.unmarshal(json.writeValueAsString(resource));
        @SuppressWarnings("unchecked") T created = (T) client.resource(object).inNamespace(NS).create();
        return created;
    }

    void provision(KubernetesClient client, String name, String cluster, ServiceAccount owner, String image) throws Exception {
        create(client, "rbac.authorization.k8s.io/v1", "Role", name, cluster, owner, Map.of("rules", List.of(
                Map.of("apiGroups", List.of(""), "resources", List.of("secrets"), "resourceNames", List.of("storage-secret-store", "ibm-cloud-credentials"), "verbs", List.of("get", "list", "watch")),
                Map.of("apiGroups", List.of(""), "resources", List.of("configmaps"), "resourceNames", List.of("cloud-conf", "cluster-info"), "verbs", List.of("get")))));
        create(client, "rbac.authorization.k8s.io/v1", "RoleBinding", name, cluster, owner, Map.of(
                "roleRef", Map.of("apiGroup", "rbac.authorization.k8s.io", "kind", "Role", "name", name),
                "subjects", List.of(Map.of("kind", "ServiceAccount", "name", name, "namespace", NS))));
        byte[] bridge;
        try (var stream = IbmClusterIdentityBridge.class.getResourceAsStream("IbmClusterIdentityBridge.class")) {
            if (stream == null) throw new IOException(); bridge = stream.readNBytes(65537);
        }
        if (bridge.length > 65536) throw new IOException();
        create(client, "v1", "ConfigMap", name, cluster, owner,
                Map.of("binaryData", Map.of("IbmClusterIdentityBridge.class", Base64.getEncoder().encodeToString(bridge))));
        var provider = Map.of("name", "provider", "image", image, "args", List.of("--endpoint=$(ENDPOINT)"),
                "env", List.of(Map.of("name", "ENDPOINT", "value", "unix:/sidecardir/provider.sock"),
                        Map.of("name", "TOKEN_EXPIRY_DIFF", "value", "20m"), Map.of("name", "PROFILE_CAPACITY", "value", "1")),
                "volumeMounts", List.of(Map.of("name", "socket", "mountPath", "/sidecardir"), Map.of("name", "vault-token", "mountPath", "/var/run/secrets/tokens")),
                "resources", Map.of("requests", Map.of("cpu", "10m", "memory", "24Mi"), "limits", Map.of("cpu", "100m", "memory", "100Mi")));
        var bridgeContainer = Map.of("name", "client", "image", RUNTIME, "command", List.of("sh", "-c", "sleep 450"),
                "volumeMounts", List.of(Map.of("name", "socket", "mountPath", "/sidecardir"), Map.of("name", "bridge", "mountPath", "/bridge", "readOnly", true), Map.of("name", "temp", "mountPath", "/tmp")),
                "resources", Map.of("requests", Map.of("cpu", "20m", "memory", "48Mi"), "limits", Map.of("cpu", "1", "memory", "192Mi")),
                "securityContext", Map.of("runAsUser", 2121, "runAsGroup", 0, "allowPrivilegeEscalation", false,
                        "readOnlyRootFilesystem", true, "capabilities", Map.of("drop", List.of("ALL"))));
        create(client, "v1", "Pod", name, cluster, owner, Map.of("spec", Map.of("serviceAccountName", name,
                "restartPolicy", "Never", "activeDeadlineSeconds", 420, "terminationGracePeriodSeconds", 3,
                "containers", List.of(provider, bridgeContainer), "volumes", List.of(
                        Map.of("name", "socket", "emptyDir", Map.of()), Map.of("name", "temp", "emptyDir", Map.of()),
                        Map.of("name", "bridge", "configMap", Map.of("name", name, "items", List.of(Map.of("key", "IbmClusterIdentityBridge.class", "path", "kr/co/mcmp/softwarecatalog/kubernetes/service/IbmClusterIdentityBridge.class")))),
                        Map.of("name", "vault-token", "projected", Map.of("sources", List.of(Map.of("serviceAccountToken", Map.of("path", "vault-token", "expirationSeconds", 600)))))))));
    }

    void awaitReady(KubernetesClient client, String name, String uid) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(180);
        while (System.nanoTime() < deadline) {
            var pod = client.pods().inNamespace(NS).withName(name).get();
            if (pod == null || pod.getMetadata().getOwnerReferences().stream().noneMatch(o -> uid.equals(o.getUid()))) throw new IOException();
            var status = pod.getStatus();
            if (status != null && Set.of("Failed", "Succeeded").contains(status.getPhase())) throw new IOException();
            if (status != null && status.getContainerStatuses() != null && status.getContainerStatuses().size() == 2
                    && status.getContainerStatuses().stream().allMatch(s -> Boolean.TRUE.equals(s.getReady()))) {
                Thread.sleep(1000); return;
            }
            Thread.sleep(2000);
        }
        throw new IOException();
    }

    String execute(KubernetesClient client, String name) throws Exception {
        var output = new ByteArrayOutputStream();
        try (var watch = client.pods().inNamespace(NS).withName(name).inContainer("client")
                .writingOutput(limited(output)).writingError(limited(new ByteArrayOutputStream()))
                .exec("java", "-Xmx96m", "-cp", "/bridge", IbmClusterIdentityBridge.class.getName())) {
            if (watch.exitCode().get(165, TimeUnit.SECONDS) != 0) throw new IOException();
            return output.toString(StandardCharsets.UTF_8);
        }
    }

    static OutputStream limited(ByteArrayOutputStream target) {
        return new OutputStream() {
            @Override public synchronized void write(int value) throws IOException {
                if (target.size() >= 65536) throw new IOException("IBM identity output exceeded its limit.");
                target.write(value);
            }
            @Override public synchronized void write(byte[] bytes, int offset, int length) throws IOException {
                if (target.size() + length > 65536) throw new IOException("IBM identity output exceeded its limit.");
                target.write(bytes, offset, length);
            }
        };
    }

    Token parse(String output) {
        String[] fields = output.strip().split(" ");
        if (fields.length != 3 || !"IAM".equals(fields[0]) || !fields[1].matches("[0-9]{1,5}")
                || !fields[2].matches("[a-zA-Z0-9._-]{30,32768}")) throw new IllegalArgumentException();
        long ttl = Long.parseLong(fields[1]);
        if (ttl <= 120 || ttl > 86400) throw new IllegalArgumentException();
        return new Token(fields[2], clock.instant().plusSeconds(Math.min(900, ttl - 60)));
    }

    void removeExpired(KubernetesClient client, String id) {
        for (var account : client.serviceAccounts().inNamespace(NS).withLabel(OWNER, "true").withLabel(CLUSTER, id).list().getItems()) {
            String created = account.getMetadata().getCreationTimestamp();
            if (created != null && Instant.parse(created).plusSeconds(600).isBefore(clock.instant())) remove(client, account, id);
        }
    }

    void remove(KubernetesClient client, ServiceAccount account, String cluster) {
        var current = client.serviceAccounts().inNamespace(NS).withName(account.getMetadata().getName()).get();
        if (current == null) return;
        var meta = current.getMetadata();
        if (!meta.getName().startsWith(PREFIX) || !Objects.equals(meta.getUid(), account.getMetadata().getUid())
                || (meta.getLabels() == null || !"true".equals(meta.getLabels().get(OWNER))) || !cluster.equals(meta.getLabels().get(CLUSTER)))
            throw new IllegalArgumentException("IBM identity helper ownership changed; automatic cleanup stopped.");
        client.resource(current).inNamespace(NS).lockResourceVersion(meta.getResourceVersion()).delete();
    }
}
