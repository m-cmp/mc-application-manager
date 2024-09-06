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
public class K8SDeploymentsDto {

    private final String apiVersion = "apps/v1";
    private final String kind = "Deployment";
    private MetadataDto metadata;
    private SpecDto spec;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetadataDto {
        private String name;
        private String namespace;
        private Map<String, String> labels = null;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SpecDto {
        private Integer replicas;
        private SelectorDto selector;
        private TemplateDto template;

        @Getter
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class SelectorDto {
            private Map<String, String> matchLabels = null;
        }

        @Getter
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class TemplateDto {
            private MetadataDto metadata;
            private PodSpecDto spec;

            @Getter
            @Builder
            @NoArgsConstructor
            @AllArgsConstructor
            public static class PodSpecDto {
                private List<ContainerDto> containers = null;

                @Getter
                @Builder
                @NoArgsConstructor
                @AllArgsConstructor
                public static class ContainerDto {
                    private String name;
                    private String image;
                    private List<EnvDto> env = null;
                    private List<PortDto> ports = null;

                    @Getter
                    @Builder
                    @NoArgsConstructor
                    @AllArgsConstructor
                    public static class EnvDto {
                        private String name;
                        private String value;
                    }

                    @Getter
                    @Builder
                    @NoArgsConstructor
                    @AllArgsConstructor
                    public static class PortDto {
                        private Integer containerPort;
                    }
                }
            }
        }
    }
}
