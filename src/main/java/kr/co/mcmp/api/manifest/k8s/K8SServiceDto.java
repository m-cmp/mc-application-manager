package kr.co.mcmp.api.manifest.k8s;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class K8SServiceDto {

    private final String apiVersion = "v1";
    private final String kind = "Service";
    @Valid
    @NotNull
    private ServiceMetadataDto metadata;
    @Valid
    @NotNull
    private ServiceSpecDto spec;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServiceMetadataDto {
        @NotBlank
        private String name;
        private String namespace;
        private Map<String, String> labels = null;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServiceSpecDto {
        @NotNull
        private Map<String, String> selector;
        @Valid
        @NotNull
        private List<ServicePortsDto> ports;
        private String type;

        @Getter
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ServicePortsDto {
            private String protocol;
            @NotNull
            private Integer port;
            @NotNull
            private Integer targetPort;
            private Integer nodePort;
        }
    }
}
