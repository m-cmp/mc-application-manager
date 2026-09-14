package kr.co.mcmp.softwarecatalog.kubernetes.service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import kr.co.mcmp.softwarecatalog.SoftwareCatalog;
import kr.co.mcmp.softwarecatalog.application.constants.*;
import kr.co.mcmp.softwarecatalog.application.dto.*;
import kr.co.mcmp.softwarecatalog.application.model.DeploymentHistory;
import kr.co.mcmp.softwarecatalog.application.repository.DeploymentHistoryRepository;
import kr.co.mcmp.softwarecatalog.application.service.ObjectStorageAccessGrantService;
import kr.co.mcmp.softwarecatalog.application.service.tunnel.K8sObjectStorageTunnelService;
import kr.co.mcmp.softwarecatalog.application.service.tunnel.K8sObjectStorageTunnelRuntime;
import kr.co.mcmp.softwarecatalog.kubernetes.config.KubernetesClientFactory;
import kr.co.mcmp.softwarecatalog.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;

/** Kubernetes-native deployment of the existing Jupyter image and notebook. */
@Service
@RequiredArgsConstructor
public class K8sJupyterService {
    static final String OWNER = "mcmp.io/jupyter-deployment";
    private final KubernetesClientFactory clients;
    private final HelmChartService helm;
    private final ObjectStorageAccessGrantService grants;
    private final K8sIngressAccessService access;
    private final DeploymentHistoryRepository histories;
    private final UserRepository users;
    private final ObjectMapper mapper;
    private final K8sObjectStorageTunnelService tunnels;
    private final IbmIngressAutomationService automation;
    @Value("${app.object-storage.k8s-transport:SSH_TUNNEL}")
    private String transport = "SSH_TUNNEL";
    @Value("${app.object-storage.k8s-ssh-image:" + K8sSshSidecar.DEFAULT_IMAGE + "}")
    private String sshImage = K8sSshSidecar.DEFAULT_IMAGE;
    @Value("${app.object-storage.k8s-gateway-url:}")
    private String gatewayUrl;
    @Value("${app.object-storage.k8s-allow-http:false}")
    private boolean allowHttp;
    @Value("${app.object-storage.k8s-ready-timeout-seconds:1800}")
    private long readyTimeout;

    public boolean supports(SoftwareCatalog catalog) {
        return catalog != null && catalog.getPackageInfo() != null
                && catalog.getPackageInfo().getPackageName() != null
                && catalog.getPackageInfo().getPackageName().toLowerCase(Locale.ROOT).contains("jupyter");
    }

    static String workloadId(String cluster) { return "k8s:" + cluster; }

    static void validate(DeploymentRequest r, String gateway) {
        validate(r, gateway, false);
    }

