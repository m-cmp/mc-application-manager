package kr.co.mcmp.manifest;

import io.kubernetes.client.custom.Quantity;
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
    public List<Container> containers;
    public Map<String, String> labels;
    public String restartPolicy;

    @Getter
    @Setter
    public class Container{
        public String image;
        public String name;
        public List<Port> ports;
        public Map<String, Quantity> resource;
        /*limits:
        cpu: 500m
        memory: 1Gi
        requests:
        cpu: 200m
        memory: 256Mi*/
    }

    @Getter
    @Setter
    public class Port{
        public Integer containerPort;
        public Integer hostPort;
        public String name;
        public String protocol;
    }

}
