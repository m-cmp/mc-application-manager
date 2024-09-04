package kr.co.mcmp.api.manifest.k8s;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class K8SPodDto {

    private final String apiVersion = "v1";
    private final String kind = "Pod";
    private MetadataDto metadata;
    private SpecDto spec;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetadataDto {
        private String name;
        private String namespace;
        private Map<String, Object> labels = null;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SpecDto {
        private List<ContainerDto> containers = null;
        private String restartPolicy;

        @Getter
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ContainerDto {
            private String name;
            private String image;
            private List<PortDto> ports = null;
            private ResourceDto resources;

            @Getter
            @Builder
            @NoArgsConstructor
            @AllArgsConstructor
            public static class PortDto {
                private String name;
                private Integer containerPort;
                private Integer hostPort;
                private String protocol;
            }

            @Getter
            @Builder
            @NoArgsConstructor
            @AllArgsConstructor
            public static class ResourceDto {
                private Map<String, Object> limits = null;
                private Map<String, Object> requests = null;
            }
        }
    }
}