    static void validate(DeploymentRequest r, String gateway, boolean allowHttp) {
        if (r.getServicePort() != null && r.getServicePort() != 8888)
            throw new IllegalArgumentException("K8s Jupyter uses service port 8888 and external Ingress port 30880.");
        if (!Boolean.TRUE.equals(r.getIngressEnabled())) throw new IllegalArgumentException("K8s Jupyter requires Ingress.");
        if (!"nginx".equals(r.getIngressClass()) && !IbmIngressSupport.managed(r.getIngressClass())) throw new IllegalArgumentException("Use nginx or an IBM managed NGINX Ingress class.");
        String host = r.getIngressHost();
        if (host == null || host.length() > 253 || !host.matches("(?=.{1,253}$)[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?")
                || host.contains("..") || host.contains("*") || host.matches("[0-9.]+"))
            throw new IllegalArgumentException("Enter a DNS hostname for Jupyter Ingress.");
        for (String label : host.split("\\.")) if (label.length() > 63 || label.startsWith("-") || label.endsWith("-"))
            throw new IllegalArgumentException("Invalid Ingress hostname.");
        if (!"/".equals(r.getIngressPath())) throw new IllegalArgumentException("Jupyter uses a dedicated hostname with path /.");
        if (Boolean.TRUE.equals(r.getIngressTlsEnabled()) && !IbmIngressSupport.managed(r.getIngressClass())) throw new IllegalArgumentException("This managed entry uses HTTP NodePort 30880; configure a separate HTTPS entry before enabling TLS.");
        if (Boolean.TRUE.equals(r.getHpaEnabled()) || Boolean.TRUE.equals(r.getWorkloadRebalancingEnabled())
                || (r.getMinReplicas() != null && r.getMinReplicas() != 1))
            throw new IllegalArgumentException("Jupyter requires one replica without HPA or workload rebalancing.");
        K8sIngressAccessService.validateCidr(r.getServicePortCidr());
        URI uri;
        try { uri = URI.create(gateway == null ? "" : gateway); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("Configure app.object-storage.k8s-gateway-url."); }
        if (uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                || Set.of("localhost", "127.0.0.1", "::1", "[::1]").contains(uri.getHost()))
            throw new IllegalArgumentException("Configure a Pod-reachable app.object-storage.k8s-gateway-url (not VM loopback).");
        if ("http".equals(uri.getScheme()) && !allowHttp && !(uri.getHost().endsWith(".svc") || uri.getHost().endsWith(".svc.cluster.local")))
            throw new IllegalArgumentException("Use HTTPS for the external AM Gateway, or set OBJECT_STORAGE_K8S_ALLOW_HTTP=true for HTTP testing.");
    }

