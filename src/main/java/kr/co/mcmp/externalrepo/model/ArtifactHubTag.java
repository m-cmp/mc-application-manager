package kr.co.mcmp.externalrepo.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;
import lombok.ToString;

import java.util.List;

@Data
@ToString
public class ArtifactHubTag {
    private String packageId;
    private String name;
    private String normalizedName;
    private String description;
    private boolean official;
    private String category;
    private List<String> keywords;
    private String repositoryUrl;
    private String readme;
    @JsonAlias({"available_versions", "availableVersions"})
    private List<ArtifactHubVersion> availableVersions;

    @Data
    public static class ArtifactHubVersion {
        private String version;              // Helm Chart 버전
        private String appVersion;           // 실제 앱 버전
        private boolean containsSecurityUpdates;
        private String createdAt;            // 등록/업데이트 시점
        private String digest;               // 차트 패키지 해시
        private String license;              // 라이선스
        private String url;                  // 다운로드 URL
    }
}
