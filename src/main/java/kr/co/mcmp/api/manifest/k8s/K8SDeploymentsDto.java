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

    private String apiVersion;
    private String kind;
    private MetadataDto metadata;
    private SpecDto spec;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    private static class MetadataDto {
        private String name;
        private String namespace;
        private Map<String, Object> labels = null;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    private static class SpecDto {
        private Integer replicas;
        private SelectorDto selector;
        private TemplateDto template;

        @Getter
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        private static class SelectorDto {
            private Map<String, Object> matchLabels = null;
        }

        @Getter
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        private static class TemplateDto {
            private MetadataDto metadata;
            private PodSpecDto spec;

            @Getter
            @Builder
            @NoArgsConstructor
            @AllArgsConstructor
            private static class PodSpecDto {
                private List<ContainerDto> containers = null;

                @Getter
                @Builder
                @NoArgsConstructor
                @AllArgsConstructor
                private static class ContainerDto {
                    private String name;
                    private String image;
                    private List<PortDto> ports = null;

                    @Getter
                    @Builder
                    @NoArgsConstructor
                    @AllArgsConstructor
                    private static class PortDto {
                        private Integer containerPort;
                    }
                }
            }
        }
    }
}
