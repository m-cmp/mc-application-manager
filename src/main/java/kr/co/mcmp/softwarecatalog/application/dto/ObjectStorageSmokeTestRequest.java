package kr.co.mcmp.softwarecatalog.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ObjectStorageSmokeTestRequest {
    private String namespace;
    private String clusterName;
    private Long catalogId;
    private ObjectStorageConfig objectStorage;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ObjectStorageConfig {
        private Boolean enabled;
        private String backendType;
        private String endpoint;
        private String region;
        private String bucket;
        private String accessKey;
        private String secretKey;
        private Boolean forcePathStyle;
        private Boolean insecure;
    }
}
