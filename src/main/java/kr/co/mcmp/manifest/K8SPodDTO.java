package kr.co.mcmp.manifest;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvVar;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class K8SPodDTO {

    public String podName;
    public String namespace;
    public List<V1Container> containers;
    public Map<String, String> labels;
    public String restartPolicy;


}
