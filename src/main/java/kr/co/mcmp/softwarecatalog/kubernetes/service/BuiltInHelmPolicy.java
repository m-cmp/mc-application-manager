package kr.co.mcmp.softwarecatalog.kubernetes.service;

import java.math.BigDecimal;
import java.util.*;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.client.KubernetesClient;
import kr.co.mcmp.softwarecatalog.application.dto.*;
import kr.co.mcmp.softwarecatalog.application.model.HelmChart;

/** Applies only to the exact bundled chart identity; custom charts and VM installs are unchanged. */
final class BuiltInHelmPolicy {
    private BuiltInHelmPolicy() { }
    static void validate(HelmChart chart, DeploymentConfigDTO config) {
        BuiltInHelmCharts.app(chart).filter(BuiltInHelmCharts.App::persistent).ifPresent(app -> {
            if (config.isIngressEnabled()) throw new IllegalArgumentException(app.title()
                    + " uses TCP, not HTTP Ingress. Use its ClusterIP Service or an authenticated port-forward.");
            if (config.isHpaEnabled() || !Integer.valueOf(1).equals(config.getMinReplicas()))
                throw new IllegalArgumentException(app.title() + " supports one persistent instance; disable HPA and use one replica.");
        });
    }
    static void validateStorage(HelmChart chart, KubernetesClient client, DeploymentRequest request) {
        if (BuiltInHelmCharts.app(chart).filter(BuiltInHelmCharts.App::persistent).isEmpty()) return;
        if (request != null && Boolean.TRUE.equals(request.getWorkloadRebalancingEnabled()))
            throw new IllegalArgumentException("Single-instance persistent applications do not support workload rebalancing.");
        Map<String, Object> extra = extra(request);
        String name = Objects.toString(extra.get("storageClass"), "").trim();
        if (name.isEmpty()) throw new IllegalArgumentException("Select a StorageClass for this application's data volume.");
        String mode = Objects.toString(extra.get("storageAccessMode"), "ReadWriteOnce");
        if (!"ReadWriteOnce".equals(mode)) throw new IllegalArgumentException("Built-in data volumes require ReadWriteOnce.");
        var storage = client.storage().v1().storageClasses().withName(name).get();
        if (storage == null || storage.getProvisioner() == null || storage.getProvisioner().isBlank()
                || "kubernetes.io/no-provisioner".equals(storage.getProvisioner()))
            throw new IllegalArgumentException("Select an existing StorageClass with dynamic provisioning.");
        int minimum = JupyterStorageValidation.minimumSizeGi(storage);
        if (Quantity.getAmountInBytes(Quantity.parse(size(extra))).compareTo(
                BigDecimal.valueOf(minimum).multiply(BigDecimal.valueOf(1073741824L))) < 0)
            throw new IllegalArgumentException("This StorageClass requires at least " + minimum + "Gi.");
    }
    static String size(Map<String, Object> extra) {
        String size = Objects.toString(extra.get("storageSize"), "10Gi");
        // A deliberately narrow UI/API contract avoids Helm --set injection and accidental byte-sized disks.
        if (!size.matches("[1-9][0-9]{0,3}Gi"))
            throw new IllegalArgumentException("Enter a whole-number data volume capacity from 1Gi to 9999Gi.");
        return size;
    }
    static void configure(HelmChart chart, DeploymentRequest request, Map<String, String> cli, Map<String, Object> yaml) {
        BuiltInHelmCharts.app(chart).ifPresent(app -> {
            cli.put("securityContext.runAsNonRoot", "true");
            if (app.persistent()) {
                cli.put("persistence.enabled", "true");
                // Typed YAML preserves literal StorageClass names instead of interpreting Helm --set syntax.
                yaml.put("persistence", Map.of("enabled", true, "storageClass",
                        Objects.toString(extra(request).get("storageClass"), "").trim(),
                        "size", size(extra(request)), "retain", true));
            }
        });
    }
    private static Map<String, Object> extra(DeploymentRequest request) {
        return request == null || request.getAdditionalConfig() == null ? Map.of() : request.getAdditionalConfig();
    }
}
