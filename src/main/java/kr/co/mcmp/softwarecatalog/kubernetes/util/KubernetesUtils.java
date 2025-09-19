package kr.co.mcmp.softwarecatalog.kubernetes.util;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;

import java.util.List;
import java.util.stream.Collectors;

public class KubernetesUtils {

    public static String getPodStatus(KubernetesClient client, String namespace, String appName) {
        return client.pods().inNamespace(namespace)
                .withLabel("app", appName)
                .list()
                .getItems()
                .stream()
                .findFirst()
                .map(pod -> pod.getStatus().getPhase())
                .orElse("Unknown");
    }

    public static Integer getServicePort(KubernetesClient client, String namespace, String appName) {
        try {
            var service = client.services().inNamespace(namespace).withName(appName).get();
            if (service == null || service.getSpec() == null || service.getSpec().getPorts() == null) {
                return null;
            }
            
            return service.getSpec()
                    .getPorts()
                    .stream()
                    .findFirst()
                    .map(port -> port.getPort())
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    public static List<String> getPodStatusSummary(List<Pod> pods) {
        return pods.stream()
                   .map(pod -> pod.getMetadata().getName() + ": " + pod.getStatus().getPhase())
                   .collect(Collectors.toList());
    }

    /**
     * Deployment의 replica 개수를 조정합니다.
     */
    public static void scaleDeployment(KubernetesClient client, String namespace, String deploymentName, int replicas) {
        try {
            Deployment deployment = client.apps().deployments()
                    .inNamespace(namespace)
                    .withName(deploymentName)
                    .get();
            
            if (deployment != null) {
                deployment.getSpec().setReplicas(replicas);
                client.apps().deployments()
                        .inNamespace(namespace)
                        .createOrReplace(deployment);
            } else {
                throw new RuntimeException("Deployment not found: " + deploymentName);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to scale deployment: " + deploymentName, e);
        }
    }
}