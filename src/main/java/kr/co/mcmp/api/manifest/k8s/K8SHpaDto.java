package kr.co.mcmp.api.manifest.k8s;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class K8SHpaDto {

    private final String apiVersion = "autoscaling/v1";
    private final String kind = "HorizontalPodAutoscaler";
    @Valid
    @NotNull
    private HpaMetadataDto metadata;
    @Valid
    @NotNull
    private HpaSpecDto spec;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HpaMetadataDto {
        @NotBlank
        private String name;
        private String namespace;
        private Map<String, String> labels = null;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HpaSpecDto {
        @Valid
        @NotNull
        private HpaScaleTargetRefDto scaleTargetRef;
        @NotNull
        private Integer minReplicas;
        @NotNull
        private Integer maxReplicas;
        @NotNull
        private Integer targetCPUUtilizationPercentage;

        @Getter
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class HpaScaleTargetRefDto {
            @NotBlank
            private String apiVersion;
            @NotBlank
            private String kind;
            @NotBlank
            private String name;
        }
    }
}
