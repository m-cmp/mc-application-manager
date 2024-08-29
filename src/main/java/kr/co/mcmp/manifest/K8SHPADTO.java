package kr.co.mcmp.manifest;

import com.google.gson.annotations.SerializedName;
import io.kubernetes.client.openapi.models.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class K8SHPADTO {

    public String hpaName;
    public String namespace;
    public List<V1Container> containers;
    public Map<String, String> labels;

    public TargetRef target;

    public int minReplicas;
    public int maxReplicas;

    public Metric metric;

    @Getter
    @Setter
    public class TargetRef{
        public String kind;
        public String name;
    }

    @Getter
    @Setter
    public class Metric{
        public String type;
        public String resourceName;
        public String targetType;
        public int targetAverageUtilization;
    }

}
