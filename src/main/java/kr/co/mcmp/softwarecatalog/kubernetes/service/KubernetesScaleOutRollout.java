package kr.co.mcmp.softwarecatalog.kubernetes.service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import org.apache.commons.lang3.StringUtils;

import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import kr.co.mcmp.ape.cbtumblebug.dto.K8sClusterDto;
import kr.co.mcmp.softwarecatalog.application.model.ScalingEvent;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class KubernetesScaleOutRollout {

    private static final String HELM_INSTANCE = "app.kubernetes.io/instance";
    private static final String HELM_RELEASE = "meta.helm.sh/release-name";
    private static final String LEGACY_NODE_SELECTOR = "autoscale-group";

    private KubernetesScaleOutRollout() {
    }

    static Deployment findDeployment(KubernetesClient client, String namespace, String releaseName, String catalogName) {
        var deployments = client.apps().deployments().inNamespace(namespace);
        if (StringUtils.isNotBlank(releaseName)) {
            List<Deployment> matches = deployments.list().getItems().stream()
                    .filter(deployment -> releaseName.equals(deployment.getMetadata().getName())
                            || releaseName.equals(deployment.getMetadata().getLabels().get(HELM_INSTANCE))
                            || releaseName.equals(deployment.getMetadata().getAnnotations().get(HELM_RELEASE)))
                    .toList();
            return uniqueDeployment(matches, releaseName);
        }

        // Older history records may not have a Helm release name.
        String legacyName = catalogName.toLowerCase(Locale.ROOT).replaceAll("\\s+", "-");
        List<Deployment> matches = deployments.list().getItems().stream()
                .filter(deployment -> deployment.getMetadata().getName().equals(legacyName)
                        || deployment.getMetadata().getName().startsWith(legacyName + "-"))
                .toList();
        return uniqueDeployment(matches, legacyName);
    }

    private static Deployment uniqueDeployment(List<Deployment> matches, String expectedName) {
        if (matches.size() == 1) {
            return matches.get(0);
        }
        return matches.stream()
                .filter(deployment -> expectedName.equals(deployment.getMetadata().getName()))
                .findFirst().orElse(null);
    }

    static boolean reconcile(KubernetesClient client, String namespace, Deployment deployment, ScalingEvent event,
            Supplier<K8sClusterDto> clusterInfo) {
        String label = placementLabel(event);
        String eventId = event.getId().toString();
        List<Node> nodes = client.nodes().list().getItems();
        List<Node> targetNodes = nodes.stream()
                .filter(node -> eventId.equals(node.getMetadata().getLabels().get(label)))
                .toList();

        if (eventId.equals(nodeSelector(deployment).get(label))) {
            return targetNodes.size() == 1 && isReadyWorker(targetNodes.get(0))
                    && isRolloutReady(client, namespace, deployment, label, eventId, targetNodes.get(0));
        }

        Node target = targetNodes.size() == 1 && isReadyWorker(targetNodes.get(0)) ? targetNodes.get(0) : null;
        if (targetNodes.isEmpty()) {
            target = findNewWorker(nodes, event, clusterInfo.get());
        }
        if (target == null || replicas(deployment) == 0) {
            return false;
        }

        String nodeName = target.getMetadata().getName();
        client.nodes().withName(nodeName).edit(node ->
                new NodeBuilder(node).editMetadata().addToLabels(label, eventId).endMetadata().build());

        client.apps().deployments().inNamespace(namespace).withName(deployment.getMetadata().getName())
                .edit(current -> {
                    if (!Objects.equals(current.getMetadata().getUid(), deployment.getMetadata().getUid())) {
                        throw new IllegalStateException("Deployment was replaced during scale-out");
                    }
                    var template = new DeploymentBuilder(current).editSpec().editTemplate();
                    template.editMetadata().addToLabels(label, eventId).endMetadata();
                    var podSpec = template.editSpec().addToNodeSelector(label, eventId);
                    String legacySelector = nodeSelector(current).get(LEGACY_NODE_SELECTOR);
                    if ("scaled".equals(legacySelector) || event.getNodeGroupName().equals(legacySelector)) {
                        podSpec.removeFromNodeSelector(LEGACY_NODE_SELECTOR);
                    }
                    return podSpec.endSpec().endTemplate().endSpec().build();
                });

        log.info("Started scale-out rollout: event={}, deployment={}, targetNode={}",
                event.getId(), deployment.getMetadata().getName(), nodeName);
        return false;
    }

    private static Node findNewWorker(List<Node> nodes, ScalingEvent event, K8sClusterDto clusterInfo) {
        if (clusterInfo == null) {
            return null;
        }

        List<K8sClusterDto.NodeGroup> groups = clusterInfo.getEffectiveNodeGroups();
        List<K8sClusterDto.IID> groupNodes = groups.stream()
                .filter(group -> event.getNodeGroupName().equals(group.getEffectiveName()))
                .findFirst().map(K8sClusterDto.NodeGroup::getEffectiveNodes).orElse(List.of());
        if (groupNodes.size() < event.getNewNodeCount()) {
            return null;
        }

        Instant triggeredAt = event.getTriggeredAt().atZone(ZoneId.systemDefault()).toInstant()
                .truncatedTo(ChronoUnit.SECONDS);
        List<Node> newWorkers = nodes.stream()
                .filter(KubernetesScaleOutRollout::isReadyWorker)
                .filter(node -> node.getMetadata().getCreationTimestamp() != null)
                .filter(node -> !creationTime(node).isBefore(triggeredAt))
                .toList();

        Node target = newWorkers.stream()
                .filter(node -> groupNodes.stream().anyMatch(member -> matchesGroupMember(node, member)))
                .max(Comparator.comparing(KubernetesScaleOutRollout::creationTime)
                        .thenComparing(node -> node.getMetadata().getName()))
                .orElse(null);
        if (target == null && groups.size() == 1 && newWorkers.size() == 1) {
            // Some providers report IDs that do not appear in the Kubernetes Node object.
            return newWorkers.get(0);
        }
        return target;
    }

    private static boolean matchesGroupMember(Node node, K8sClusterDto.IID member) {
        String nodeName = node.getMetadata().getName();
        String providerId = node.getSpec().getProviderID();
        String systemId = member.getSystemId();
        return nodeName.equals(member.getNameId()) || nodeName.equals(systemId)
                || (StringUtils.isNotBlank(providerId) && StringUtils.isNotBlank(systemId)
                        && (providerId.equals(systemId) || providerId.endsWith("/" + systemId)));
    }

    private static Instant creationTime(Node node) {
        return Instant.parse(node.getMetadata().getCreationTimestamp());
    }

    private static boolean isReadyWorker(Node node) {
        Map<String, String> labels = node.getMetadata().getLabels();
        return node.getMetadata().getDeletionTimestamp() == null
                && node.getSpec() != null && !Boolean.TRUE.equals(node.getSpec().getUnschedulable())
                && !labels.containsKey("node-role.kubernetes.io/control-plane")
                && !labels.containsKey("node-role.kubernetes.io/master")
                && node.getStatus() != null && node.getStatus().getConditions().stream()
                        .anyMatch(condition -> "Ready".equals(condition.getType()) && "True".equals(condition.getStatus()));
    }

    private static boolean isRolloutReady(KubernetesClient client, String namespace, Deployment deployment,
            String label, String eventId, Node target) {
        int replicas = replicas(deployment);
        var status = deployment.getStatus();
        if (replicas < 1 || status == null || status.getObservedGeneration() == null
                || status.getObservedGeneration() < deployment.getMetadata().getGeneration()
                || !Objects.equals(status.getReplicas(), replicas)
                || !Objects.equals(status.getUpdatedReplicas(), replicas)) {
            return false;
        }

        List<Pod> pods = client.pods().inNamespace(namespace).withLabel(label, eventId).list().getItems();
        String targetName = target.getMetadata().getName();
        return pods.size() == replicas && pods.stream().allMatch(pod -> isReadyOnNode(pod, targetName));
    }

    private static boolean isReadyOnNode(Pod pod, String nodeName) {
        return pod.getMetadata().getDeletionTimestamp() == null
                && pod.getSpec() != null && nodeName.equals(pod.getSpec().getNodeName())
                && pod.getStatus() != null && "Running".equals(pod.getStatus().getPhase())
                && pod.getStatus().getConditions().stream()
                        .anyMatch(condition -> "Ready".equals(condition.getType()) && "True".equals(condition.getStatus()));
    }

    static void releasePlacement(KubernetesClient client, String namespace, Deployment deployment, ScalingEvent event) {
        String label = placementLabel(event);
        String eventId = event.getId().toString();
        if (deployment == null || !eventId.equals(nodeSelector(deployment).get(label))) {
            return;
        }
        client.apps().deployments().inNamespace(namespace).withName(deployment.getMetadata().getName())
                .edit(current -> {
                    if (!eventId.equals(nodeSelector(current).get(label))) {
                        return current;
                    }
                    var template = new DeploymentBuilder(current).editSpec().editTemplate();
                    template.editMetadata().removeFromLabels(label).endMetadata();
                    return template.editSpec().removeFromNodeSelector(label).endSpec().endTemplate().endSpec().build();
                });
    }

    private static String placementLabel(ScalingEvent event) {
        return "mcmp.io/scale-out-" + event.getDeploymentId();
    }

    private static Map<String, String> nodeSelector(Deployment deployment) {
        PodSpec spec = deployment.getSpec().getTemplate().getSpec();
        return spec.getNodeSelector() == null ? Map.of() : spec.getNodeSelector();
    }

    private static int replicas(Deployment deployment) {
        return Objects.requireNonNullElse(deployment.getSpec().getReplicas(), 1);
    }
}