    public synchronized DeploymentHistory deploy(DeploymentRequest request, SoftwareCatalog catalog) {
        access.resolveTarget(request, catalog);
        boolean sshTunnel = useSshTunnel();
        // Routing/CIDR requirements are identical; only DIRECT needs an external URL.
        validate(request, sshTunnel ? "https://unused.internal" : gatewayUrl, allowHttp);
        if (sshTunnel && (sshImage == null || sshImage.isBlank()))
            throw new IllegalArgumentException("OBJECT_STORAGE_K8S_SSH_IMAGE must reference a compatible LinuxServer OpenSSH image.");
        var storage = mapper.convertValue(request.getAdditionalConfig() == null ? null
                : request.getAdditionalConfig().get("objectStorage"), ObjectStorageConfiguration.class);
        if (storage == null || !Boolean.TRUE.equals(storage.getEnabled()) || storage.getJupyterToken() == null
                || storage.getJupyterToken().length() < 12)
            throw new IllegalArgumentException("Select Object Storage and a Jupyter token of at least 12 characters.");
        grants.resolveSelections(request.getNamespace(), storage);
        var previous = latest(request.getNamespace(), request.getClusterName(), catalog.getId());
        if (previous != null && !Set.of("UNINSTALLED", "FAILED").contains(Objects.toString(previous.getStatus(), "")))
            throw new IllegalStateException("A Jupyter installation already exists for this catalog and cluster.");
        DeploymentHistory history = histories.saveAndFlush(DeploymentHistory.builder()
                .namespace(request.getNamespace()).clusterName(request.getClusterName()).catalog(catalog)
                .executedBy(request.getUsername() == null ? null : users.findByUsername(request.getUsername()).orElse(null))
                .deploymentType(DeploymentType.K8S).actionType(ActionType.INSTALL).status("IN_PROGRESS")
                .executedAt(LocalDateTime.now()).servicePort(8888).build());
        String name = "mcmp-jupyter-" + history.getId();
        history.setReleaseName(name);
        history.setIngressHost(request.getIngressHost());
        history.setIngressPath("/");
        history.setIngressEnabled(true);
        history.setIngressClass(request.getIngressClass());
        histories.saveAndFlush(history);
        List<HasMetadata> created = new ArrayList<>();
        try (var client = clients.getClient(request.getNamespace(), request.getClusterName())) {
            if (client.namespaces().withName(request.getNamespace()).get() == null)
                throw new IllegalArgumentException("The target Kubernetes namespace must exist before installing Jupyter.");
            // Do not replace existing routes, even if owned by another application.
            boolean hostTaken = client.network().v1().ingresses().inAnyNamespace().list().getItems().stream()
                    .filter(i -> i.getSpec() != null && i.getSpec().getRules() != null)
                    .flatMap(i -> i.getSpec().getRules().stream()).anyMatch(r -> request.getIngressHost().equals(r.getHost()));
            if (hostTaken) throw new IllegalArgumentException("Ingress hostname is already in use.");
            if (IbmIngressSupport.managed(request.getIngressClass())) {
                var resolved = automation.prepare(client, request.getNamespace(), request.getClusterName(), request.getNamespace(),
                        DeploymentConfigDTO.from(request, catalog), message -> { });
                IbmIngressTlsResolver.apply(request, resolved);
                IbmIngressSupport.verify(client, resolved);
                history.setIngressTlsEnabled(true);
                history.setIngressTlsSecret(resolved.getIngressTlsSecret());
            } else {
                helm.ensureIngressController(client, request.getNamespace(), request.getClusterName());
                access.verifyController(client, request.getNamespace());
            }
            var issued = grants.issue(history.getId(), workloadId(request.getClusterName()), request.getNamespace(), storage);
            Secret sshSecret = sshTunnel ? tunnels.credentials(request.getNamespace(), name) : null;
            var resources = resources(request, catalog, name, issued.token(), storage.getJupyterToken(), sshSecret);
            // Ingress is last: no external route until the Pod has passed gateway checks.
            for (HasMetadata resource : resources) {
                if (resource instanceof Ingress) continue;
                created.add(client.resource(resource).create());
            }
            if (sshTunnel) {
                Deployment workload = (Deployment) created.stream().filter(Deployment.class::isInstance).findFirst().orElseThrow();
                Secret credential = (Secret) created.stream().filter(r -> r instanceof Secret && (name + "-ssh").equals(r.getMetadata().getName())).findFirst().orElseThrow();
                tunnels.register(history.getId(), request.getClusterName(), workload, credential);
            }
            waitForReady(client, request.getNamespace(), name, history.getId(), sshTunnel);
            access.open(request, history);
            for (HasMetadata resource : resources) if (resource instanceof Ingress) created.add(client.resource(resource).create());
            history.setStatus("SUCCESS");
            history.setPodStatus("Running");
            history.setUpdatedAt(LocalDateTime.now());
            return histories.saveAndFlush(history);
        } catch (Exception failure) {
            boolean clean = true;
            try { tunnels.remove(history.getId()); } catch (Exception cleanup) { clean = false; failure.addSuppressed(cleanup); }
            try { grants.revoke(history.getId(), workloadId(request.getClusterName())); } catch (Exception cleanup) { clean = false; failure.addSuppressed(cleanup); }
            try (var client = clients.getClient(request.getNamespace(), request.getClusterName())) {
                Collections.reverse(created);
                for (HasMetadata resource : created) {
                    // Retain notebook data if startup progressed far enough to create it.
                    if (!(resource instanceof PersistentVolumeClaim)) client.resource(resource).delete();
                }
            } catch (Exception cleanup) { clean = false; failure.addSuppressed(cleanup); }
            try { access.release(history.getId()); } catch (Exception cleanup) { clean = false; failure.addSuppressed(cleanup); }
            history.setStatus(clean ? "FAILED" : "DELETE_PENDING");
            histories.saveAndFlush(history);
            throw new DeploymentFailure(history, failure);
        }
    }

    List<HasMetadata> resources(DeploymentRequest r, SoftwareCatalog catalog, String name, String token, String login) throws Exception {
        return resources(r, catalog, name, token, login, null);
    }

