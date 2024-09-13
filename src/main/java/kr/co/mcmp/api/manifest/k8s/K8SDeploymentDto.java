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
public class K8SDeploymentDto {

    private final String apiVersion = "apps/v1";
    private final String kind = "Deployment";
    @Valid
    @NotNull
    private DeploymentMetadataDto metadata;
    @Valid
    @NotNull
    private DeploymentSpecDto spec;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeploymentMetadataDto {
        @NotBlank
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
        @Valid
        @NotNull
        private DeploymentSelectorDto selector;
        @Valid
        @NotNull
        private DeploymentTemplateDto template;

        @Getter
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class DeploymentSelectorDto {
            @NotNull
            private Map<String, String> matchLabels;
        }

        @Getter
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class DeploymentTemplateDto {
            @Valid
            @NotNull
            private DeploymentMetadataDto metadata;
            @Valid
            @NotNull
            private DeploymentPodSpecDto spec;

            @Getter
            @Builder
            @NoArgsConstructor
            @AllArgsConstructor
            public static class DeploymentPodSpecDto {
                @NotNull
                private List<DeploymentPodContainerDto> containers;

                @Getter
                @Builder
                @NoArgsConstructor
                @AllArgsConstructor
                public static class DeploymentPodContainerDto {
                    @NotBlank
                    private String name;
                    @NotBlank
                    private String image;
                    private List<DeploymentPodEnvDto> env = null;
                    private List<DeploymentPodPortDto> ports = null;

                    @Getter
                    @Builder
                    @NoArgsConstructor
                    @AllArgsConstructor
                    public static class DeploymentPodEnvDto {
                        @NotBlank
                        private String name;
                        private String value;
                    }

                    @Getter
                    @Builder
                    @NoArgsConstructor
                    @AllArgsConstructor
                    public static class DeploymentPodPortDto {
                        @NotNull
                        private Integer containerPort;
                    }
                }
            }
        }
    }
}
