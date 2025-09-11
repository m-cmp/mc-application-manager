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
}
