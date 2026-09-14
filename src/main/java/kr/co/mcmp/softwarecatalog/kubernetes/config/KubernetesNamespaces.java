package kr.co.mcmp.softwarecatalog.kubernetes.config;

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

    private KubernetesNamespaces() {
    }
}
