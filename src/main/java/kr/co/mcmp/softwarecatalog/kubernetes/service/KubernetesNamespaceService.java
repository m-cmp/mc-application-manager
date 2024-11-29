package kr.co.mcmp.softwarecatalog.kubernetes.service;

import org.springframework.stereotype.Service;

import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class KubernetesNamespaceService {

    public void ensureNamespaceExists(KubernetesClient client, String namespace) {
        if (client.namespaces().withName(namespace).get() == null) {
            createNamespace(client, namespace);
        }
    }

    private void createNamespace(KubernetesClient client, String namespace) {
        Namespace ns = new NamespaceBuilder()
                .withNewMetadata()
                .withName(namespace)
                .endMetadata()
                .build();

        client.namespaces().createOrReplace(ns);
        log.info("네임스페이스 '{}' 생성됨", namespace);
    }
}