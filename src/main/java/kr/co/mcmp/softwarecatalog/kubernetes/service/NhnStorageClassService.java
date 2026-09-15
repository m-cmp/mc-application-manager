package kr.co.mcmp.softwarecatalog.kubernetes.service;

import java.util.*;
import org.springframework.stereotype.Service;
import io.fabric8.kubernetes.api.model.authorization.v1.SelfSubjectAccessReviewBuilder;
import io.fabric8.kubernetes.api.model.storage.StorageClassBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import kr.co.mcmp.ape.cbtumblebug.api.CbtumblebugRestApi;
import kr.co.mcmp.softwarecatalog.application.dto.K8sStorageClassDTO;
import kr.co.mcmp.softwarecatalog.kubernetes.config.KubernetesClientFactory;
import lombok.RequiredArgsConstructor;

@Service @RequiredArgsConstructor
public class NhnStorageClassService {
    static final String DRIVER = "cinder.csi.openstack.org";
    private final KubernetesClientFactory clients;
    private final CbtumblebugRestApi tumblebug;
    public record Capability(boolean supported, boolean driverReady, boolean canCreate, String message) { }
    public record CreateRequest(String name, String diskType) { }

    public Capability capability(String namespace, String clusterName) {
        try {
            var cluster = tumblebug.getK8sClusterByName(namespace, clusterName);
            String provider = cluster == null || cluster.getConnectionConfig() == null ? "" :
                    Objects.toString(cluster.getConnectionConfig().getProviderName(), "");
            if (!"nhn".equalsIgnoreCase(provider))
                return new Capability(false, false, false, "StorageClass creation is available for NHN clusters.");
            try (var client = clients.getClient(namespace, clusterName)) { return inspect(client); }
        } catch (RuntimeException e) { throw StorageOperationException.translate(e); }
    }
    static Capability inspect(KubernetesClient client) {
        if (client.storage().v1().csiDrivers().withName(DRIVER).get() == null)
            return new Capability(true, false, false,
                    "cinder-csi-plugin is not registered. Install the managed add-on in NHN NKS, then refresh StorageClasses.");
        var csiNodes = client.storage().v1().csiNodes().list().getItems();
        boolean registered = !csiNodes.isEmpty() && csiNodes.stream()
                .allMatch(n -> n.getSpec() != null && n.getSpec().getDrivers() != null &&
                        n.getSpec().getDrivers().stream().anyMatch(d -> DRIVER.equals(d.getName())));
        if (!registered) return new Capability(true, false, false,
                "The Cinder CSI driver is still registering on worker nodes. Refresh after the add-on is ready.");
        var review = client.authorization().v1().selfSubjectAccessReview().create(new SelfSubjectAccessReviewBuilder()
                .withNewSpec().withNewResourceAttributes().withGroup("storage.k8s.io")
                .withResource("storageclasses").withVerb("create").endResourceAttributes().endSpec().build());
        boolean allowed = review.getStatus() != null && Boolean.TRUE.equals(review.getStatus().getAllowed());
        return new Capability(true, true, allowed, allowed ? "Cinder CSI is ready. Create an NHN notebook StorageClass (ReadWriteOnce)." :
                "AM needs cluster-level permission to create storageclasses.storage.k8s.io.");
    }
    public K8sStorageClassDTO create(String namespace, String clusterName, CreateRequest request) {
        if (request == null || request.name() == null || request.name().length() > 63 ||
                !request.name().matches("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?"))
            throw new StorageOperationException(400, "INVALID_STORAGE_CLASS", "Use a name of up to 63 lowercase letters, numbers and hyphens.");
        if (!Set.of("General HDD", "General SSD").contains(Objects.toString(request.diskType(), "")))
            throw new StorageOperationException(400, "INVALID_STORAGE_CLASS", "Select General HDD or General SSD.");
        Capability capability = capability(namespace, clusterName);
        if (!capability.canCreate()) throw new StorageOperationException(400, "STORAGE_CLASS_SETUP_REQUIRED", capability.message());
        try (var client = clients.getClient(namespace, clusterName)) {
            // Create only: never replace existing classes or change the cluster default.
            var sc = client.storage().v1().storageClasses().resource(new StorageClassBuilder()
                    .withNewMetadata().withName(request.name()).endMetadata()
                    .withProvisioner(DRIVER).withParameters(Map.of("type", request.diskType()))
                    .withVolumeBindingMode("WaitForFirstConsumer").withReclaimPolicy("Retain").build()).create();
            return KubernetesStorageClassService.toDto(sc);
        } catch (RuntimeException e) { throw StorageOperationException.translate(e); }
    }
}
