package kr.co.mcmp.softwarecatalog.kubernetes.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.springframework.stereotype.Component;

/**
 * Helm 릴리스 이름 생성을 위한 유틸리티 클래스
 */
@Component
public class ReleaseNameGenerator {
    
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final int MAX_RELEASE_NAME_LENGTH = 53; // Helm 릴리스 이름 최대 길이
    
    /**
     * 고유한 릴리스 이름을 생성합니다.
     * 형식: {chartName}-{timestamp}-{shortUuid}
     * 
     * @param chartName 차트 이름
     * @return 고유한 릴리스 이름
     */
    public String generateReleaseName(String chartName) {
        if (chartName == null || chartName.trim().isEmpty()) {
            chartName = "app";
        }
        
        // 차트 이름을 소문자로 변환하고 특수문자 제거
        String cleanChartName = chartName.toLowerCase()
                .replaceAll("[^a-z0-9-]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
        
        // 타임스탬프 생성
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        
        // 짧은 UUID 생성 (8자리)
        String shortUuid = UUID.randomUUID().toString().substring(0, 8);
        
        // 릴리스 이름 조합
        String releaseName = String.format("%s-%s-%s", cleanChartName, timestamp, shortUuid);
        
        // 최대 길이 제한
        if (releaseName.length() > MAX_RELEASE_NAME_LENGTH) {
            int maxChartNameLength = MAX_RELEASE_NAME_LENGTH - timestamp.length() - shortUuid.length() - 2; // 2는 하이픈 2개
            cleanChartName = cleanChartName.substring(0, Math.max(1, maxChartNameLength));
            releaseName = String.format("%s-%s-%s", cleanChartName, timestamp, shortUuid);
        }
        
        return releaseName;
    }
    
    /**
     * 기존 릴리스 이름에서 차트 이름 부분을 추출합니다.
     * 
     * @param releaseName 릴리스 이름
     * @return 차트 이름 부분
     */
    public String extractChartName(String releaseName) {
        if (releaseName == null || releaseName.trim().isEmpty()) {
            return null;
        }
        
        // 마지막 두 개의 하이픈을 기준으로 분리
        String[] parts = releaseName.split("-");
        if (parts.length >= 3) {
            // 마지막 두 부분(타임스탬프, UUID)을 제거하고 나머지를 조합
            StringBuilder chartName = new StringBuilder();
            for (int i = 0; i < parts.length - 2; i++) {
                if (chartName.length() > 0) {
                    chartName.append("-");
                }
                chartName.append(parts[i]);
            }
            return chartName.toString();
        }
        
        return releaseName;
    }
    
    /**
     * 릴리스 이름이 유효한지 확인합니다.
     * 
     * @param releaseName 릴리스 이름
     * @return 유효성 여부
     */
    public boolean isValidReleaseName(String releaseName) {
        if (releaseName == null || releaseName.trim().isEmpty()) {
            return false;
        }
        
        // Helm 릴리스 이름 규칙: 소문자, 숫자, 하이픈만 허용, 53자 이하
        return releaseName.matches("^[a-z0-9-]+$") && 
               releaseName.length() <= MAX_RELEASE_NAME_LENGTH &&
               !releaseName.startsWith("-") && 
               !releaseName.endsWith("-");
    }
}
