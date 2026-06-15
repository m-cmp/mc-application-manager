package kr.co.mcmp.softwarecatalog.kubernetes.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import io.fabric8.kubernetes.api.model.storage.StorageClass;
import io.fabric8.kubernetes.client.KubernetesClient;
import kr.co.mcmp.softwarecatalog.application.dto.K8sStorageClassDTO;
import kr.co.mcmp.softwarecatalog.kubernetes.config.KubernetesClientFactory;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class KubernetesStorageClassService {

    private static final String DEFAULT_CLASS_ANNOTATION = "storageclass.kubernetes.io/is-default-class";
    private static final String BETA_DEFAULT_CLASS_ANNOTATION = "storageclass.beta.kubernetes.io/is-default-class";

    private final KubernetesClientFactory kubernetesClientFactory;

    public List<K8sStorageClassDTO> getStorageClasses(String namespace, String clusterName) {
        try (KubernetesClient client = kubernetesClientFactory.getClient(namespace, clusterName)) {
            return client.storage().v1().storageClasses().list().getItems().stream()
                    .map(this::toDto)
                    .sorted(Comparator
                            .comparing(K8sStorageClassDTO::getDefaultClass, Comparator.nullsLast(Comparator.reverseOrder()))
                            .thenComparing(K8sStorageClassDTO::getName, Comparator.nullsLast(String::compareToIgnoreCase)))
                    .toList();
        }
    }

    public boolean exists(String namespace, String clusterName, String storageClassName) {
        if (storageClassName == null || storageClassName.isBlank()) {
            return false;
        }
        return getStorageClasses(namespace, clusterName).stream()
                .anyMatch(storageClass -> storageClassName.equals(storageClass.getName()));
    }

    private K8sStorageClassDTO toDto(StorageClass storageClass) {
        return K8sStorageClassDTO.builder()
                .name(storageClass.getMetadata() != null ? storageClass.getMetadata().getName() : null)
                .provisioner(storageClass.getProvisioner())
                .defaultClass(isDefault(storageClass))
                .reclaimPolicy(storageClass.getReclaimPolicy())
                .volumeBindingMode(storageClass.getVolumeBindingMode())
                .build();
    }

    private boolean isDefault(StorageClass storageClass) {
        if (storageClass.getMetadata() == null) {
            return false;
        }
        Map<String, String> annotations = storageClass.getMetadata().getAnnotations();
        if (annotations == null || annotations.isEmpty()) {
            return false;
        }
        return Boolean.parseBoolean(annotations.get(DEFAULT_CLASS_ANNOTATION))
                || Boolean.parseBoolean(annotations.get(BETA_DEFAULT_CLASS_ANNOTATION));
    }
}
