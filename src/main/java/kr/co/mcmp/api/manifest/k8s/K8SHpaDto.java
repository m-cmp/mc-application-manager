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
public class K8SHpaDto {

    private final String apiVersion = "autoscaling/v1";
    private final String kind = "HorizontalPodAutoscaler";
    private HpaMetadataDto metadata;
    private HpaSpecDto spec;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HpaMetadataDto {
        private String name;
        private String namespace;
        private Map<String, String> labels = null;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HpaSpecDto {
        private HpaScaleTargetRefDto scaleTargetRef;
        private Integer minReplicas;
        private Integer maxReplicas;
        private Integer targetCPUUtilizationPercentage;

        @Getter
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class HpaScaleTargetRefDto {
            private String apiVersion;
            private String kind;
            private String name;
        }
    }
}
