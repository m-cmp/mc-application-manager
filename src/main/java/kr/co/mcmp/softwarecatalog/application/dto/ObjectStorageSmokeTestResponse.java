package kr.co.mcmp.softwarecatalog.application.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ObjectStorageSmokeTestResponse {
    private boolean success;
    private String detectedProvider;
    private String backendType;

    @Builder.Default
    private List<CheckResult> checks = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CheckResult {
        private String name;
        private boolean success;
        private String message;
    }
}
