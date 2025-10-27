package kr.co.mcmp.softwarecatalog.kubernetes.config;

import io.fabric8.kubernetes.client.Config;
import kr.co.mcmp.ape.cbtumblebug.dto.K8sClusterDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class GcpKubeConfigProvider implements KubeConfigProvider {

    @Value("${cbtumblebug.url}")
    private String tumblebugUrl;
    
    @Value("${cbtumblebug.port}")
    private String tumblebugPort;
    
    @Value("${cbtumblebug.id}")
    private String tumblebugId;
    
    @Value("${cbtumblebug.pass}")
    private String tumblebugPassword;

    @Override
    public boolean supports(String providerName) {
        return "gcp".equalsIgnoreCase(providerName);
    }

    @Override
    public String getOriginalKubeconfigYaml(K8sClusterDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("K8sClusterDto cannot be null");
        }
        
        if (dto.getAccessInfo() == null) {
            throw new IllegalStateException("AccessInfo is null for GCP cluster: " + dto.getName());
        }
        
        String originalKubeconfig = dto.getAccessInfo().getKubeconfig();
        if (originalKubeconfig == null || originalKubeconfig.trim().isEmpty()) {
            throw new IllegalStateException("Kubeconfig is null or empty for GCP cluster: " + dto.getName());
        }
        
        // GCP의 경우 원본 kubeconfig를 그대로 반환 (Helm에서는 원본이 필요)
        return originalKubeconfig;
    }

    @Override
    public Config buildConfig(K8sClusterDto dto) {
        String yaml = dto.getAccessInfo().getKubeconfig();
        String patched = processGcpKubeconfig(yaml);
        Config cfg = Config.fromKubeconfig(patched);
        cfg.setTrustCerts(true);
        cfg.setConnectionTimeout(30_000);
        cfg.setRequestTimeout(30_000);
        // remove exec auth
        cfg.setImpersonateUsername(null);
        cfg.setAuthProvider(null);
        return cfg;
    }

    private String processGcpKubeconfig(String kubeconfig) {
        System.out.println("=== GCP kubeconfig 처리 시작 ===");
        try {
            // 1. localhost만 Tumblebug IP로 변경 (포트는 그대로 유지)
            String processedKubeconfig = kubeconfig.replace("localhost:", tumblebugUrl + ":");
            
            // 2. kubeconfig에서 cluster ID와 connection name 추출
            String clusterId = extractClusterId(processedKubeconfig);
            String connectionName = extractConnectionName(processedKubeconfig);
            
            // 3. 현재 토큰 가져오기
            String token = getCurrentToken(clusterId, connectionName);
            
            // 4. kubeconfig에서 exec 플러그인을 제거하고 직접 토큰 설정
            processedKubeconfig = replaceExecWithToken(processedKubeconfig, token);
            
            return processedKubeconfig;
        } catch (Exception e) {
            System.err.println("토큰 가져오기 실패, 원본 kubeconfig 사용: " + e.getMessage());
            e.printStackTrace();
            return kubeconfig;
        }
    }
    
    /**
     * kubeconfig에서 cluster ID 추출
     */
    private String extractClusterId(String kubeconfig) {
        for (String line : kubeconfig.split("\n")) {
            if (line.contains("/spider/cluster/") && line.contains("/token")) {
                // "http://localhost:1024/spider/cluster/CLUSTER_ID/token?ConnectionName=..."
                String[] parts = line.split("/spider/cluster/");
                if (parts.length > 1) {
                    String clusterPart = parts[1].split("/token")[0];
                    return clusterPart;
                }
            }
        }
        throw new RuntimeException("kubeconfig에서 cluster ID를 찾을 수 없습니다");
    }
    
    /**
     * kubeconfig에서 connection name 추출
     */
    private String extractConnectionName(String kubeconfig) {
        for (String line : kubeconfig.split("\n")) {
            if (line.contains("ConnectionName=")) {
                // "http://localhost:1024/spider/cluster/.../token?ConnectionName=CONNECTION_NAME"
                String[] parts = line.split("ConnectionName=");
                if (parts.length > 1) {
                    return parts[1].trim();
                }
            }
        }
        throw new RuntimeException("kubeconfig에서 connection name을 찾을 수 없습니다");
    }
    
    /**
     * Tumblebug에서 현재 토큰을 가져옵니다 (Java HTTP 클라이언트 사용)
     */
    private String getCurrentToken(String clusterId, String connectionName) throws Exception {
        // URL 인코딩 처리 (백슬래시 및 따옴표 문제 해결)
        String cleanConnectionName = connectionName.replace("\\", "").replace("\"", "").trim();
        String encodedConnectionName = URLEncoder.encode(cleanConnectionName, StandardCharsets.UTF_8);
        String tokenUrl = "http://" + tumblebugUrl + ":1024/spider/cluster/" + clusterId + "/token?ConnectionName=" + encodedConnectionName;
        
        System.out.println("토큰 요청 URL: " + tokenUrl);
        
        // Java HTTP 클라이언트로 토큰 가져오기 (인증 헤더 포함)
        HttpClient client = HttpClient.newHttpClient();
        
        // Basic Auth 헤더 생성
        String auth = tumblebugId + ":" + tumblebugPassword;
        String encodedAuth = java.util.Base64.getEncoder().encodeToString(auth.getBytes());
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tokenUrl))
                .header("Authorization", "Basic " + encodedAuth)
                .GET()
                .build();
        
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new RuntimeException("토큰 요청 실패: HTTP " + response.statusCode() + " - " + response.body());
        }
        
        String responseBody = response.body();
        System.out.println("Tumblebug 응답: " + responseBody);
        
        // JSON 응답에서 token 추출 (더 정확한 파싱)
        if (responseBody.contains("\"token\"")) {
            String[] parts = responseBody.split("\"token\"");
            if (parts.length > 1) {
                String tokenPart = parts[1].split("\"")[1];
                return tokenPart;
            }
        }
        
        throw new RuntimeException("응답에서 토큰을 찾을 수 없습니다: " + responseBody);
    }
    
    /**
     * kubeconfig에서 exec 플러그인을 제거하고 직접 토큰 설정
     */
    private String replaceExecWithToken(String kubeconfig, String token) {
        // exec 플러그인을 제거하고 직접 토큰 설정
        String processed = kubeconfig
            .replaceAll("exec:\\s*\\{[^}]*\\}", "")
            .replaceAll("gcp-dynamic-token", "gke-user")
            .replaceAll("command: curl[^\\n]*\\n", "")
            .replaceAll("args:[^\\n]*\\n", "")
            .replaceAll("apiVersion: client\\.authentication\\.k8s\\.io/v1[^\\n]*\\n", "")
            .replaceAll("interactiveMode: Never[^\\n]*\\n", "");
        
        // 사용자 섹션을 토큰 인증으로 교체
        if (processed.contains("users:")) {
            String[] parts = processed.split("users:");
            if (parts.length >= 2) {
                StringBuilder sb = new StringBuilder(parts[0])
                    .append("users:\n")
                    .append("- name: gke-user\n")
                    .append("  user:\n")
                    .append("    token: \"").append(token).append("\"\n");
                
                int idx = parts[1].indexOf("current-context:");
                if (idx >= 0) {
                    sb.append(parts[1].substring(idx));
                }
                return sb.toString();
            }
        }
        
        return processed;
    }
}
