package kr.co.mcmp.softwarecatalog.kubernetes.service;

import java.net.URI;

import org.springframework.stereotype.Service;

import com.marcnuri.helm.Helm;
import com.marcnuri.helm.InstallCommand;
import com.marcnuri.helm.Release;

import kr.co.mcmp.softwarecatalog.SoftwareCatalog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class HelmChartService {

    public Release deployHelmChart(String namespace, SoftwareCatalog catalog) {
        try {
            addHelmRepository(catalog);
            
            // Helm 설치 명령어 구성
            InstallCommand installCommand = Helm.install(
                catalog.getHelmChart().getRepositoryName() + "/" + catalog.getHelmChart().getChartName())
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
        Helm.repo().add()
            .withName(catalog.getHelmChart().getRepositoryName())
            .withUrl(URI.create(catalog.getHelmChart().getChartRepositoryUrl()))
            .call();
        Helm.repo().update();
    }
}