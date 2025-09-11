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
    
    private String packageId;        // ArtifactHub 패키지 ID
    private String chartName;        // Helm Chart 이름
    private String chartVersion;     // Helm Chart 버전
    private String repositoryUrl;    // 저장소 URL
    private String category;         // 카테고리
    private String imageRepository;  // 이미지 저장소
    private String description;      // 설명
    private String license;          // 라이선스   
    private String homepage;         // 홈페이지
    private String documentationUrl; // 문서 URL
    private String sourceUrl;        // 소스 코드 URL
    private String maintainers;      // 유지보수자 정보 (JSON 문자열 등으로 표현 가능)
    private String keywords;         // 키워드 (쉼표로 구분된 문자열
    private String digest;           // 이미지 다이제스트
}
