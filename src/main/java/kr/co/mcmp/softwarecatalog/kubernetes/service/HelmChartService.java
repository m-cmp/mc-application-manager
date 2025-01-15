package kr.co.mcmp.softwarecatalog.kubernetes.service;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.stereotype.Service;

import com.marcnuri.helm.Helm;
import com.marcnuri.helm.InstallCommand;
import com.marcnuri.helm.Release;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import kr.co.mcmp.ape.cbtumblebug.api.CbtumblebugRestApi;
import kr.co.mcmp.ape.cbtumblebug.dto.K8sClusterDto;
import kr.co.mcmp.softwarecatalog.SoftwareCatalog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
@Service
@Slf4j
@RequiredArgsConstructor
public class HelmChartService {
    private final CbtumblebugRestApi api;

    public Release deployHelmChart(KubernetesClient client, String namespace, SoftwareCatalog catalog,
            String clusterName) {
        Path tempConfigFile = null;
        K8sClusterDto dto = api.getK8sClusterByName(namespace, clusterName);
        String cspType = dto.getConnectionConfig().getProviderName().toUpperCase();
        log.info("CSP Type: {}", cspType);
        
        try {
            addHelmRepository(catalog);
            String kubeconfig = dto.getCspViewK8sClusterDetail().getAccessInfo().getKubeconfig();
            
             // GCP의 경우 gke-gcloud-auth-plugin 관련 설정 제거
            if ("GCP".equalsIgnoreCase(cspType)) {
                kubeconfig = kubeconfig.replaceAll("command:\\s*gke-gcloud-auth-plugin", "")
                                    .replaceAll("apiVersion:\\s*client.authentication.k8s.io/.*\\n", "")
                                    .replaceAll("installHint:.*\\n", "")
                                    .replaceAll("provideClusterInfo:.*\\n", "");
            }
            tempConfigFile = createTempKubeconfigFile(kubeconfig);

            InstallCommand installCommand = createInstallCommand(catalog, namespace, tempConfigFile);
            applyCspSpecificSettings(installCommand, cspType);

            // HPA 설정
            if (Boolean.TRUE.equals(catalog.getHpaEnabled())) {
                installCommand
                    .set("autoscaling.enabled", true)
                    .set("autoscaling.minReplicas", catalog.getMinReplicas())
                    .set("autoscaling.maxReplicas", catalog.getMaxReplicas())
                    .set("autoscaling.targetCPUUtilizationPercentage", catalog.getCpuThreshold())
                    .set("autoscaling.targetMemoryUtilizationPercentage", catalog.getMemoryThreshold());
            }

            Release result = installCommand.call();
            log.info("Helm Chart '{}' 배포 완료 - namespace: {}, CSP: {}", 
                    catalog.getHelmChart().getChartName(), 
                    namespace,
                    cspType);
            return result;

        } catch (Exception e) {
            log.error("Helm Chart 배포 중 오류 발생 - CSP: {}", cspType, e);
            throw new RuntimeException("Helm Chart 배포 실패", e);
        } finally {
            deleteTempFile(tempConfigFile);
        }
    }


    private InstallCommand createInstallCommand(SoftwareCatalog catalog, String namespace, Path configFile) {
        return Helm.install(catalog.getHelmChart().getRepositoryName() + "/" + catalog.getHelmChart().getChartName())
                .withKubeConfig(configFile)
                .withName(catalog.getHelmChart().getChartName())
                .withNamespace(namespace)
                .withVersion(catalog.getHelmChart().getChartVersion())
                .set("replicaCount", catalog.getMinReplicas())
                .set("image.repository", catalog.getHelmChart().getImageRepository())
                .set("image.tag", "latest")
                .set("image.pullPolicy", "Always")
                .set("service.port", catalog.getDefaultPort())
                .set("resources.requests.cpu", catalog.getMinCpu().toString())
                .set("resources.requests.memory", catalog.getMinMemory() + "Mi")
                .set("resources.limits.cpu", catalog.getRecommendedCpu().toString())
                .set("resources.limits.memory", catalog.getRecommendedMemory() + "Mi")
                .set("persistence.enabled", false)
                .set("securityContext.enabled", false)
                .set("serviceAccount.create", true)
                .withTimeout(300)
                .waitReady();
    }

    private void applyCspSpecificSettings(InstallCommand command, String cspType) {
        log.info("Applying CSP specific settings for: {}", cspType);
        switch (cspType) {
            case "GCP":
                command.set("gcp.auth.enabled", false)
                       .set("serviceAccount.annotations.iam\\.gke\\.io/gcp-service-account", "false")
                       .set("rbac.create", true);
                break;
            case "AZURE":
                command.set("azure.auth.enabled", false)
                       .set("serviceAccount.annotations.azure\\.workload\\.identity/use", "false");
                break;
            default:
                log.warn("알 수 없는 CSP 타입: {}", cspType);
        }
    }

    private void addHelmRepository(SoftwareCatalog catalog) throws Exception {
        Helm.repo().add()
            .withName(catalog.getHelmChart().getRepositoryName())
            .withUrl(URI.create(catalog.getHelmChart().getChartRepositoryUrl()))
            .call();
        Helm.repo().update();
    }

    private Path createTempKubeconfigFile(String kubeconfig) throws IOException {
        Path tempFile = Files.createTempFile("kubeconfig", ".yaml");
        Files.write(tempFile, kubeconfig.getBytes(StandardCharsets.UTF_8));
        return tempFile;
    }

    private void deleteTempFile(Path tempFile) {
        if (tempFile != null) {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException e) {
                log.warn("임시 파일 삭제 실패: {}", tempFile, e);
            }
        }
    }

    public void uninstallHelmChart(String namespace, SoftwareCatalog catalog, String clusterName) {
        Path tempConfigFile = null;
        try {
            K8sClusterDto dto = api.getK8sClusterByName(namespace, clusterName);
            String cspType = dto.getConnectionConfig().getProviderName().toUpperCase();
            String kubeconfig = dto.getCspViewK8sClusterDetail().getAccessInfo().getKubeconfig();
            
             // GCP의 경우 gke-gcloud-auth-plugin 관련 설정 제거
            if ("GCP".equalsIgnoreCase(cspType)) {
                kubeconfig = kubeconfig.replaceAll("command:\\s*gke-gcloud-auth-plugin", "")
                                    .replaceAll("apiVersion:\\s*client.authentication.k8s.io/.*\\n", "")
                                    .replaceAll("installHint:.*\\n", "")
                                    .replaceAll("provideClusterInfo:.*\\n", "");
            }
            tempConfigFile = createTempKubeconfigFile(kubeconfig);

            String result = Helm.uninstall(catalog.getHelmChart().getChartName())
                    .withKubeConfig(tempConfigFile)
                    .withNamespace(namespace)
                    .call();

            log.info("Helm Release '{}' 삭제 완료 - namespace: {}, CSP: {}", 
                    catalog.getHelmChart().getChartName(), 
                    namespace,
                    cspType);

        } catch (Exception e) {
            log.error("Helm Release 삭제 실패", e);
            throw new RuntimeException("Helm Release 삭제 실패", e);
        } finally {
            deleteTempFile(tempConfigFile);
        }
    }
}