    List<HasMetadata> resources(DeploymentRequest r, SoftwareCatalog catalog, String name, String token, String login, Secret sshSecret) throws Exception {
        String ns = r.getNamespace();
        Map<String, String> labels = Map.of(OWNER, name, "app.kubernetes.io/instance", name, "app.kubernetes.io/name", "jupyter");
        Map<String, Object> metadata = Map.of("name", name, "namespace", ns, "labels", labels);
        List<HasMetadata> result = new ArrayList<>();
        result.add(mapper.convertValue(Map.of("apiVersion", "v1", "kind", "Secret", "metadata", metadata,
                "stringData", Map.of("MCMP_OBJECT_STORAGE_TOKEN", token, "JUPYTER_TOKEN", login,
                        "MCMP_OBJECT_STORAGE_GATEWAY_URL", sshSecret != null ? K8sObjectStorageTunnelRuntime.GATEWAY : gatewayUrl.replaceAll("/+$", ""))), Secret.class));
        String notebook;
        try (var input = new ClassPathResource("notebooks/object-storage.ipynb").getInputStream()) {
            notebook = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        result.add(mapper.convertValue(Map.of("apiVersion", "v1", "kind", "ConfigMap", "metadata", metadata,
                "data", Map.of("sample-data.ipynb", notebook, "startup.py", startupScript())), ConfigMap.class));
        var config = r.getAdditionalConfig() == null ? Map.of() : r.getAdditionalConfig();
        String storageClass = Objects.toString(config.get("storageClass"), "");
        Map<String,Object> pvcSpec = new LinkedHashMap<>(Map.of("accessModes", List.of("ReadWriteOnce"),
                "resources", Map.of("requests", Map.of("storage", "10Gi"))));
        if (!storageClass.isBlank()) pvcSpec.put("storageClassName", storageClass);
        result.add(mapper.convertValue(Map.of("apiVersion", "v1", "kind", "PersistentVolumeClaim", "metadata", metadata, "spec", pvcSpec), PersistentVolumeClaim.class));
        String image = catalog.getPackageInfo().getPackageName() + ":" + catalog.getPackageInfo().getPackageVersion();
        Map<String,Object> container = new LinkedHashMap<>();
        container.put("name", "jupyter"); container.put("image", image);
        container.put("command", List.of("python", "/opt/mcmp/startup.py"));
        container.put("envFrom", List.of(Map.of("secretRef", Map.of("name", name))));
        container.put("ports", List.of(Map.of("name", "http", "containerPort", 8888)));
        container.put("volumeMounts", List.of(Map.of("name", "work", "mountPath", "/home/jovyan/work"),
                Map.of("name", "templates", "mountPath", "/opt/mcmp", "readOnly", true)));
        container.put("readinessProbe", Map.of("tcpSocket", Map.of("port", "http"), "periodSeconds", 5));
        container.put("resources", Map.of("requests", Map.of("cpu", "500m", "memory", "1Gi"),
                "limits", Map.of("cpu", Objects.toString(catalog.getRecommendedCpu(), "2"), "memory", "4Gi")));
        result.add(mapper.convertValue(Map.of("apiVersion", "apps/v1", "kind", "Deployment", "metadata", metadata,
                "spec", Map.of("replicas", 1, "strategy", Map.of("type", "Recreate"), "selector", Map.of("matchLabels", Map.of(OWNER, name)),
                        "template", Map.of("metadata", Map.of("labels", labels), "spec", Map.of(
                                "automountServiceAccountToken", false, "securityContext", Map.of("fsGroup", 100),
                                "containers", List.of(container), "volumes", List.of(
                                        Map.of("name", "work", "persistentVolumeClaim", Map.of("claimName", name)),
                                        Map.of("name", "templates", "configMap", Map.of("name", name))))))), Deployment.class));
        result.add(mapper.convertValue(Map.of("apiVersion", "v1", "kind", "Service", "metadata", metadata,
                "spec", Map.of("type", "ClusterIP", "selector", Map.of(OWNER, name),
                        "ports", List.of(Map.of("name", "http", "port", 8888, "targetPort", "http")))), io.fabric8.kubernetes.api.model.Service.class));
        var ingressMeta = new LinkedHashMap<>(metadata);
        var ingressAnnotations = new LinkedHashMap<String,String>(Map.of(K8sIngressAccessService.CIDR_ANNOTATION, K8sIngressAccessService.validateCidr(r.getServicePortCidr()),
                "nginx.ingress.kubernetes.io/proxy-read-timeout", "3600", "nginx.ingress.kubernetes.io/proxy-send-timeout", "3600",
                "nginx.ingress.kubernetes.io/proxy-body-size", "100m"));
        if (IbmIngressSupport.managed(r.getIngressClass()) && !Boolean.TRUE.equals(r.getIngressTlsEnabled())) {
            ingressAnnotations.put("nginx.ingress.kubernetes.io/ssl-redirect", "false");
            ingressAnnotations.put("nginx.ingress.kubernetes.io/force-ssl-redirect", "false");
        }
        ingressMeta.put("annotations", ingressAnnotations);
        Map<String, Object> ingressSpec = new LinkedHashMap<>(Map.of("ingressClassName", r.getIngressClass(), "rules", List.of(Map.of("host", r.getIngressHost(),
                        "http", Map.of("paths", List.of(Map.of("path", "/", "pathType", "Prefix", "backend", Map.of(
                                "service", Map.of("name", name, "port", Map.of("number", 8888))))))))));
        if (IbmIngressSupport.managed(r.getIngressClass()) && Boolean.TRUE.equals(r.getIngressTlsEnabled())) {
            ingressSpec.put("tls", List.of(Map.of("hosts", List.of(r.getIngressHost()), "secretName", r.getIngressTlsSecret())));
        }
        result.add(mapper.convertValue(Map.of("apiVersion", "networking.k8s.io/v1", "kind", "Ingress", "metadata", ingressMeta,
                "spec", ingressSpec), Ingress.class));
        if (sshSecret != null) {
            result.add(0, K8sSshSidecar.configuration(sshSecret.getMetadata().getName(), ns, labels));
            K8sSshSidecar.attach((Deployment) result.stream().filter(Deployment.class::isInstance).findFirst().orElseThrow(), sshImage, sshSecret.getMetadata().getName());
            result.add(0, sshSecret);
        }
        return result;
    }

    boolean useSshTunnel() {
        if ("SSH_TUNNEL".equalsIgnoreCase(transport)) return true;
        if ("DIRECT".equalsIgnoreCase(transport)) return false;
        throw new IllegalArgumentException("OBJECT_STORAGE_K8S_TRANSPORT must be SSH_TUNNEL or DIRECT.");
    }

    private void waitForReady(KubernetesClient client, String ns, String name, Long id, boolean sshTunnel) throws InterruptedException {
        if (!sshTunnel) {
            client.apps().deployments().inNamespace(ns).withName(name).waitUntilReady(readyTimeout, TimeUnit.SECONDS);
            return;
        }
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(readyTimeout);
        while (System.nanoTime() < deadline) {
            try { tunnels.ensure(id); } catch (IllegalStateException retry) { /* Sidecar/image/API may not be ready yet. */ }
            if (client.apps().deployments().inNamespace(ns).withName(name).isReady()) return;
            Thread.sleep(2000);
        }
        throw new IllegalStateException("Jupyter SSH readiness timed out; check sidecar image, pods/portforward permission and AM tunnel status.");
    }

    static String startupScript() {
        return """
                import os, sys, shutil, time
                from pathlib import Path
                import requests
                gateway = os.environ['MCMP_OBJECT_STORAGE_GATEWAY_URL'].rstrip('/')
                headers = {'Authorization': 'Bearer ' + os.environ['MCMP_OBJECT_STORAGE_TOKEN']}
                for attempt in range(12):
                    try:
                        reply = requests.get(gateway + '/storages', headers=headers, timeout=20)
                        reply.raise_for_status()
                        payload = reply.json()
                        if payload.get('code') != 200 or not payload.get('data'):
                            raise ValueError('No storage grants')
                        for storage in payload['data']:
                            reply = requests.get(gateway + '/objects', headers=headers, params={'storage': storage['alias']}, timeout=20)
                            reply.raise_for_status()
                            if reply.json().get('code') != 200: raise ValueError('Storage check failed')
                        break
                    except Exception as error:
                        print('AM Object Storage connection check failed (' + type(error).__name__ + '); retrying.', flush=True)
                        time.sleep(5)
                else:
                    sys.exit('AM Object Storage unavailable. Check SSH tunnel or Gateway URL, network and grant.')
                path = Path('/home/jovyan/work/sample-data.ipynb')
                if not path.exists(): shutil.copyfile('/opt/mcmp/sample-data.ipynb', path)
                path.chmod(0o600)
                os.execvp('start-notebook.py', ['start-notebook.py', '--ServerApp.port=8888', '--ServerApp.default_url=/lab/tree/sample-data.ipynb'])
                """;
    }

    public DeploymentHistory latest(String ns, String cluster, Long catalog) {
        // A rejected request has no managed release and must not hide an existing installation.
        return histories.findTopByCatalogIdAndClusterNameAndNamespaceAndActionTypeAndReleaseNameStartingWithOrderByExecutedAtDesc(
                catalog, cluster, ns, ActionType.INSTALL, "mcmp-jupyter-");
    }

    public Map<String,Integer> scale(String ns, String cluster, Long catalog, int replicas, boolean restart) {
        var history = requireHistory(ns, cluster, catalog);
        try (var client = clients.getClient(ns, cluster)) {
            var resource = client.apps().deployments().inNamespace(ns).withName(history.getReleaseName());
            var existing = resource.get();
            if (existing == null || !history.getReleaseName().equals(existing.getMetadata().getLabels().get(OWNER)))
                throw new IllegalStateException("Owned Jupyter Deployment not found.");
            int old = existing.getSpec().getReplicas();
            if (replicas == 0 || restart) tunnels.suspend(history.getId());
            resource.scale(replicas);
            if (restart) resource.rolling().restart();
            if (replicas > 0) {
                tunnels.resume(history.getId());
                try { waitForReady(client, ns, history.getReleaseName(), history.getId(), tunnels.exists(history.getId())); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("Jupyter start interrupted"); }
            }
            history.setStatus(replicas == 0 ? "STOPPED" : "SUCCESS");
            history.setUpdatedAt(LocalDateTime.now());
            histories.saveAndFlush(history);
            return Map.of(history.getReleaseName(), old);
        }
    }

    public void uninstall(String ns, String cluster, Long catalog) {
        var history = requireHistory(ns, cluster, catalog);
        String name = history.getReleaseName();
        tunnels.remove(history.getId());
        grants.revoke(history.getId(), workloadId(cluster));
        try (var client = clients.getClient(ns, cluster)) {
            client.network().v1().ingresses().inNamespace(ns).withLabel(OWNER, name).delete();
            client.apps().deployments().inNamespace(ns).withLabel(OWNER, name).delete();
            client.services().inNamespace(ns).withLabel(OWNER, name).delete();
            client.secrets().inNamespace(ns).withLabel(OWNER, name).delete();
            client.configMaps().inNamespace(ns).withLabel(OWNER, name).delete();
            access.release(history.getId());
            history.setStatus("UNINSTALLED");
            history.setUpdatedAt(LocalDateTime.now());
            histories.saveAndFlush(history);
            // PVC intentionally retained; never destroy user notebooks during uninstall.
        } catch (RuntimeException e) {
            history.setStatus("DELETE_PENDING"); histories.saveAndFlush(history); throw e;
        }
    }

    private DeploymentHistory requireHistory(String ns, String cluster, Long catalog) {
        var history = latest(ns, cluster, catalog);
        if (history == null || history.getReleaseName() == null || !history.getReleaseName().startsWith("mcmp-jupyter-"))
            throw new IllegalStateException("Managed K8s Jupyter installation not found.");
        return history;
    }

    static final class DeploymentFailure extends IllegalStateException {
        final DeploymentHistory history;
        DeploymentFailure(DeploymentHistory history, Exception cause) {
            super("K8s Jupyter installation failed; inspect Pod events and AM Gateway connectivity. Deployment " + history.getId(), cause);
            this.history = history;
        }
    }
}
