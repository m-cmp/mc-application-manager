package kr.co.mcmp.softwarecatalog.kubernetes.service;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import com.marcnuri.helm.Helm;
import com.marcnuri.helm.InstallCommand;
import com.marcnuri.helm.Release;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import kr.co.mcmp.softwarecatalog.SoftwareCatalog;
import kr.co.mcmp.softwarecatalog.application.config.NexusConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class HelmChartService {

    private final NexusConfig nexusConfig;



    public String convertConfigToYaml(Config config) {
        // SnakeYAML 설정
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);  // 블록 스타일 (가독성이 좋음)
        Yaml yaml = new Yaml(options);

        // Config 객체를 YAML로 직렬화
        StringWriter writer = new StringWriter();
        yaml.dump(config, writer);

        return writer.toString();
    }
    public Release deployHelmChart(KubernetesClient client, String namespace, SoftwareCatalog catalog) {
        Path tempKubeconfigPath = null;

        try {
            // Helm repository 추가
            addHelmRepository(catalog);

            Config kubeConfig = client.getConfiguration();
            log.info("===========================kubeconfig");
            log.info(kubeConfig.toString());  // Config 객체 로그 확인
            log.info("===========================================");

            // Config 객체를 YAML로 변환
            String kubeconfigYaml = convertConfigToYaml(kubeConfig);  // Config를 YAML 형식으로 직렬화
            log.info(kubeconfigYaml);
            // 임시 kubeconfig 파일 생성
            tempKubeconfigPath = createTempKubeconfigFile(kubeConfig.toString());  // YAML 파일로 저장

            // Helm 설치 명령어 생성
            InstallCommand installCommand = Helm.install(
                    catalog.getHelmChart().getRepositoryName() + "/" + catalog.getHelmChart().getChartName())
                    .withKubeConfig(tempKubeconfigPath) // Path 타입으로 kubeconfig 전달
                    .withName(catalog.getHelmChart().getChartName())
                    .withNamespace(namespace)
                    .withVersion(catalog.getHelmChart().getChartVersion())
                    .set("replicaCount", catalog.getMinReplicas())
                    .set("image.repository", buildImageRepository(catalog))
                    .set("image.tag", catalog.getHelmChart().getChartVersion())
                    .set("image.pullPolicy", "Always")
                    .set("service.port", catalog.getDefaultPort())
                    .set("resources.requests.cpu", catalog.getMinCpu().toString())
                    .set("resources.requests.memory", catalog.getMinMemory() + "Mi")
                    .set("resources.limits.cpu", catalog.getRecommendedCpu().toString())
                    .set("resources.limits.memory", catalog.getRecommendedMemory() + "Mi");

            // HPA 설정 추가
            if (Boolean.TRUE.equals(catalog.getHpaEnabled())) {
                installCommand
                        .set("autoscaling.enabled", true)
                        .set("autoscaling.minReplicas", catalog.getMinReplicas())
                        .set("autoscaling.maxReplicas", catalog.getMaxReplicas())
                        .set("autoscaling.targetCPUUtilizationPercentage", catalog.getCpuThreshold().intValue())
                        .set("autoscaling.targetMemoryUtilizationPercentage", catalog.getMemoryThreshold().intValue());
            }

            // Helm 차트 설치 실행
            Release result = installCommand.call();

            log.info("Helm Chart '{}' 버전 '{}'가 네임스페이스 '{}'에 배포됨 (HPA: {})",
                    catalog.getHelmChart().getChartName(),
                    "latest",
                    namespace,
                    catalog.getHpaEnabled());
            return result;

        } catch (Exception e) {
            log.error("Helm Chart 배포 중 오류 발생", e);
            throw new RuntimeException("Helm Chart 배포 실패", e);
        } finally {
            // 임시 kubeconfig 파일 삭제
            if (tempKubeconfigPath != null) {
                try {
                    deleteTempFile(tempKubeconfigPath);
                } catch (IOException e) {
                    log.warn("임시 kubeconfig 파일 삭제 실패: {}", tempKubeconfigPath, e);
                }
            }
        }
    }

    public void uninstallHelmChart(String namespace, SoftwareCatalog catalog) {
        try {
            String result = Helm.uninstall(catalog.getHelmChart().getChartName())
                    .withNamespace(namespace)
                    .call();

            boolean deleted = result != null && !result.isEmpty();

            if (deleted) {
                log.info("Helm Release '{}' 가 네임스페이스 '{}'에서 삭제됨",
                        catalog.getHelmChart().getChartName(), namespace);
            } else {
                log.warn("Helm Release '{}' 삭제 실패",
                        catalog.getHelmChart().getChartName());
            }
        } catch (Exception e) {
            log.error("Helm Release 삭제 중 오류 발생", e);
            throw new RuntimeException("Helm Release 삭제 실패", e);
        }
    }

    private void addHelmRepository(SoftwareCatalog catalog) throws Exception {
        String chartRepositoryUrl = getHelmChartRepositoryUrl(catalog);
        Helm.repo().add()
                .withName(catalog.getHelmChart().getRepositoryName())
                .withUrl(URI.create(chartRepositoryUrl))
                .call();
        Helm.repo().update();
    }



    // 임시 kubeconfig 파일 생성
    private Path createTempKubeconfigFile(String kubeconfig) throws IOException {
        Path tempFile = Files.createTempFile("kubeconfig", ".yaml");
        Files.write(tempFile, kubeconfig.getBytes(StandardCharsets.UTF_8));
        return tempFile;
    }

    // 임시 kubeconfig 파일 삭제
    private void deleteTempFile(Path tempFile) throws IOException {
        Files.deleteIfExists(tempFile);
    }
    
    /**
     * 소스 타입에 따라 적절한 이미지 레포지토리 URL을 생성합니다.
     */
    private String buildImageRepository(SoftwareCatalog catalog) {
        String imageName = catalog.getHelmChart().getImageRepository();
        if (imageName == null || imageName.isEmpty()) {
            imageName = catalog.getName().toLowerCase().replaceAll("\\s+", "-");
        }
        
        String sourceType = catalog.getSourceType();
        return nexusConfig.getImageUrlBySourceType(imageName, "latest", sourceType).replace(":latest", "");
    }
    
    /**
     * 소스 타입에 따라 적절한 Helm 차트 레포지토리 URL을 반환합니다.
     */
    private String getHelmChartRepositoryUrl(SoftwareCatalog catalog) {
        String sourceType = catalog.getSourceType();
        String originalUrl = catalog.getHelmChart().getChartRepositoryUrl();
        
        return nexusConfig.getHelmChartUrlBySourceType(originalUrl, sourceType);
    }
}
