package kr.co.mcmp.softwarecatalog.kubernetes.service;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import io.fabric8.kubernetes.api.model.apps.DaemonSetBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder;
import io.fabric8.kubernetes.api.model.autoscaling.v2.HorizontalPodAutoscaler;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.Serialization;
import kr.co.mcmp.softwarecatalog.SoftwareCatalog;
import kr.co.mcmp.softwarecatalog.kubernetes.config.KubernetesClientFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class KubernetesOperationService {

    private static final String DEFAULT_HELM_NAMESPACE = "default";
    private static final Set<String> RESTARTABLE_WORKLOAD_KINDS = Set.of("Deployment", "StatefulSet", "DaemonSet");
    private static final Set<String> SCALABLE_WORKLOAD_KINDS = Set.of("Deployment", "StatefulSet");
    private static final String RESTARTED_AT_ANNOTATION = "kubectl.kubernetes.io/restartedAt";
    private static final long HELM_COMMAND_TIMEOUT_SECONDS = 120;

    private final HelmChartService helmChartService;
    private final KubernetesClientFactory kubernetesClientFactory;

    public void restartApplication(String namespace, String clusterName, SoftwareCatalog catalog, String username) {
        try {
            String manifest = getInstalledReleaseManifest(namespace, clusterName, catalog);
            List<WorkloadRef> workloads = parseWorkloads(manifest, true);
            if (workloads.isEmpty()) {
                throw new RuntimeException("No restartable workload found in Helm release manifest");
            }

            String restartedAt = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            try (KubernetesClient client = kubernetesClientFactory.getClient(namespace, clusterName)) {
                for (WorkloadRef workload : workloads) {
                    restartWorkload(client, workload, restartedAt);
                }
            }
            log.info("Restarted K8s workloads for catalog {}: {}", catalog.getName(), workloads);
        } catch (Exception e) {
            log.error("Application restart failed", e);
            throw new RuntimeException("Application restart failed", e);
        }
    }

    public Map<String, Integer> stopApplication(String namespace, String clusterName, SoftwareCatalog catalog, String username) {
        try {
            String manifest = getInstalledReleaseManifest(namespace, clusterName, catalog);
            List<WorkloadRef> workloads = parseWorkloads(manifest, false);
            if (workloads.isEmpty()) {
                throw new RuntimeException("No scalable workload found in Helm release manifest");
            }
            List<HpaRef> hpas = parseHpas(manifest, workloads);

            Map<String, Integer> previousReplicas = new LinkedHashMap<>();
            try (KubernetesClient client = kubernetesClientFactory.getClient(namespace, clusterName)) {
                for (HpaRef hpa : hpas) {
                    deleteHpa(client, hpa);
                }

                for (WorkloadRef workload : workloads) {
                    int replicas = getCurrentReplicas(client, workload);
                    previousReplicas.put(workload.key(), Math.max(replicas, fallbackReplicas(workload)));
                    scaleWorkload(client, workload, 0);
                }
            }
            log.info("Stopped K8s workloads for catalog {}: {}", catalog.getName(), previousReplicas);
            return previousReplicas;
        } catch (Exception e) {
            log.error("Application stop failed", e);
            throw new RuntimeException("Application stop failed", e);
        }
    }

    public void startApplication(String namespace, String clusterName, SoftwareCatalog catalog, Map<String, Integer> previousReplicas, String username) {
        try {
            String manifest = getInstalledReleaseManifest(namespace, clusterName, catalog);
            List<WorkloadRef> workloads = parseWorkloads(manifest, false);
            if (workloads.isEmpty()) {
                throw new RuntimeException("No scalable workload found in Helm release manifest");
            }
            List<HpaRef> hpas = parseHpas(manifest, workloads);

            try (KubernetesClient client = kubernetesClientFactory.getClient(namespace, clusterName)) {
                for (WorkloadRef workload : workloads) {
                    int replicas = previousReplicas != null && previousReplicas.get(workload.key()) != null
                            ? previousReplicas.get(workload.key())
                            : fallbackReplicas(workload);
                    scaleWorkload(client, workload, Math.max(replicas, 1));
                }

                for (HpaRef hpa : hpas) {
                    restoreHpa(client, hpa);
                }
            }
            log.info("Started K8s workloads for catalog {}: {}", catalog.getName(), workloads);
        } catch (Exception e) {
            log.error("Application start failed", e);
            throw new RuntimeException("Application start failed", e);
        }
    }

    public void uninstallApplication(String namespace, String clusterName, SoftwareCatalog catalog, String username) {
        java.nio.file.Path tempKubeconfigPath = null;
        try {
            String kubeconfigYaml = getKubeconfigForCluster(namespace, clusterName);
            tempKubeconfigPath = createTempKubeconfigFile(kubeconfigYaml);
            String releaseName = findInstalledReleaseName(DEFAULT_HELM_NAMESPACE, tempKubeconfigPath, catalog);
            if (releaseName == null) {
                releaseName = catalog.getHelmChart().getChartName();
                log.warn("Installed release was not found. Trying chart name as release name: {}", releaseName);
            }

            runHelmUninstallCli(releaseName, DEFAULT_HELM_NAMESPACE, tempKubeconfigPath);
            log.info("Application uninstall completed: {}", releaseName);
        } catch (Exception e) {
            log.error("Application uninstall failed", e);
            throw new RuntimeException("Application uninstall failed", e);
        } finally {
            deleteTempKubeconfig(tempKubeconfigPath);
        }
    }

    private String getInstalledReleaseManifest(String namespace, String clusterName, SoftwareCatalog catalog) throws Exception {
        java.nio.file.Path tempKubeconfigPath = null;
        try {
            String kubeconfigYaml = getKubeconfigForCluster(namespace, clusterName);
            tempKubeconfigPath = createTempKubeconfigFile(kubeconfigYaml);
            String releaseName = findInstalledReleaseName(DEFAULT_HELM_NAMESPACE, tempKubeconfigPath, catalog);
            if (releaseName == null) {
                throw new RuntimeException("Installed Helm release was not found");
            }
            return runHelmGetManifestCli(releaseName, DEFAULT_HELM_NAMESPACE, tempKubeconfigPath);
        } finally {
            deleteTempKubeconfig(tempKubeconfigPath);
        }
    }

    private void restartWorkload(KubernetesClient client, WorkloadRef workload, String restartedAt) {
        switch (workload.kind) {
            case "Deployment":
                client.apps().deployments()
                        .inNamespace(workload.namespace)
                        .withName(workload.name)
                        .edit(item -> new DeploymentBuilder(item)
                                .editSpec()
                                .editTemplate()
                                .editOrNewMetadata()
                                .addToAnnotations(RESTARTED_AT_ANNOTATION, restartedAt)
                                .endMetadata()
                                .endTemplate()
                                .endSpec()
                                .build());
                break;
            case "StatefulSet":
                client.apps().statefulSets()
                        .inNamespace(workload.namespace)
                        .withName(workload.name)
                        .edit(item -> new StatefulSetBuilder(item)
                                .editSpec()
                                .editTemplate()
                                .editOrNewMetadata()
                                .addToAnnotations(RESTARTED_AT_ANNOTATION, restartedAt)
                                .endMetadata()
                                .endTemplate()
                                .endSpec()
                                .build());
                break;
            case "DaemonSet":
                client.apps().daemonSets()
                        .inNamespace(workload.namespace)
                        .withName(workload.name)
                        .edit(item -> new DaemonSetBuilder(item)
                                .editSpec()
                                .editTemplate()
                                .editOrNewMetadata()
                                .addToAnnotations(RESTARTED_AT_ANNOTATION, restartedAt)
                                .endMetadata()
                                .endTemplate()
                                .endSpec()
                                .build());
                break;
            default:
                throw new IllegalArgumentException("Unsupported restart workload kind: " + workload.kind);
        }
    }

    private int getCurrentReplicas(KubernetesClient client, WorkloadRef workload) {
        Integer replicas;
        switch (workload.kind) {
            case "Deployment":
                var deployment = client.apps().deployments().inNamespace(workload.namespace).withName(workload.name).get();
                if (deployment == null) {
                    throw new RuntimeException("Deployment not found: " + workload.name);
                }
                replicas = deployment.getSpec() != null ? deployment.getSpec().getReplicas() : null;
                break;
            case "StatefulSet":
                var statefulSet = client.apps().statefulSets().inNamespace(workload.namespace).withName(workload.name).get();
                if (statefulSet == null) {
                    throw new RuntimeException("StatefulSet not found: " + workload.name);
                }
                replicas = statefulSet.getSpec() != null ? statefulSet.getSpec().getReplicas() : null;
                break;
            default:
                throw new IllegalArgumentException("Unsupported scale workload kind: " + workload.kind);
        }
        return replicas != null ? replicas : fallbackReplicas(workload);
    }

    private void scaleWorkload(KubernetesClient client, WorkloadRef workload, int replicas) {
        switch (workload.kind) {
            case "Deployment":
                client.apps().deployments()
                        .inNamespace(workload.namespace)
                        .withName(workload.name)
                        .edit(item -> new DeploymentBuilder(item)
                                .editSpec()
                                .withReplicas(replicas)
                                .endSpec()
                                .build());
                break;
            case "StatefulSet":
                client.apps().statefulSets()
                        .inNamespace(workload.namespace)
                        .withName(workload.name)
                        .edit(item -> new StatefulSetBuilder(item)
                                .editSpec()
                                .withReplicas(replicas)
                                .endSpec()
                                .build());
                break;
            default:
                throw new IllegalArgumentException("Unsupported scale workload kind: " + workload.kind);
        }
    }

    private void deleteHpa(KubernetesClient client, HpaRef hpa) {
        client.autoscaling().v2().horizontalPodAutoscalers()
                .inNamespace(hpa.namespace)
                .withName(hpa.name)
                .delete();
        log.info("Deleted HPA before stop: {}/{}", hpa.namespace, hpa.name);
    }

    private void restoreHpa(KubernetesClient client, HpaRef hpaRef) {
        HorizontalPodAutoscaler hpa = Serialization.unmarshal(hpaRef.yaml, HorizontalPodAutoscaler.class);
        if (hpa.getMetadata() != null && hpa.getMetadata().getNamespace() == null) {
            hpa.getMetadata().setNamespace(hpaRef.namespace);
        }
        client.autoscaling().v2().horizontalPodAutoscalers()
                .inNamespace(hpaRef.namespace)
                .resource(hpa)
                .createOrReplace();
        log.info("Restored HPA after start: {}/{}", hpaRef.namespace, hpaRef.name);
    }

    private List<WorkloadRef> parseWorkloads(String manifest, boolean includeDaemonSet) {
        Set<String> supportedKinds = includeDaemonSet ? RESTARTABLE_WORKLOAD_KINDS : SCALABLE_WORKLOAD_KINDS;
        List<WorkloadRef> workloads = new ArrayList<>();
        for (ManifestDoc doc : parseManifestDocs(manifest)) {
            if (!supportedKinds.contains(doc.kind())) {
                continue;
            }
            String name = stringAt(doc.content(), "metadata", "name");
            if (name == null) {
                continue;
            }
            String namespace = stringAt(doc.content(), "metadata", "namespace");
            Integer replicas = integerAt(doc.content(), "spec", "replicas");
            workloads.add(new WorkloadRef(doc.kind(), name, namespace != null ? namespace : DEFAULT_HELM_NAMESPACE, replicas));
        }
        return workloads;
    }

    private List<HpaRef> parseHpas(String manifest, List<WorkloadRef> workloads) {
        List<HpaRef> hpas = new ArrayList<>();
        for (ManifestDoc doc : parseManifestDocs(manifest)) {
            if (!"HorizontalPodAutoscaler".equals(doc.kind())) {
                continue;
            }
            String name = stringAt(doc.content(), "metadata", "name");
            if (name == null) {
                continue;
            }
            String namespace = stringAt(doc.content(), "metadata", "namespace");
            String targetKind = stringAt(doc.content(), "spec", "scaleTargetRef", "kind");
            String targetName = stringAt(doc.content(), "spec", "scaleTargetRef", "name");
            HpaRef hpa = new HpaRef(name, namespace != null ? namespace : DEFAULT_HELM_NAMESPACE, targetKind, targetName, doc.yaml());
            if (workloads.stream().anyMatch(hpa::targets)) {
                hpas.add(hpa);
            }
        }
        return hpas;
    }

    private List<ManifestDoc> parseManifestDocs(String manifest) {
        List<ManifestDoc> docs = new ArrayList<>();
        Yaml yaml = new Yaml();
        String[] rawDocs = manifest.split("(?m)^---\\s*$");
        for (String rawDoc : rawDocs) {
            String document = rawDoc.trim();
            if (document.isEmpty()) {
                continue;
            }
            Object loaded = yaml.load(document);
            if (!(loaded instanceof Map<?, ?> map)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> content = (Map<String, Object>) map;
            String kind = stringAt(content, "kind");
            if (kind != null) {
                docs.add(new ManifestDoc(kind, content, document));
            }
        }
        return docs;
    }

    private int fallbackReplicas(WorkloadRef workload) {
        return workload.manifestReplicas != null && workload.manifestReplicas > 0 ? workload.manifestReplicas : 1;
    }

    private String stringAt(Map<String, Object> source, String... path) {
        Object value = valueAt(source, path);
        return value != null ? String.valueOf(value) : null;
    }

    private Integer integerAt(Map<String, Object> source, String... path) {
        Object value = valueAt(source, path);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Object valueAt(Map<String, Object> source, String... path) {
        Object current = source;
        for (String key : path) {
            if (!(current instanceof Map<?, ?> currentMap)) {
                return null;
            }
            current = currentMap.get(key);
        }
        return current;
    }

    private String getKubeconfigForCluster(String namespace, String clusterName) throws Exception {
        return helmChartService.getKubeconfigForCluster(namespace, clusterName);
    }

    private String findInstalledReleaseName(String namespace, java.nio.file.Path kubeconfig, SoftwareCatalog catalog) throws Exception {
        java.util.List<String> cmd = new java.util.ArrayList<>();
        cmd.add(helmChartService.getHelmPath()); cmd.add("list");
        cmd.add("--namespace"); cmd.add(namespace);
        cmd.add("--output"); cmd.add("json");
        if (kubeconfig != null) {
            cmd.add("--kubeconfig"); cmd.add(kubeconfig.toString());
        }

        CommandResult result = runCommand(cmd);

        if (result.exitCode != 0) {
            log.warn("helm list failed: {}", result.stderr);
            return null;
        }

        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode releases = mapper.readTree(result.stdout);

            if (releases.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode release : releases) {
                    String releaseName = release.path("name").asText();
                    String chartName = release.path("chart").asText();

                    if (chartName.contains(catalog.getHelmChart().getChartName())) {
                        log.info("Found Helm release: {} (chart: {})", releaseName, chartName);
                        return releaseName;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse Helm release list: {}", e.getMessage());
        }

        log.warn("Installed Helm release was not found. chartName={}", catalog.getHelmChart().getChartName());
        return null;
    }

    private String runHelmGetManifestCli(String releaseName, String namespace, java.nio.file.Path kubeconfig) throws Exception {
        java.util.List<String> cmd = new java.util.ArrayList<>();
        cmd.add(helmChartService.getHelmPath()); cmd.add("get"); cmd.add("manifest");
        cmd.add(releaseName);
        cmd.add("--namespace"); cmd.add(namespace);
        if (kubeconfig != null) {
            cmd.add("--kubeconfig"); cmd.add(kubeconfig.toString());
        }

        CommandResult result = runCommand(cmd);
        if (result.exitCode != 0) {
            throw new RuntimeException("helm get manifest failed (" + result.exitCode + "): " + result.stderr + "\n" + result.stdout);
        }
        return result.stdout;
    }

    private java.nio.file.Path createTempKubeconfigFile(String kubeconfigYaml) throws Exception {
        return helmChartService.createTempKubeconfigFile(kubeconfigYaml);
    }

    private void deleteTempKubeconfig(java.nio.file.Path tempKubeconfigPath) {
        if (tempKubeconfigPath == null) {
            return;
        }
        try {
            java.nio.file.Files.deleteIfExists(tempKubeconfigPath);
        } catch (Exception e) {
            log.warn("Failed to delete temporary kubeconfig file: {}", e.getMessage());
        }
    }

    private void runHelmUninstallCli(String releaseName, String namespace, java.nio.file.Path kubeconfig) throws Exception {
        java.util.List<String> cmd = new java.util.ArrayList<>();
        cmd.add(helmChartService.getHelmPath()); cmd.add("uninstall");
        cmd.add(releaseName);
        cmd.add("--namespace"); cmd.add(namespace);
        if (kubeconfig != null) {
            cmd.add("--kubeconfig"); cmd.add(kubeconfig.toString());
        }

        CommandResult result = runCommand(cmd);
        if (result.exitCode != 0) {
            throw new RuntimeException("helm uninstall failed (" + result.exitCode + "): " + result.stderr + "\n" + result.stdout);
        }
        log.info("helm uninstall output: {}", result.stdout);
    }

    private CommandResult runCommand(List<String> cmd) throws Exception {
        Process process = new ProcessBuilder(cmd).start();
        CompletableFuture<String> stdout = CompletableFuture.supplyAsync(() -> readProcessOutput(process.getInputStream()));
        CompletableFuture<String> stderr = CompletableFuture.supplyAsync(() -> readProcessOutput(process.getErrorStream()));

        if (!process.waitFor(HELM_COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new RuntimeException("helm command timed out: " + String.join(" ", cmd.subList(0, Math.min(cmd.size(), 3))));
        }

        return new CommandResult(process.exitValue(), stdout.get(), stderr.get());
    }

    private String readProcessOutput(java.io.InputStream inputStream) {
        try {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read process output", e);
        }
    }

    private record CommandResult(int exitCode, String stdout, String stderr) {
    }

    private static class ManifestDoc {
        private final String kind;
        private final Map<String, Object> content;
        private final String yaml;

        private ManifestDoc(String kind, Map<String, Object> content, String yaml) {
            this.kind = kind;
            this.content = content;
            this.yaml = yaml;
        }

        private String kind() {
            return kind;
        }

        private Map<String, Object> content() {
            return content;
        }

        private String yaml() {
            return yaml;
        }
    }

    private static class WorkloadRef {
        private final String kind;
        private final String name;
        private final String namespace;
        private final Integer manifestReplicas;

        private WorkloadRef(String kind, String name, String namespace, Integer manifestReplicas) {
            this.kind = kind;
            this.name = name;
            this.namespace = namespace;
            this.manifestReplicas = manifestReplicas;
        }

        private String key() {
            return kind + "/" + namespace + "/" + name;
        }

        @Override
        public String toString() {
            return key();
        }
    }

    private static class HpaRef {
        private final String name;
        private final String namespace;
        private final String targetKind;
        private final String targetName;
        private final String yaml;

        private HpaRef(String name, String namespace, String targetKind, String targetName, String yaml) {
            this.name = name;
            this.namespace = namespace;
            this.targetKind = targetKind;
            this.targetName = targetName;
            this.yaml = yaml;
        }

        private boolean targets(WorkloadRef workload) {
            return Objects.equals(targetKind, workload.kind)
                    && Objects.equals(targetName, workload.name)
                    && Objects.equals(namespace, workload.namespace);
        }
    }
}
