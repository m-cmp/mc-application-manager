package kr.co.mcmp.softwarecatalog.application.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

/**
 * 넥서스 설정 정보를 담는 Configuration 클래스
 */
@Configuration
@ConfigurationProperties(prefix = "nexus")
@Data
public class NexusConfig {
    
    /**
     * 넥서스 서버 URL
     */
    private String url;
    
    /**
     * 넥서스 웹 포트
     */
    private int port;
    
    /**
     * 넥서스 Docker 레지스트리 포트
     */
    private int dockerPort;
    
    /**
     * 넥서스 사용자명
     */
    private String username;
    
    /**
     * 넥서스 비밀번호
     */
    private String password;
    
    /**
     * Docker 레포지토리 이름
     */
    private String dockerRepository;
    
    /**
     * Helm 레포지토리 이름
     */
    private String helmRepository;
    
    /**
     * 하이브리드 모드 사용 여부 (외부 + 내부 넥서스)
     */
    private boolean hybridMode = true;
    
    /**
     * 외부 Docker Hub 사용 여부
     */
    private boolean useExternalDockerHub = true;
    
    /**
     * 외부 Artifact Hub 사용 여부
     */
    private boolean useExternalArtifactHub = true;
    
    /**
     * 넥서스 웹 URL을 반환합니다.
     */
    public String getWebUrl() {
        return "http://" + url + ":" + port;
    }
    
    /**
     * 넥서스 Docker 레지스트리 URL을 반환합니다.
     */
    public String getDockerRegistryUrl() {
        // Docker 레지스트리는 localhost를 사용 (로컬 도커 환경)
        return "localhost:" + dockerPort;
    }
    
    /**
     * 넥서스 Docker 이미지 풀 URL을 반환합니다.
     */
    public String getDockerImageUrl(String imageName, String tag) {
        return getDockerRegistryUrl() + "/" + dockerRepository + "/" + imageName + ":" + tag;
    }
    
    /**
     * 넥서스 Helm 차트 URL을 반환합니다.
     */
    public String getHelmChartUrl() {
        return getWebUrl() + "/repository/" + helmRepository + "/";
    }
    
    /**
     * 소스 타입에 따라 적절한 이미지 URL을 반환합니다.
     */
    public String getImageUrlBySourceType(String imageName, String tag, String sourceType) {
        if (hybridMode) {
            switch (sourceType.toUpperCase()) {
                case "DOCKERHUB":
                    if (useExternalDockerHub) {
                        return "docker.io/" + imageName + ":" + tag;
                    } else {
                        return getDockerImageUrl(imageName, tag);
                    }
                case "ARTIFACTHUB":
                    if (useExternalArtifactHub) {
                        return "docker.io/" + imageName + ":" + tag;
                    } else {
                        return getDockerImageUrl(imageName, tag);
                    }
                case "NEXUS":
                default:
                    return getDockerImageUrl(imageName, tag);
            }
        } else {
            // 하이브리드 모드가 아닌 경우 항상 넥서스 사용
            return getDockerImageUrl(imageName, tag);
        }
    }
    
    /**
     * 소스 타입에 따라 적절한 Helm 차트 URL을 반환합니다.
     */
    public String getHelmChartUrlBySourceType(String chartRepositoryUrl, String sourceType) {
        if (hybridMode) {
            switch (sourceType.toUpperCase()) {
                case "ARTIFACTHUB":
                    if (useExternalArtifactHub) {
                        return chartRepositoryUrl; // 외부 Artifact Hub URL 사용
                    } else {
                        return getHelmChartUrl();
                    }
                case "NEXUS":
                default:
                    return getHelmChartUrl();
            }
        } else {
            // 하이브리드 모드가 아닌 경우 항상 넥서스 사용
            return getHelmChartUrl();
        }
    }
}
