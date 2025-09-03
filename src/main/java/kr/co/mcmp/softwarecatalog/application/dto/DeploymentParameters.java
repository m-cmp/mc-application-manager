package kr.co.mcmp.softwarecatalog.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 배포 파라미터 정보를 담는 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeploymentParameters {
    
    /**
     * 컨테이너 이름
     */
    private String name;
    
    /**
     * 이미지 이름
     */
    private String image;
    
    /**
     * 포트 바인딩 (hostPort:containerPort)
     */
    private String portBindings;
    
    /**
     * 환경 변수
     */
    private Map<String, String> environmentVariables;
    
    /**
     * 볼륨 마운트
     */
    private String volumeMounts;
    
    /**
     * 리소스 제한
     */
    private ResourceLimits resourceLimits;
    
    /**
     * 추가 설정
     */
    private Map<String, Object> additionalConfig;
    
    /**
     * 리소스 제한 정보
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResourceLimits {
        private Double cpu;
        private Long memory; // MB
        private Long storage; // GB
    }
}


