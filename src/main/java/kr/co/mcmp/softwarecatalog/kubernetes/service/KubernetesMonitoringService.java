package kr.co.mcmp.softwarecatalog.kubernetes.service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import kr.co.mcmp.softwarecatalog.application.constants.DeploymentType;
import kr.co.mcmp.softwarecatalog.application.model.ApplicationStatus;
import kr.co.mcmp.softwarecatalog.application.model.DeploymentHistory;
import kr.co.mcmp.softwarecatalog.application.repository.ApplicationStatusRepository;
import kr.co.mcmp.softwarecatalog.application.repository.DeploymentHistoryRepository;
import kr.co.mcmp.softwarecatalog.kubernetes.config.KubernetesClientFactory;
import kr.co.mcmp.softwarecatalog.users.Entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class KubernetesMonitoringService {

    private final KubernetesClientFactory clientFactory;
    private final DeploymentHistoryRepository historyRepository;
    private final ApplicationStatusRepository statusRepository;

    @Scheduled(fixedRate = 60000) // 1분마다 실행
    public void monitorKubernetesResources() {
        log.info("Starting Kubernetes resource monitoring");

        List<DeploymentHistory> activeDeployments = getActiveK8sDeployments();

        for (DeploymentHistory deployment : activeDeployments) {
            String namespace = deployment.getNamespace();
            String clusterName = deployment.getClusterName();

            try (KubernetesClient client = clientFactory.getClient(namespace, clusterName)) {

                if (!isMetricsServerInstalled(client)) {
                    installMetricsServer(client);
                }

                updateApplicationStatus(deployment, client);
            } catch (Exception e) {
                log.error("Error monitoring Kubernetes resources for deployment: {}", deployment.getId(), e);
            }
        }
    }

    private List<DeploymentHistory> getActiveK8sDeployments() {
        return historyRepository.findAll().stream()
                .filter(d -> DeploymentType.K8S.equals(d.getDeploymentType()))
                .collect(Collectors.groupingBy(DeploymentHistory::getClusterName))
                .values().stream()
                .flatMap(deployments -> deployments.stream()
                        .collect(Collectors.groupingBy(DeploymentHistory::getCatalog))
                        .values().stream()
                        .map(catalogDeployments -> catalogDeployments.stream()
                                .max(Comparator.comparing(DeploymentHistory::getExecutedAt))
                                .orElse(null)))
                .filter(Objects::nonNull)
                .filter(d -> ("RUN".equalsIgnoreCase(d.getActionType().name())
                        || "INSTALL".equalsIgnoreCase(d.getActionType().name()))
                        && "SUCCESS".equalsIgnoreCase(d.getStatus()))
                .collect(Collectors.toList());
    }

    private void updateApplicationStatus(DeploymentHistory deployment, KubernetesClient client) {
        String namespace = deployment.getNamespace();
        String clusterName = deployment.getClusterName();
        String appName = deployment.getCatalog().getHelmChart().getChartName();
        Long catalogId = deployment.getCatalog().getId();
        User user = deployment.getExecutedBy();

        List<Pod> pods = client.pods().inNamespace(namespace)
                .list()
                .getItems()
                .stream()
                .filter(pod -> pod.getMetadata().getName().startsWith(appName))
                .collect(Collectors.toList());  
                
        log.info("Pod names in namespace: {}", 
                client.pods().inNamespace(namespace).list().getItems()
                    .stream()
                    .map(pod -> pod.getMetadata().getName())
                    .collect(Collectors.joining(", "))
            );
            

        ApplicationStatus status = statusRepository.findTopByCatalogIdOrderByCheckedAtDesc(catalogId)
                .orElse(new ApplicationStatus());

        long runningPods = pods.stream()
                .filter(pod -> "Running".equalsIgnoreCase(pod.getStatus().getPhase()))
                .count();
        status.setPodStatus(runningPods + "/" + pods.size() + " running");

        Map<String, Object> resourceUsage = getResourceUsagePercentage(client, namespace, appName);
        status.setCpuUsage((Double) resourceUsage.get("cpuPercentage"));
        status.setMemoryUsage((Double) resourceUsage.get("memoryPercentage"));
        status.setStatus((String) resourceUsage.get("status"));
        status.setServicePort((Integer) resourceUsage.get("port"));
        status.setCheckedAt(LocalDateTime.now());
        status.setClusterName(clusterName);
        status.setNamespace(namespace);
        status.setExecutedBy(user);
        status.setDeploymentType(DeploymentType.K8S);

        statusRepository.save(status);
    }

    private void checkMetricsServerStatus(KubernetesClient client) {
        Deployment metricsServer = client.apps().deployments()
                .inNamespace("kube-system")
                .withName("metrics-server")
                .get();

        if (metricsServer == null) {
            log.error("Metrics Server not found. Please install it.");
        } else {
            Integer readyReplicas = metricsServer.getStatus().getReadyReplicas();
            if (readyReplicas == null || readyReplicas == 0) {
                log.error("Metrics Server is not ready. Check its status and logs.");
            } else {
                log.info("Metrics Server is running with {} ready replicas", readyReplicas);
            }
        }
    }

    private Map<String, Object> getResourceUsagePercentage(KubernetesClient client, String namespace, String appName) {
        ResourceDefinitionContext context = new ResourceDefinitionContext.Builder()
                .withGroup("metrics.k8s.io")
                .withVersion("v1beta1")
                .withKind("PodMetrics")
                .withPlural("pods")
                .withNamespaced(true)
                .build();

        // log.info("appName : " + appName);
        List<GenericKubernetesResource> podMetrics = client.genericKubernetesResources(context)
                .inNamespace(namespace)
                // .withLabel("app", appName)
                .list()
                .getItems();

        log.info("Found {} pod metrics in namespace {}", podMetrics.size(), namespace);

        if (podMetrics.isEmpty()) {
            log.warn("No pod metrics found. Checking if metrics-server is running...");
            checkMetricsServerStatus(client);
            return Map.of("cpu", 0.0, "memory", 0.0, "status", "UNKNOWN");
        }

        double totalCpuUsage = 0.0;
        double totalMemoryUsage = 0.0;
        int podCount = 0;

        for (GenericKubernetesResource podMetric : podMetrics) {
            String podName = podMetric.getMetadata().getName();
            if (podName.startsWith(appName.toLowerCase())) {
                try {

                    List<Map<String, Object>> containers = (List<Map<String, Object>>) podMetric
                            .getAdditionalProperties().get("containers");
                    if (containers != null) {
                        for (Map<String, Object> container : containers) {
                            Map<String, Object> usage = (Map<String, Object>) container.get("usage");
                            if (usage != null) {
                                String cpuUsage = (String) usage.get("cpu");
                                String memoryUsage = (String) usage.get("memory");
                                totalCpuUsage += parseCpuUsage(cpuUsage);
                                totalMemoryUsage += parseMemoryUsage(memoryUsage);
                            }
                        }
                        podCount++;
                    }
                } catch (Exception e) {
                    log.error("Error processing pod metric: {}", e.getMessage(), e);
                }
            }
        }

        double totalCpuCapacity = getTotalCpuCapacity(client);
        double totalMemoryCapacity = getTotalMemoryCapacity(client);

        double cpuUsagePercentage = (totalCpuUsage / totalCpuCapacity) * 100;
        double memoryUsagePercentage = (totalMemoryUsage / (totalMemoryCapacity * 1024)) * 100;

        // 소수점 2자리까지 반올림
        Double roundedCpuUsage = Math.round(cpuUsagePercentage * 100.0) / 100.0;
        Double roundedMemoryUsage = Math.round(memoryUsagePercentage * 100.0) / 100.0;

        // 서비스 포트 정보 수집
        List<Integer> ports = new ArrayList<>();
        client.services().inNamespace(namespace).list().getItems().stream()
                .filter(service -> service.getMetadata().getName().startsWith(appName.toLowerCase()))
                .forEach(service -> {
                    service.getSpec().getPorts().forEach(servicePort -> {
                        if (servicePort.getNodePort() != null) {
                            ports.add(servicePort.getNodePort());
                        }
                    });
                });
        Integer primaryPort = ports.isEmpty() ? null : ports.get(0);

        Map<String, Object> result = new HashMap<>();
        result.put("cpuPercentage", roundedCpuUsage != null ? roundedCpuUsage : 0.0);
        result.put("memoryPercentage", roundedMemoryUsage != null ? roundedMemoryUsage : 0.0);
        result.put("status", podCount > 0 ? "RUNNING" : "STOPPED");
        result.put("port", primaryPort);

        return result;
    }

    private double getTotalCpuCapacity(KubernetesClient client) {
        return client.nodes().list().getItems().stream()
                .mapToDouble(node -> parseCpuUsage(node.getStatus().getCapacity().get("cpu").getAmount()))
                .sum();
    }

    private double getTotalMemoryCapacity(KubernetesClient client) {
        return client.nodes().list().getItems().stream()
                .mapToDouble(node -> parseMemoryUsage(node.getStatus().getCapacity().get("memory").getAmount()))
                .sum();
    }

    private double parseCpuUsage(String cpuUsage) {
        if (cpuUsage.endsWith("n")) {
            return Double.parseDouble(cpuUsage.substring(0, cpuUsage.length() - 1)) / 1_000_000_000.0;
        } else if (cpuUsage.endsWith("u")) {
            return Double.parseDouble(cpuUsage.substring(0, cpuUsage.length() - 1)) / 1_000_000.0;
        } else if (cpuUsage.endsWith("m")) {
            return Double.parseDouble(cpuUsage.substring(0, cpuUsage.length() - 1)) / 1_000.0;
        } else {
            return Double.parseDouble(cpuUsage);
        }
    }

    private double parseMemoryUsage(String memoryUsage) {
        if (memoryUsage.endsWith("Ki")) {
            return Double.parseDouble(memoryUsage.substring(0, memoryUsage.length() - 2)) / (1024.0 * 1024.0);
        } else if (memoryUsage.endsWith("Mi")) {
            return Double.parseDouble(memoryUsage.substring(0, memoryUsage.length() - 2)) / 1024.0;
        } else if (memoryUsage.endsWith("Gi")) {
            return Double.parseDouble(memoryUsage.substring(0, memoryUsage.length() - 2));
        } else {
            return Double.parseDouble(memoryUsage) / (1024.0 * 1024.0 * 1024.0);
        }
    }

    private boolean isMetricsServerInstalled(KubernetesClient client) {
        return client.apps().deployments().inNamespace("kube-system").withName("metrics-server").get() != null;
    }

    private void installMetricsServer(KubernetesClient client) throws IOException {
        log.info("Metrics Server not found. Installing...");

        String metricsServerYaml = "";
        try {
            metricsServerYaml = downloadMetricsServerYaml();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        client.resourceList(metricsServerYaml).createOrReplace();

        log.info("Metrics Server installation completed");
        try {
            waitForMetricsServerReady(client);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private String downloadMetricsServerYaml() throws IOException, InterruptedException {
        String url = "https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml";
        Process process = Runtime.getRuntime().exec("curl -s " + url);
        process.waitFor();
        return new String(process.getInputStream().readAllBytes());
    }

    private void waitForMetricsServerReady(KubernetesClient client) throws InterruptedException {
        log.info("Waiting for Metrics Server to be ready...");
        int maxAttempts = 30;
        int attempt = 0;
        while (attempt < maxAttempts) {
            Deployment metricsServer = client.apps().deployments().inNamespace("kube-system").withName("metrics-server")
                    .get();
            if (metricsServer != null && metricsServer.getStatus().getReadyReplicas() != null
                    && metricsServer.getStatus().getReadyReplicas() > 0) {
                log.info("Metrics Server is ready");
                return;
            }
            Thread.sleep(10000); // 10초 대기
            attempt++;
        }
        log.warn("Metrics Server did not become ready within the expected time");
    }

}