package kr.co.mcmp.softwarecatalog.application.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Docker Hub 이미지 등록 요청 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DockerHubImageRegistrationRequest {
    
    /**
     * Docker Hub 이미지 이름
     */
    private String imageName;
    
    /**
     * 이미지 태그
     */
    private String tag;
    
    /**
     * 카탈로그 제목
     */
    private String title;
    
    /**
     * 카탈로그 설명
     */
    private String description;
    
    /**
     * 카탈로그 카테고리
     */
    private String category;
    
    /**
     * 소스 타입 (DOCKERHUB)
     */
    private String sourceType;
    
    /**
     * Docker Hub 이미지 URL
     */
    private String dockerHubUrl;
    
    /**
     * Docker Hub 이미지 설명
     */
    private String dockerHubDescription;
    
    /**
     * Docker Hub 이미지 스타 수
     */
    private Integer starCount;
    
    /**
     * Docker Hub 이미지 풀 카운트
     */
    private Long pullCount;
    
    /**
     * Docker Hub 이미지 크기 (bytes)
     */
    private Long size;
    
    /**
     * Docker Hub 이미지 아키텍처
     */
    private String architecture;
    
    /**
     * Docker Hub 이미지 OS
     */
    private String os;
    
    /**
     * Docker Hub 이미지 생성일
     */
    private String created;
    
    /**
     * Docker Hub 이미지 업데이트일
     */
    private String updated;
}
