package kr.co.mcmp.softwarecatalog.application.dto;

import kr.co.mcmp.softwarecatalog.SoftwareCatalog;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 배포 설정을 관리하는 DTO
 * Request 파라미터와 카탈로그 기본값을 조합하여 최종 설정값을 제공
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeploymentConfigDTO {
    
    // HPA 설정
    private Boolean hpaEnabled;
    private Integer minReplicas;
    private Integer maxReplicas;
    private Double cpuThreshold;
    private Double memoryThreshold;
    
    // Ingress 설정
    private Boolean ingressEnabled;
    private String ingressHost;
    private String ingressPath;
    private String ingressClass;
    private Boolean ingressTlsEnabled;
    private String ingressTlsSecret;
    
    /**
     * DeploymentRequest와 SoftwareCatalog를 기반으로 최종 설정을 생성합니다.
     * 우선순위: Request > Catalog > Default
     */
    public static DeploymentConfigDTO from(DeploymentRequest request, SoftwareCatalog catalog) {
        return DeploymentConfigDTO.builder()
                // HPA 설정 (Request 우선)
                .hpaEnabled(getValue(request.getHpaEnabled(), catalog.getHpaEnabled(), false))
                .minReplicas(getValue(request.getMinReplicas(), catalog.getMinReplicas(), 1))
                .maxReplicas(getValue(request.getMaxReplicas(), catalog.getMaxReplicas(), 5))
                .cpuThreshold(getValue(request.getCpuThreshold(), catalog.getCpuThreshold(), 80.0))
                .memoryThreshold(getValue(request.getMemoryThreshold(), catalog.getMemoryThreshold(), 80.0))
                
                // Ingress 설정 (Request 우선)
                .ingressEnabled(getValue(request.getIngressEnabled(), catalog.getIngressEnabled(), false))
                .ingressHost(getValue(request.getIngressHost(), catalog.getIngressHost(), "localhost"))
                .ingressPath(getValue(request.getIngressPath(), catalog.getIngressPath(), "/"))
                .ingressClass(getValue(request.getIngressClass(), catalog.getIngressClass(), "nginx"))
                .ingressTlsEnabled(getValue(request.getIngressTlsEnabled(), catalog.getIngressTlsEnabled(), false))
                .ingressTlsSecret(getValue(request.getIngressTlsSecret(), catalog.getIngressTlsSecret(), null))
                .build();
    }
    
    /**
     * 우선순위에 따라 값을 반환합니다.
     * 1순위: requestValue
     * 2순위: catalogValue  
     * 3순위: defaultValue
     */
    private static <T> T getValue(T requestValue, T catalogValue, T defaultValue) {
        if (requestValue != null) {
            return requestValue;
        }
        if (catalogValue != null) {
            return catalogValue;
        }
        return defaultValue;
    }
    
    /**
     * HPA가 활성화되어 있는지 확인합니다.
     */
    public boolean isHpaEnabled() {
        return Boolean.TRUE.equals(hpaEnabled);
    }
    
    /**
     * Ingress가 활성화되어 있는지 확인합니다.
     */
    public boolean isIngressEnabled() {
        return Boolean.TRUE.equals(ingressEnabled);
    }
    
    /**
     * TLS가 활성화되어 있는지 확인합니다.
     */
    public boolean isTlsEnabled() {
        return Boolean.TRUE.equals(ingressTlsEnabled);
    }
    
    /**
     * 설정 정보를 로그용 문자열로 반환합니다.
     */
    public String getHpaConfigSummary() {
        if (!isHpaEnabled()) {
            return "HPA 비활성화됨";
        }
        return String.format("HPA 설정 - Min: %d, Max: %d, CPU: %.1f%%, Memory: %.1f%%", 
                minReplicas, maxReplicas, cpuThreshold, memoryThreshold);
    }
    
    /**
     * Ingress 설정 정보를 로그용 문자열로 반환합니다.
     */
    public String getIngressConfigSummary() {
        if (!isIngressEnabled()) {
            return "Ingress 비활성화됨";
        }
        return String.format("Ingress 설정 - Host: %s, Path: %s, Class: %s, TLS: %s", 
                ingressHost, ingressPath, ingressClass, isTlsEnabled());
    }
    
    @Override
    public String toString() {
        return String.format("DeploymentConfig{HPA: %s, Ingress: %s}", 
                getHpaConfigSummary(), getIngressConfigSummary());
    }
}
