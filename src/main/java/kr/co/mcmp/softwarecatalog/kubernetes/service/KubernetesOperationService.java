package kr.co.mcmp.softwarecatalog.kubernetes.service;

import org.springframework.stereotype.Service;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import kr.co.mcmp.softwarecatalog.SoftwareCatalog;
import kr.co.mcmp.softwarecatalog.application.repository.OperationHistoryRepository;
import kr.co.mcmp.softwarecatalog.kubernetes.config.KubernetesClientFactory;
import kr.co.mcmp.softwarecatalog.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class KubernetesOperationService {

    private final KubernetesClientFactory clientFactory;
    private final HelmChartService helmChartService;
    private final OperationHistoryRepository operationHistoryRepository;
    private final UserRepository userRepository;

    public void restartApplication(String namespace, String clusterName, SoftwareCatalog catalog, String username) {
        try {
            String releaseName = catalog.getHelmChart().getChartName();
            
            // kubeconfig 생성
            String kubeconfigYaml = getKubeconfigForCluster(namespace, clusterName);
            java.nio.file.Path tempKubeconfigPath = createTempKubeconfigFile(kubeconfigYaml);
            
            try {
                // Helm을 사용한 롤링 재시작
                runHelmRollbackCli(releaseName, namespace, tempKubeconfigPath);
                log.info("애플리케이션 {} Helm 롤링 재시작 완료", releaseName);
            } finally {
                // 임시 kubeconfig 파일 삭제
                if (tempKubeconfigPath != null) {
                    try {
                        java.nio.file.Files.deleteIfExists(tempKubeconfigPath);
                    } catch (Exception e) {
                        log.warn("임시 kubeconfig 파일 삭제 중 오류 발생: {}", e.getMessage());
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("애플리케이션 재시작 중 오류 발생", e);
            throw new RuntimeException("애플리케이션 재시작 실패", e);
        }
    }

    public void stopApplication(String namespace, String clusterName, SoftwareCatalog catalog, String username) {
        try {
            String releaseName = catalog.getHelmChart().getChartName();
            
            // kubeconfig 생성
            String kubeconfigYaml = getKubeconfigForCluster(namespace, clusterName);
            java.nio.file.Path tempKubeconfigPath = createTempKubeconfigFile(kubeconfigYaml);
            
            try {
                // Helm을 사용한 애플리케이션 중지 (replicaCount를 0으로 설정)
                runHelmUpgradeCli(releaseName, namespace, tempKubeconfigPath, "replicaCount=0");
                log.info("애플리케이션 {} Helm 중지 완료", releaseName);
            } finally {
                // 임시 kubeconfig 파일 삭제
                if (tempKubeconfigPath != null) {
                    try {
                        java.nio.file.Files.deleteIfExists(tempKubeconfigPath);
                    } catch (Exception e) {
                        log.warn("임시 kubeconfig 파일 삭제 중 오류 발생: {}", e.getMessage());
                    }
                }
            }

        } catch (Exception e) {
            log.error("애플리케이션 중지 중 오류 발생", e);
            throw new RuntimeException("애플리케이션 중지 실패", e);
        }
    }

    public void uninstallApplication(String namespace, String clusterName, SoftwareCatalog catalog, String username) {
        try {
            helmChartService.uninstallHelmChart(namespace, catalog, clusterName);
            log.info("애플리케이션 제거 완료: {}", catalog.getHelmChart().getChartName());
        } catch (Exception e) {
            log.error("애플리케이션 제거 중 오류 발생", e);
            throw new RuntimeException("애플리케이션 제거 실패", e);
        }
    }

    private void runHelmRollbackCli(String releaseName, String namespace, java.nio.file.Path kubeconfig) throws Exception {
        java.util.List<String> cmd = new java.util.ArrayList<>();
        cmd.add("helm"); cmd.add("rollback");
        cmd.add(releaseName);
        cmd.add("--namespace"); cmd.add(namespace);
        if (kubeconfig != null) {
            cmd.add("--kubeconfig"); cmd.add(kubeconfig.toString());
        }
        cmd.add("--wait");
        
        ProcessBuilder pb = new ProcessBuilder(cmd);
        Process p = pb.start();
        int ec = p.waitFor();
        String out = new String(p.getInputStream().readAllBytes());
        String err = new String(p.getErrorStream().readAllBytes());
        if (ec != 0) {
            throw new RuntimeException("helm rollback failed (" + ec + "): " + err + "\n" + out);
        }
        log.info("helm rollback output: {}", out);
    }

    private void runHelmUpgradeCli(String releaseName, String namespace, java.nio.file.Path kubeconfig, String setValue) throws Exception {
        java.util.List<String> cmd = new java.util.ArrayList<>();
        cmd.add("helm"); cmd.add("upgrade");
        cmd.add(releaseName);
        cmd.add(releaseName); // chart reference (same as release name for existing releases)
        cmd.add("--namespace"); cmd.add(namespace);
        if (kubeconfig != null) {
            cmd.add("--kubeconfig"); cmd.add(kubeconfig.toString());
        }
        cmd.add("--set"); cmd.add(setValue);
        cmd.add("--reuse-values");
        
        ProcessBuilder pb = new ProcessBuilder(cmd);
        Process p = pb.start();
        int ec = p.waitFor();
        String out = new String(p.getInputStream().readAllBytes());
        String err = new String(p.getErrorStream().readAllBytes());
        if (ec != 0) {
            throw new RuntimeException("helm upgrade failed (" + ec + "): " + err + "\n" + out);
        }
        log.info("helm upgrade output: {}", out);
    }

    private String getKubeconfigForCluster(String namespace, String clusterName) throws Exception {
        // HelmChartService의 로직을 참조하여 kubeconfig 생성
        return helmChartService.getKubeconfigForCluster(namespace, clusterName);
    }

    private java.nio.file.Path createTempKubeconfigFile(String kubeconfigYaml) throws Exception {
        // HelmChartService의 로직을 참조하여 임시 kubeconfig 파일 생성
        return helmChartService.createTempKubeconfigFile(kubeconfigYaml);
    }

}