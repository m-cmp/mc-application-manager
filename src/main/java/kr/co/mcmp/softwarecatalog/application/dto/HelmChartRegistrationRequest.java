package kr.co.mcmp.softwarecatalog.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Helm Chart 등록 요청 DTO
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class HelmChartRegistrationRequest {

    private String packageId;       // ArtifactHub 패키지 ID
    private String name;            // Helm Chart 이름
    private String version;         // Helm Chart 버전
    private String appVersion;      // 애플리케이션 버전
    private String description;     // 설명
    private String category;        // 카테고리
    private String imageRepository; // 이미지 저장소
    private String license;         // 라이선스
    private String homepage;        // 홈페이지
    private String documentationUrl;// 문서 URL
    private String sourceUrl;       // 소스 코드 URL
    private String maintainers;     // 유지보수자 정보 (JSON 문자열 등)
    private String keywords;        // 키워드
    private String digest;          // 이미지 다이제스트
    private String tag;             // 이미지 태그

    private Repository repository;  // 저장소 객체
    private SecurityReportSummary securityReportSummary; // 보안 리포트 요약

    @Getter
    @Setter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Repository {
        private String url;
        private String name;
        private boolean official;
        private String displayName;
        private String organizationDisplayName;
        private String repositoryId;
        private boolean scannerDisabled;
        private String organizationName;
        private boolean verifiedPublisher;
    }
    @Getter
    @Setter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SecurityReportSummary {
        private int low;
        private int medium;
        private int high;
        private int critical;
        private int unknown;
    }

}
