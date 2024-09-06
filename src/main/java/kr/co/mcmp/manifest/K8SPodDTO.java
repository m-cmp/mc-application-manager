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
    //public List<V1Container> containers;

    public List<Container> containers;

    @Getter
    @Setter
    public class Container{
        public String containerImage;
        public String containerName;
        public List<Port> ports;
        //public Resource resource;

    }

    @Getter
    @Setter
    public class Port{
        public int containerPort;
        public int hostPort;
        public String name;
        public String protocol;
    }

    @Getter
    @Setter
    public class Resource{
        public String limitCpu;
        public String LimitMemory;
        public String reqCpu;
        public String reqMemory;
    }



    public Map<String, String> labels;
    public String restartPolicy;


}
