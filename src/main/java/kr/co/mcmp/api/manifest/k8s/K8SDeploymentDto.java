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
public class K8SDeploymentDto {

    private final String apiVersion = "apps/v1";
    private final String kind = "Deployment";
    private DeploymentMetadataDto metadata;
    private DeploymentSpecDto spec;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeploymentMetadataDto {
        private String name;
        private String namespace;
        private Map<String, String> labels = null;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeploymentSpecDto {
        private Integer replicas;
        private DeploymentSelectorDto selector;
        private DeploymentTemplateDto template;

        @Getter
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class DeploymentSelectorDto {
            private Map<String, String> matchLabels = null;
        }

        @Getter
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class DeploymentTemplateDto {
            private DeploymentMetadataDto metadata;
            private DeploymentPodSpecDto spec;

            @Getter
            @Builder
            @NoArgsConstructor
            @AllArgsConstructor
            public static class DeploymentPodSpecDto {
                private List<DeploymentPodContainerDto> containers = null;

                @Getter
                @Builder
                @NoArgsConstructor
                @AllArgsConstructor
                public static class DeploymentPodContainerDto {
                    private String name;
                    private String image;
                    private List<DeploymentPodEnvDto> env = null;
                    private List<DeploymentPodPortDto> ports = null;

                    @Getter
                    @Builder
                    @NoArgsConstructor
                    @AllArgsConstructor
                    public static class DeploymentPodEnvDto {
                        private String name;
                        private String value;
                    }

                    @Getter
                    @Builder
                    @NoArgsConstructor
                    @AllArgsConstructor
                    public static class DeploymentPodPortDto {
                        private Integer containerPort;
                    }
                }
            }
        }
    }
}
