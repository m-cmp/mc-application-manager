package kr.co.mcmp.softwarecatalog.kubernetes.config;

import io.fabric8.kubernetes.client.Config;
import kr.co.mcmp.ape.cbtumblebug.dto.K8sClusterDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.eks.EksClient;
import software.amazon.awssdk.services.eks.model.*;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class AwsKubeConfigProvider implements KubeConfigProvider {

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
        return "aws".equalsIgnoreCase(providerName);
    }

    @Override
    public String getOriginalKubeconfigYaml(K8sClusterDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("K8sClusterDto cannot be null");
        }
        
        if (dto.getAccessInfo() == null) {
            throw new IllegalStateException("AccessInfo is null for AWS cluster: " + dto.getName());
        }
        
        String kubeconfig = dto.getAccessInfo().getKubeconfig();
        if (kubeconfig == null || kubeconfig.trim().isEmpty()) {
            throw new IllegalStateException("Kubeconfig is null or empty for AWS cluster: " + dto.getName());
        }
        
        // AWS의 경우 localhost를 실제 Tumblebug 주소로 변경
        return processAwsKubeconfig(kubeconfig);
    }

    @Override
    public Config buildConfig(K8sClusterDto dto) {
        // String yaml = dto.getAccessInfo().getKubeconfig();
        // String server = extractServer(yaml);
        // String ca = extractCertificateAuthorityData(yaml);
        // String clusterName = dto.getLabel().get("sys.cspResourceName");

        // String token = EksClient.builder()
        //     .region(Region.of(System.getenv("AWS_REGION")))
        //     .credentialsProvider(DefaultCredentialsProvider.create())
        //     .build()
        //     .getToken(GetTokenRequest.builder().clusterName(clusterName).build())
        //     .token();

        // Config cfg = new Config();
        // cfg.setMasterUrl(server);
        // cfg.setCaCertData(ca);
        // // cfg.setOauthToken(token);
        // cfg.setTrustCerts(true);
        // cfg.setConnectionTimeout(30_000);
        // cfg.setRequestTimeout(30_000);
        // return cfg;
        return null;
    }

    private String extractServer(String yaml) {
        for (String line : yaml.split("\\r?\\n")) {
            if (line.trim().startsWith("server:")) {
                return line.split("server:")[1].trim();
            }
        }
        throw new IllegalStateException("server URL을 찾을 수 없습니다");
    }

    private String extractCertificateAuthorityData(String yaml) {
        StringBuilder sb = new StringBuilder();
        boolean inBlock = false;
        for (String line : yaml.split("\\r?\\n")) {
            if (line.trim().startsWith("certificate-authority-data:")) {
                inBlock = true;
                sb.append(line.substring(line.indexOf(':') + 1).trim());
            } else if (inBlock && (line.startsWith(" ") || line.startsWith("\t"))) {
                sb.append(line.trim());
            } else if (inBlock) {
                break;
            }
        }
        if (sb.isEmpty()) throw new IllegalStateException("certificate-authority-data를 찾을 수 없습니다");
        return sb.toString();
    }
    
    /**
     * AWS kubeconfig에서 localhost를 Tumblebug IP로 변경하고 exec 플러그인을 제거하여 직접 토큰 사용
     */
    private String processAwsKubeconfig(String kubeconfig) {
        System.out.println("=== AWS kubeconfig 처리 시작 ===");
        try {
            // 1. localhost:1024를 Tumblebug IP:1024로 변경
            String tumblebugHost = tumblebugUrl + ":1024";
            String processedKubeconfig = kubeconfig.replace("localhost:1024", tumblebugHost);
            System.out.println("1. 주소 변경: localhost:1024 -> " + tumblebugHost);
            
            // 2. kubeconfig에서 cluster ID와 connection name 추출
            String clusterId = extractClusterId(processedKubeconfig);
            String connectionName = extractConnectionName(processedKubeconfig);
            System.out.println("2. 추출된 정보 - clusterId: " + clusterId + ", connectionName: " + connectionName);
            
            // 3. 현재 토큰 가져오기
            System.out.println("3. 토큰 요청 시작...");
            String token = getCurrentToken(clusterId, connectionName);
            System.out.println("4. 토큰 가져오기 성공: " + token.substring(0, Math.min(20, token.length())) + "...");
            
            // 4. kubeconfig에서 exec 플러그인을 제거하고 직접 토큰 설정
            processedKubeconfig = replaceExecWithToken(processedKubeconfig, token);
            System.out.println("5. kubeconfig 수정 완료");
            
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
        throw new RuntimeException("kubeconfig에서 ConnectionName을 찾을 수 없습니다");
    }
    
    /**
     * Tumblebug에서 현재 토큰을 가져옵니다 (Java HTTP 클라이언트 사용)
     */
    private String getCurrentToken(String clusterId, String connectionName) throws Exception {
        String tumblebugHost = tumblebugUrl + ":1024";
        
        // URL 인코딩 처리 (백슬래시 및 따옴표 문제 해결)
        String cleanConnectionName = connectionName.replace("\\", "").replace("\"", "").trim();
        String encodedConnectionName = URLEncoder.encode(cleanConnectionName, StandardCharsets.UTF_8);
        String tokenUrl = "http://" + tumblebugHost + "/spider/cluster/" + clusterId + "/token?ConnectionName=" + encodedConnectionName;
        
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
            // "token":"실제토큰값" 형태에서 토큰 추출
            int tokenStart = responseBody.indexOf("\"token\":\"") + 9;
            int tokenEnd = responseBody.indexOf("\"", tokenStart);
            if (tokenStart > 8 && tokenEnd > tokenStart) {
                String token = responseBody.substring(tokenStart, tokenEnd);
                return token;
            }
        }
        
        throw new RuntimeException("토큰을 찾을 수 없습니다: " + responseBody);
    }
    
    /**
     * kubeconfig에서 exec 플러그인을 제거하고 직접 토큰을 설정
     */
    private String replaceExecWithToken(String kubeconfig, String token) {
        System.out.println("=== kubeconfig 수정 시작 ===");
        
        // kubeconfig를 줄별로 분석하여 정확한 YAML 구조 유지
        String[] lines = kubeconfig.split("\n");
        StringBuilder result = new StringBuilder();
        boolean inExecBlock = false;
        boolean userBlockFound = false;
        boolean tokenAdded = false;
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            
            if (line.trim().startsWith("users:")) {
                userBlockFound = true;
                result.append(line).append("\n");
            } else if (userBlockFound && line.trim().startsWith("exec:")) {
                inExecBlock = true;
                System.out.println("exec 블록 발견, 토큰으로 교체 중...");
                // exec: 라인을 token: 라인으로 교체
                result.append("    token: ").append(token).append("\n");
                tokenAdded = true;
                // exec 블록의 나머지 라인들 건너뛰기
                i++;
                while (i < lines.length && (lines[i].startsWith("      ") || lines[i].startsWith("    "))) {
                    i++;
                }
                i--; // 다음 반복에서 현재 라인을 처리하도록
            } else if (inExecBlock && (line.startsWith("      ") || line.startsWith("    "))) {
                // exec 블록 내부 라인들 건너뛰기
                continue;
            } else if (inExecBlock && !line.startsWith(" ") && !line.startsWith("\t")) {
                // exec 블록 종료
                inExecBlock = false;
                result.append(line).append("\n");
            } else {
                result.append(line).append("\n");
            }
        }
        
        // 토큰이 추가되지 않았다면 users 섹션에 직접 추가
        if (!tokenAdded && userBlockFound) {
            System.out.println("exec 블록을 찾을 수 없음, users 섹션에 토큰 추가");
            String modifiedKubeconfig = result.toString();
            modifiedKubeconfig = modifiedKubeconfig.replace(
                "users:\n- name: aws-dynamic-token\n  user:",
                "users:\n- name: aws-dynamic-token\n  user:\n    token: " + token
            );
            return modifiedKubeconfig;
        }
        
        System.out.println("=== kubeconfig 수정 완료 ===");
        return result.toString();
    }
}
