package kr.co.mcmp.api.manifest.k8s;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class K8SConfigMapDto {

    private final String apiVersion = "v1";
    private final String kind = "ConfigMap";
    private MetadataDto metadata;
    private Map<String, String> data = null;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetadataDto {
        private String name;
        private String namespace;
        private Map<String, Object> labels = null;
    }
}
