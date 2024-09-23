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
public class K8SPodDto {

    private final String apiVersion = "v1";
    private final String kind = "Pod";
    @Valid
    @NotNull
    private PodMetadataDto metadata;
    @Valid
    @NotNull
    private PodSpecDto spec;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PodMetadataDto {
        @NotBlank
        private String name;
        private String namespace = "default";
        private Map<String, String> labels = null;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PodSpecDto {
        @Valid
        @NotNull
        private List<PodContainerDto> containers;
        private String restartPolicy;

        @Getter
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class PodContainerDto {
            @NotBlank
            private String name;
            @NotBlank
            private String image;
            private List<PodEnvDto> env = null;
            private List<PodPortDto> ports = null;
            private PodResourceDto resources;

            @Getter
            @Builder
            @NoArgsConstructor
            @AllArgsConstructor
            public static class PodEnvDto {
                private String name;
                private String value;
            }

            @Getter
            @Builder
            @NoArgsConstructor
            @AllArgsConstructor
            public static class PodPortDto {
                private String name;
                private Integer containerPort;
                private Integer hostPort;
                private String protocol;
            }

            @Getter
            @Builder
            @NoArgsConstructor
            @AllArgsConstructor
            public static class PodResourceDto {
                private Map<String, String> limits = null;
                private Map<String, String> requests = null;
            }
        }
    }
}