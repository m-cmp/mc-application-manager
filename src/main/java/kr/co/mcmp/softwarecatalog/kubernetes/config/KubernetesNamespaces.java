package kr.co.mcmp.softwarecatalog.kubernetes.config;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;

/**
 * Kubernetes namespaces used by Application Manager.
 *
 * <p>The namespace received from a deployment request identifies a CB-Tumblebug
 * project. It must not be used as a Kubernetes namespace. The current API does
 * not expose a separate Kubernetes namespace, so application workloads are
 * intentionally installed in {@value #APPLICATION_WORKLOAD}.</p>
 */
public final class KubernetesNamespaces {

    public static final String APPLICATION_WORKLOAD = "default";

    /** New Jupyter installs use default; retain management of older project-namespace installs. */
    public static String jupyterWorkloadNamespace(KubernetesClient client, String projectNamespace, String releaseName) {
        if (APPLICATION_WORKLOAD.equals(projectNamespace)) return APPLICATION_WORKLOAD;
        if (client.apps().deployments().inNamespace(APPLICATION_WORKLOAD).withName(releaseName).get() != null)
            return APPLICATION_WORKLOAD;
        if (owned(client.apps().deployments().inNamespace(projectNamespace).withName(releaseName).get(), releaseName)
                || owned(client.persistentVolumeClaims().inNamespace(projectNamespace).withName(releaseName).get(), releaseName))
            return projectNamespace;
        return APPLICATION_WORKLOAD;
    }

    private static boolean owned(HasMetadata resource, String releaseName) {
        return resource != null && resource.getMetadata() != null && resource.getMetadata().getLabels() != null
                && releaseName.equals(resource.getMetadata().getLabels().get("mcmp.io/jupyter-deployment"));
    }

    private KubernetesNamespaces() {
    }
}
