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

    private String apiVersion;
    private String kind;
    private MetadataDto metadata;
    private Map<String, String> data = null;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    private static class MetadataDto {
        private String name;
        private String namespace;
        private Map<String, Object> labels = null;
    }
}
