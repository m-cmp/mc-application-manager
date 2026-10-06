package kr.co.mcmp.softwarecatalog.kubernetes.service;

import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.Locale;
import java.util.function.Consumer;
import kr.co.mcmp.ape.cbtumblebug.api.CbtumblebugRestApi;
import kr.co.mcmp.ape.cbtumblebug.dto.K8sClusterDto;
import kr.co.mcmp.softwarecatalog.kubernetes.config.KubernetesClientFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor
public class NhnCinderAddonService {
    public record Capability(boolean supported, boolean canInstall, boolean driverReady, String state, String version, String message) { }
    public record Target(String namespace, String clusterName, String clusterId, String connectionName, String region) { }
    private final CbtumblebugRestApi tumblebug;
    private final KubernetesClientFactory clients;
    private final NhnCinderAddonProperties properties;
    private final NhnOpenBaoCredentials credentials;
    private final NhnNksApi nks;

    public Capability capability(String namespace, String clusterName) {
        validateNames(namespace, clusterName);
        var cluster = lookup(namespace, clusterName);
        if (!nhn(cluster)) return new Capability(false, false, false, "UNSUPPORTED", "", "Managed Cinder add-on installation is available for NHN clusters.");
        var target = target(namespace, clusterName, cluster);
        try (var client = clients.getClient(namespace, clusterName)) {
            if (driverReady(client)) return new Capability(true, false, true, "READY", "", "Cinder CSI is ready. Select or create an NHN StorageClass.");
        } catch (RuntimeException e) { throw StorageOperationException.translate(e); }
        NhnCinderAddonProperties.Binding binding;
        try { binding = binding(target); }
        catch (StorageOperationException e) { return new Capability(true, false, false, "NOT_CONFIGURED", "", e.getMessage()); }
        var session = nks.login(credentials.read(binding), binding.getRegion());
        var nativeCluster = nks.cluster(session, target.clusterId());
        var installed = nks.installed(session, target.clusterId());
        if (installed.isPresent()) {
            var addon = installed.get();
            String state = addon.path("status").asText();
            boolean failed = failed(state);
            return new Capability(true, !failed, false, failed ? "FAILED" : "REGISTERING", addon.path("version").asText(),
                    failed ? "The existing Cinder add-on failed in NKS. Resolve its status in NKS before retrying."
                            : "Cinder is installed or installing. Continue to wait for CSI registration on the worker nodes.");
        }
        requireClusterReady(nativeCluster.path("status").asText());
        return new Capability(true, true, false, "AVAILABLE", nks.compatibleVersion(session, nativeCluster),
                "Install the compatible Cinder CSI add-on, then create or select an NHN StorageClass.");
    }

    Target resolve(String namespace, String clusterName) {
        validateNames(namespace, clusterName);
        var cluster = lookup(namespace, clusterName);
        if (!nhn(cluster)) throw new StorageOperationException(400, "NHN_ONLY", "This endpoint installs Cinder only on NHN Kubernetes clusters.");
        properties.validateTiming();
        return target(namespace, clusterName, cluster);
    }

