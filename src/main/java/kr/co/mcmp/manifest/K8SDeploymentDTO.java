package kr.co.mcmp.manifest;

import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapEnvSource;
import io.kubernetes.client.openapi.models.V1Secret;
import kr.co.mcmp.manifest.k8s.K8SVolume;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class K8SDeploymentDTO {

    public String name;
    public String namespace;
    public Integer replicas;
    public Map<String, String> labels;
    public K8SPodDTO podDto;

    public V1ConfigMapEnvSource configmap;
    public V1Secret secret;

    private List<K8SVolume> volumes;

}
