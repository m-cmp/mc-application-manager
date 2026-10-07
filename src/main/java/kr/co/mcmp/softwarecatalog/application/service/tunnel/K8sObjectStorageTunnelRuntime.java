package kr.co.mcmp.softwarecatalog.application.service.tunnel;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import kr.co.mcmp.softwarecatalog.application.model.K8sObjectStorageTunnel;
import kr.co.mcmp.softwarecatalog.kubernetes.config.KubernetesClientFactory;
import kr.co.mcmp.softwarecatalog.kubernetes.config.KubernetesNamespaces;
import lombok.RequiredArgsConstructor;

/** AM -> Kubernetes port-forward -> SSH server; reverse forwarding remains Pod-local. */
@Component @RequiredArgsConstructor
public class K8sObjectStorageTunnelRuntime {
    public static final String OWNER = "mcmp.io/jupyter-deployment";
    public static final String GATEWAY = "http://127.0.0.1:18084" + ObjectStorageTunnelProxy.PREFIX;
    private final KubernetesClientFactory clients;
    private final ObjectStorageTunnelProxy proxy;
    @Value("${app.object-storage.tunnel.ssh-executable:ssh}") private String ssh = "ssh";
    @Value("${app.object-storage.k8s-ssh-keygen:ssh-keygen}") private String keygen = "ssh-keygen";

    public Secret credentials(String namespace, String name) {
        Path directory = null;
        try {
            directory = ObjectStorageTunnelRuntime.privateDirectory();
            for (String key : List.of("client", "host")) {
                Process p = new ProcessBuilder(keygen, "-q", "-t", "ed25519", "-N", "", "-C", "mcmp-k8s", "-f", directory.resolve(key).toString())
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
                if (!p.waitFor(15, TimeUnit.SECONDS)) { p.destroyForcibly(); throw new IllegalStateException("SSH key generation timed out"); }
                if (p.exitValue() != 0) throw new IllegalStateException("SSH key generation failed");
            }
            return new SecretBuilder().withNewMetadata().withName(name + "-ssh").withNamespace(namespace)
                    .addToLabels(OWNER, name).endMetadata().withType("Opaque").withImmutable(true)
                    .withStringData(Map.of("client-key", Files.readString(directory.resolve("client")),
                            "host-key", Files.readString(directory.resolve("host")),
                            "host-public", publicKey(Files.readString(directory.resolve("host.pub"))),
                            "authorized_keys", "restrict,port-forwarding,permitlisten=\"127.0.0.1:18084\" " + publicKey(Files.readString(directory.resolve("client.pub"))) + "\n"))
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot create K8s SSH credentials; install ssh-keygen on AM.");
        } finally { erase(directory); }
    }

    static String publicKey(String value) {
        String[] parts = value.strip().split("\\s+");
        if (parts.length < 2 || !"ssh-ed25519".equals(parts[0]) || !parts[1].matches("[A-Za-z0-9+/=]+"))
            throw new IllegalArgumentException("Invalid pinned SSH host key");
        return parts[0] + " " + parts[1];
    }