    void prepare(Target target, Consumer<String> progress) {
        if (!target.equals(resolve(target.namespace(), target.clusterName())))
            throw new StorageOperationException(409, "NHN_CLUSTER_CHANGED", "The selected cluster changed. Refresh cluster information and retry.");
        try (var client = clients.getClient(target.namespace(), target.clusterName())) {
            // Preserve a working managed or manually installed driver; no cloud mutation or credential read is needed.
            if (driverReady(client)) { progress.accept("Cinder CSI is already ready. Existing installation reused."); return; }
            var binding = binding(target);
            progress.accept("Authenticating the registered NHN project and checking the selected NKS cluster…");
            var session = nks.login(credentials.read(binding), binding.getRegion());
            var nativeCluster = nks.cluster(session, target.clusterId());
            var installed = nks.installed(session, target.clusterId());
            if (installed.isEmpty()) {
                requireClusterReady(nativeCluster.path("status").asText());
                String version = nks.compatibleVersion(session, nativeCluster);
                progress.accept("Requesting installation of the compatible Cinder CSI add-on…");
                nks.install(session, target.clusterId(), version);
            } else {
                requireAddonNotFailed(installed.get().path("status").asText());
                progress.accept("Reusing the existing Cinder add-on and waiting for CSI readiness…");
            }
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(properties.getReadyTimeoutSeconds());
            while (System.nanoTime() < deadline) {
                installed = nks.installed(session, target.clusterId());
                if (installed.isPresent()) {
                    String state = installed.get().path("status").asText();
                    requireAddonNotFailed(state);
                    if (complete(state) && driverReady(client)) {
                        progress.accept("Cinder CSI is ready. Select or create an NHN StorageClass.");
                        return;
                    }
                }
                progress.accept("Waiting for NKS installation and Cinder CSI registration on all worker nodes…");
                try { Thread.sleep(Math.min(java.util.concurrent.TimeUnit.SECONDS.toMillis(properties.getPollSeconds()),
                        Math.max(1, java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())))); }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new StorageOperationException(503, "NHN_ADDON_INTERRUPTED", "Verification was interrupted. Refresh status to reuse an installation that may still be running.");
                }
            }
            throw new StorageOperationException(504, "NHN_ADDON_TIMEOUT", "Cinder verification timed out. Refresh status to check NKS and CSI readiness; AM will reuse an existing installation on retry.");
        } catch (RuntimeException e) { throw StorageOperationException.translate(e); }
    }

    private NhnCinderAddonProperties.Binding binding(Target target) {
        var binding = properties.binding(target.namespace(), target.connectionName());
        if (!target.region().isBlank() && !target.region().equalsIgnoreCase(binding.getRegion()))
            throw new StorageOperationException(503, "NHN_ADDON_NOT_CONFIGURED", "The configured NHN region does not match the selected cloud connection.");
        return binding;
    }
    private K8sClusterDto lookup(String namespace, String clusterName) {
        try {
            var cluster = tumblebug.getK8sClusterByName(namespace, clusterName);
            if (cluster == null) throw new StorageOperationException(404, "NHN_CLUSTER_NOT_FOUND", "The selected Kubernetes cluster was not found.");
            return cluster;
        } catch (RuntimeException e) { throw StorageOperationException.translate(e); }
    }
    private static Target target(String namespace, String clusterName, K8sClusterDto cluster) {
        NhnNksApi.requireUuid(cluster.getCspResourceId());
        String connection = cluster.getConnectionName();
        if (connection == null || connection.isBlank()) connection = cluster.getConnectionConfig().getConfigName();
        if (connection == null || connection.isBlank()) throw new StorageOperationException(400, "NHN_CONNECTION_MISSING", "The selected NHN cluster has no cloud connection name.");
        String region = cluster.getConnectionConfig().getRegionZoneInfo() == null ? ""
                : cluster.getConnectionConfig().getRegionZoneInfo().getAssignedRegion();
        if ((region == null || region.isBlank()) && cluster.getConnectionConfig().getRegionDetail() != null) {
            // regionName is a display label. Older responses can provide the regional identifier in regionId.
            String regionalId = cluster.getConnectionConfig().getRegionDetail().getRegionId();
            region = regionalId != null && regionalId.matches("(?i)(?:kr[123]|jp1)") ? regionalId : "";
        }
        return new Target(namespace, clusterName, cluster.getCspResourceId(), connection, region == null ? "" : region.toLowerCase(Locale.ROOT));
    }
    private static boolean nhn(K8sClusterDto cluster) {
        return cluster.getConnectionConfig() != null && "nhn".equalsIgnoreCase(cluster.getConnectionConfig().getProviderName());
    }
    private static boolean driverReady(KubernetesClient client) {
        try { NhnStorageClassService.requireDriverReady(client); return true; }
        catch (StorageOperationException e) {
            if ("STORAGE_CLASS_SETUP_REQUIRED".equals(e.getCode())) return false;
            throw e;
        }
    }
    private static void validateNames(String namespace, String clusterName) {
        if (namespace == null || clusterName == null || !namespace.matches("[a-z0-9][a-z0-9_-]{0,62}")
                || !clusterName.matches("[a-z0-9][a-z0-9_-]{0,62}"))
            throw new StorageOperationException(400, "NHN_TARGET_REQUIRED", "Provide a valid project namespace and cluster name.");
    }
    private static boolean failed(String state) { return state.endsWith("_FAILED") || state.startsWith("DELETE_") || state.equals("FAILED"); }
    private static boolean complete(String state) { return state.equals("CREATE_COMPLETE") || state.equals("UPDATE_COMPLETE"); }
    private static void requireAddonNotFailed(String state) {
        if (failed(state)) throw new StorageOperationException(409, "NHN_ADDON_FAILED", "The existing Cinder add-on failed or is being deleted in NKS. Check NKS before retrying; AM will not replace it.");
    }
    private static void requireClusterReady(String state) {
        if (!complete(state)) throw new StorageOperationException(409, "NHN_CLUSTER_NOT_READY", "The NKS cluster is not ready for an add-on installation. Wait for its current operation to complete.");
    }
}
