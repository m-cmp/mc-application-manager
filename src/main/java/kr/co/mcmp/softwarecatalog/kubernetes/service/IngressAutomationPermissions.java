package kr.co.mcmp.softwarecatalog.kubernetes.service;

import io.fabric8.kubernetes.api.model.authorization.v1.SelfSubjectAccessReviewBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;

final class IngressAutomationPermissions {
    private IngressAutomationPermissions() { }
    /** Authorization review only: Kubernetes does not persist these review objects. */
    static void require(KubernetesClient client, String group, String resource, String namespace, String verb) {
        try {
            var review = new SelfSubjectAccessReviewBuilder().withNewSpec().withNewResourceAttributes()
                    .withGroup(group).withResource(resource).withNamespace(namespace).withVerb(verb)
                    .endResourceAttributes().endSpec().build();
            var result = client.authorization().v1().selfSubjectAccessReview().create(review);
            if (result.getStatus() == null || !Boolean.TRUE.equals(result.getStatus().getAllowed()))
                throw new IllegalArgumentException("Kubernetes permission required for automatic Ingress setup: " + verb + " " + resource
                        + (namespace == null ? " (cluster scope)." : " in namespace " + namespace + "."));
        } catch (IllegalArgumentException e) { throw e; }
        catch (RuntimeException e) { throw new IllegalArgumentException("Cannot verify Kubernetes permissions for automatic Ingress setup."); }
    }
    static void lock(KubernetesClient client) {
        for (String verb : java.util.List.of("get","create","update","delete")) require(client,"","configmaps","kube-system",verb);
    }
}
