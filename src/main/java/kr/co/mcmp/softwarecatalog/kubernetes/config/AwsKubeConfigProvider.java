package kr.co.mcmp.softwarecatalog.kubernetes.config;

import io.fabric8.kubernetes.client.Config;
import kr.co.mcmp.ape.cbtumblebug.dto.K8sClusterDto;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.eks.EksClient;
import software.amazon.awssdk.services.eks.model.*;

@Component
public class AwsKubeConfigProvider implements KubeConfigProvider {

    @Override
    public boolean supports(String providerName) {
        return "aws".equalsIgnoreCase(providerName);
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
}
