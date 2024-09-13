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
public class K8SServiceDto {

    private final String apiVersion = "v1";
    private final String kind = "Service";
    private ServiceMetadataDto metadata;
    private ServiceSpecDto spec;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServiceMetadataDto {
        private String name;
        private String namespace;
        private Map<String, String> labels = null;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServiceSpecDto {
        private Map<String, String> selector = null;
        private List<ServicePortsDto> ports = null;
        private String type;

        @Getter
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ServicePortsDto {
            private String protocol;
            private Integer port;
            private Integer targetPort;
            private Integer nodePort;
        }
    }
}
