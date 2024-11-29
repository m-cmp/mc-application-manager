package kr.co.mcmp.softwarecatalog.kubernetes.util;

import io.fabric8.kubernetes.api.model.Pod;
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
        return client.services().inNamespace(namespace)
                .withName(appName)
                .get()
                .getSpec()
                .getPorts()
                .stream()
                .findFirst()
                .map(port -> port.getPort())
                .orElse(null);
    }

    public static List<String> getPodStatusSummary(List<Pod> pods) {
        return pods.stream()
                   .map(pod -> pod.getMetadata().getName() + ": " + pod.getStatus().getPhase())
                   .collect(Collectors.toList());
    }
}