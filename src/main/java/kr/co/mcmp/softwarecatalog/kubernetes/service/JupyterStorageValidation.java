package kr.co.mcmp.softwarecatalog.kubernetes.service;

import java.math.BigDecimal;
import java.util.*;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.storage.StorageClass;
import io.fabric8.kubernetes.client.KubernetesClient;

public final class JupyterStorageValidation {
    private JupyterStorageValidation() { }
    public static String size(Map<String, Object> config) {
        String value = Objects.toString(config == null ? null : config.get("storageSize"), "10Gi");
        try {
            if (Quantity.getAmountInBytes(Quantity.parse(value)).signum() <= 0) throw new IllegalArgumentException();
        } catch (RuntimeException e) { throw invalid("Enter a positive PVC capacity such as 20Gi."); }
        return value;
    }
    // Only enforce documented limits when every fallback type has that minimum.
    public static int minimumSizeGi(StorageClass sc) {
        if (!"diskplugin.csi.alibabacloud.com".equals(sc.getProvisioner())) return 1;
        String type = sc.getParameters() == null ? "" : sc.getParameters().getOrDefault("type", "");
        var types = Arrays.stream(type.split(",")).map(String::trim).toList();
        return !types.isEmpty() && types.stream().allMatch(Set.of("cloud_efficiency", "cloud_ssd")::contains) ? 20 : 1;
    }
    public static StorageClass validate(KubernetesClient client, Map<String, Object> config) {
        String name = Objects.toString(config == null ? null : config.get("storageClass"), "");
        if (name.isBlank()) throw invalid("Select a StorageClass for the Jupyter notebook volume.");
        String mode = Objects.toString(config.get("storageAccessMode"), "ReadWriteOnce");
        if (!"ReadWriteOnce".equals(mode)) throw invalid("Jupyter uses a single replica and requires ReadWriteOnce access mode.");
        String capacity = size(config);
        try {
            StorageClass sc = client.storage().v1().storageClasses().withName(name).get();
            if (sc == null) throw invalid("The selected StorageClass no longer exists. Refresh the list.");
            if (sc.getProvisioner() == null || sc.getProvisioner().isBlank() || "kubernetes.io/no-provisioner".equals(sc.getProvisioner()))
                throw invalid("Select a StorageClass with dynamic provisioning for Jupyter.");
            if (Quantity.getAmountInBytes(Quantity.parse(capacity)).compareTo(
                    BigDecimal.valueOf(minimumSizeGi(sc)).multiply(BigDecimal.valueOf(1073741824L))) < 0)
                throw invalid("This StorageClass requires at least " + minimumSizeGi(sc) + "Gi. Increase the notebook volume capacity.");
            return sc;
        } catch (RuntimeException e) { throw StorageOperationException.translate(e); }
    }
    private static StorageOperationException invalid(String message) {
        return new StorageOperationException(400, "INVALID_JUPYTER_STORAGE", message);
    }
}