    public Running start(K8sObjectStorageTunnel tunnel) {
        KubernetesClient client = null; LocalPortForward forward = null; Process process = null; Path directory = null;
        try {
            client = clients.getClient(tunnel.getNamespace(), tunnel.getClusterName());
            String workloadNamespace = KubernetesNamespaces.jupyterWorkloadNamespace(client, tunnel.getNamespace(), tunnel.getReleaseName());
            var deployment = client.apps().deployments().inNamespace(workloadNamespace).withName(tunnel.getReleaseName()).get();
            if (deployment == null || !tunnel.getWorkloadUid().equals(deployment.getMetadata().getUid())
                    || deployment.getSpec().getReplicas() == 0) throw new IllegalStateException("Owned workload is not running");
            Secret secret = client.secrets().inNamespace(workloadNamespace).withName(tunnel.getReleaseName() + "-ssh").get();
            if (secret == null || !tunnel.getSecretUid().equals(secret.getMetadata().getUid())) throw new IllegalStateException("SSH Secret identity changed");
            var pod = client.pods().inNamespace(workloadNamespace).withLabel(OWNER, tunnel.getReleaseName()).list().getItems().stream()
                    .filter(p -> p.getMetadata().getDeletionTimestamp() == null && p.getStatus() != null && p.getStatus().getContainerStatuses() != null)
                    .filter(p -> p.getStatus().getContainerStatuses().stream().anyMatch(c -> "ssh-tunnel".equals(c.getName()) && c.getState() != null && c.getState().getRunning() != null))
                    .findFirst().orElseThrow(() -> new IllegalStateException("SSH sidecar has not started"));
            directory = ObjectStorageTunnelRuntime.privateDirectory();
            Files.writeString(directory.resolve("key"), decode(secret, "client-key"));
            Files.writeString(directory.resolve("known_hosts"), "mcmp-k8s-" + tunnel.getDeploymentId() + " " + publicKey(decode(secret, "host-public")) + "\n");
            Files.writeString(directory.resolve("config"), "");
            // The directory is private on POSIX and Windows; SSH also requires a private key file.
            if (Files.getFileStore(directory).supportsFileAttributeView("posix"))
                Files.setPosixFilePermissions(directory.resolve("key"), java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
            forward = client.pods().inNamespace(workloadNamespace).withName(pod.getMetadata().getName())
                    .portForward(2222, InetAddress.getByName("127.0.0.1"), 0);
            process = new ProcessBuilder(sshArguments(ssh, tunnel.getDeploymentId(), directory, forward.getLocalPort(), proxy.port()))
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            return new Running(client, forward, process, directory, workloadNamespace, pod.getMetadata().getName(), pod.getMetadata().getUid());
        } catch (Exception e) {
            if (process != null) process.destroyForcibly();
            if (forward != null) try { forward.close(); } catch (Exception ignored) { }
            if (client != null) client.close();
            erase(directory);
            // Kubernetes/SSH exception messages can include credentials or protocol payloads.
            throw new IllegalStateException("K8s SSH tunnel unavailable; check Pod sidecar, API access and pods/portforward permission.");
        }
    }

    static List<String> sshArguments(String ssh, Long id, Path directory, int port, int proxyPort) {
        if (id == null || id <= 0 || port < 1 || port > 65535 || proxyPort < 1 || proxyPort > 65535) throw new IllegalArgumentException("Invalid SSH tunnel parameters");
        return List.of(ssh, "-F", directory.resolve("config").toString(), "-N", "-T", "-n", "-i", directory.resolve("key").toString(),
                "-o", "BatchMode=yes", "-o", "IdentitiesOnly=yes", "-o", "StrictHostKeyChecking=yes",
                "-o", "UserKnownHostsFile=\"" + directory.resolve("known_hosts").toAbsolutePath().toString().replace('\\', '/') + "\"",
                "-o", "HostKeyAlias=mcmp-k8s-" + id, "-o", "CheckHostIP=no", "-o", "ConnectTimeout=10",
                "-o", "ServerAliveInterval=15", "-o", "ServerAliveCountMax=3", "-o", "ExitOnForwardFailure=yes",
                "-p", String.valueOf(port), "-l", "mcmp", "-R", "127.0.0.1:18084:127.0.0.1:" + proxyPort, "127.0.0.1");
    }

    private static String decode(Secret secret, String key) { return new String(Base64.getDecoder().decode(secret.getData().get(key)), StandardCharsets.UTF_8); }
    private static void erase(Path directory) {
        if (directory == null) return;
        for (String key : List.of("client", "client.pub", "host", "host.pub", "key", "known_hosts", "config"))
            try { Files.deleteIfExists(directory.resolve(key)); } catch (Exception ignored) { }
        try { Files.deleteIfExists(directory); } catch (Exception ignored) { }
    }

    public static class Running implements AutoCloseable {
        private final KubernetesClient client; private final LocalPortForward forward; private final Process process;
        private final Path directory; private final String namespace, podName, podUid;
        Running(KubernetesClient client, LocalPortForward forward, Process process, Path directory, String namespace, String podName, String podUid) {
            this.client=client; this.forward=forward; this.process=process; this.directory=directory;
            this.namespace=namespace; this.podName=podName; this.podUid=podUid;
        }
        public boolean alive() { return process.isAlive() && forward.isAlive() && !forward.errorOccurred(); }
        public boolean currentPod() {
            var pod=client.pods().inNamespace(namespace).withName(podName).get();
            return pod != null && podUid.equals(pod.getMetadata().getUid()) && pod.getMetadata().getDeletionTimestamp() == null;
        }
        public boolean ready() {
            var pod=client.pods().inNamespace(namespace).withName(podName).get();
            return pod != null && podUid.equals(pod.getMetadata().getUid()) && pod.getStatus() != null
                    && pod.getStatus().getContainerStatuses() != null && pod.getStatus().getContainerStatuses().stream()
                    .anyMatch(c -> "ssh-tunnel".equals(c.getName()) && Boolean.TRUE.equals(c.getReady()));
        }
        @Override public void close() {
            process.destroy();
            try { if (!process.waitFor(3, TimeUnit.SECONDS)) process.destroyForcibly(); }
            catch (InterruptedException e) { process.destroyForcibly(); Thread.currentThread().interrupt(); }
            try { forward.close(); } catch (Exception ignored) { }
            client.close(); erase(directory);
        }
    }
}
