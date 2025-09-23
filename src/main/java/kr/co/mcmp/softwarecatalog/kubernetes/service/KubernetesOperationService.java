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
            // kubeconfig 생성
            String kubeconfigYaml = getKubeconfigForCluster(namespace, clusterName);
            java.nio.file.Path tempKubeconfigPath = createTempKubeconfigFile(kubeconfigYaml);
            
            try {
                // 실제 설치된 릴리스 이름 찾기
                String releaseName = findInstalledReleaseName(namespace, tempKubeconfigPath, catalog);
                if (releaseName == null) {
                    throw new RuntimeException("설치된 릴리스를 찾을 수 없습니다");
                }
                
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
            // kubeconfig 생성
            String kubeconfigYaml = getKubeconfigForCluster(namespace, clusterName);
            java.nio.file.Path tempKubeconfigPath = createTempKubeconfigFile(kubeconfigYaml);
            
            try {
                // 실제 설치된 릴리스 이름 찾기
                String releaseName = findInstalledReleaseName(namespace, tempKubeconfigPath, catalog);
                if (releaseName == null) {
                    throw new RuntimeException("설치된 릴리스를 찾을 수 없습니다");
                }
                
                String chartName = catalog.getHelmChart().getChartName();
                String repositoryName = catalog.getHelmChart().getRepositoryName();
                
                // Helm을 사용한 애플리케이션 중지 (replicaCount를 0으로 설정)
                runHelmUpgradeCli(releaseName, repositoryName + "/" + chartName, namespace, tempKubeconfigPath, "replicaCount=0");
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
            // kubeconfig 생성
            String kubeconfigYaml = getKubeconfigForCluster(namespace, clusterName);
            java.nio.file.Path tempKubeconfigPath = createTempKubeconfigFile(kubeconfigYaml);
            
            try {
                // 실제 설치된 릴리스 이름 찾기
                String releaseName = findInstalledReleaseName(namespace, tempKubeconfigPath, catalog);
                if (releaseName == null) {
                    // 릴리스를 찾을 수 없으면 차트 이름으로 시도
                    releaseName = catalog.getHelmChart().getChartName();
                    log.warn("설치된 릴리스를 찾을 수 없어 차트 이름으로 시도: {}", releaseName);
                }
                
                // Helm CLI로 직접 uninstall 실행
                runHelmUninstallCli(releaseName, namespace, tempKubeconfigPath);
                log.info("애플리케이션 제거 완료: {}", releaseName);
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

    private void runHelmUpgradeCli(String releaseName, String chartRef, String namespace, java.nio.file.Path kubeconfig, String setValue) throws Exception {
        java.util.List<String> cmd = new java.util.ArrayList<>();
        cmd.add("helm"); cmd.add("upgrade");
        cmd.add(releaseName);
        cmd.add(chartRef); // chart reference (repo_name/chart_name)
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
    
    private String findInstalledReleaseName(String namespace, java.nio.file.Path kubeconfig, SoftwareCatalog catalog) throws Exception {
        // helm list 명령어로 설치된 릴리스 목록 조회
        java.util.List<String> cmd = new java.util.ArrayList<>();
        cmd.add("helm"); cmd.add("list");
        cmd.add("--namespace"); cmd.add(namespace);
        cmd.add("--output"); cmd.add("json");
        if (kubeconfig != null) {
            cmd.add("--kubeconfig"); cmd.add(kubeconfig.toString());
        }
        
        ProcessBuilder pb = new ProcessBuilder(cmd);
        Process p = pb.start();
        int ec = p.waitFor();
        String out = new String(p.getInputStream().readAllBytes());
        String err = new String(p.getErrorStream().readAllBytes());
        
        if (ec != 0) {
            log.warn("helm list failed: {}", err);
            return null;
        }
        
        // JSON 파싱하여 릴리스 이름 찾기
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode releases = mapper.readTree(out);
            
            if (releases.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode release : releases) {
                    String releaseName = release.get("name").asText();
                    String chartName = release.get("chart").asText();
                    
                    // 차트 이름이 일치하는 릴리스 찾기
                    if (chartName.contains(catalog.getHelmChart().getChartName())) {
                        log.info("찾은 릴리스: {} (차트: {})", releaseName, chartName);
                        return releaseName;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("릴리스 목록 파싱 중 오류: {}", e.getMessage());
        }
        
        // JSON 파싱 실패 시 텍스트로 검색
        String[] lines = out.split("\n");
        for (String line : lines) {
            if (line.contains(catalog.getHelmChart().getChartName())) {
                String[] parts = line.split("\\s+");
                if (parts.length > 0) {
                    log.info("텍스트에서 찾은 릴리스: {}", parts[0]);
                    return parts[0];
                }
            }
        }
        
        log.warn("릴리스를 찾을 수 없습니다. 차트명: {}", catalog.getHelmChart().getChartName());
        return null;
    }

    private java.nio.file.Path createTempKubeconfigFile(String kubeconfigYaml) throws Exception {
        // HelmChartService의 로직을 참조하여 임시 kubeconfig 파일 생성
        return helmChartService.createTempKubeconfigFile(kubeconfigYaml);
    }
    
    private void runHelmUninstallCli(String releaseName, String namespace, java.nio.file.Path kubeconfig) throws Exception {
        java.util.List<String> cmd = new java.util.ArrayList<>();
        cmd.add("helm"); cmd.add("uninstall");
        cmd.add(releaseName);
        cmd.add("--namespace"); cmd.add(namespace);
        if (kubeconfig != null) {
            cmd.add("--kubeconfig"); cmd.add(kubeconfig.toString());
        }
        
        ProcessBuilder pb = new ProcessBuilder(cmd);
        Process p = pb.start();
        int ec = p.waitFor();
        String out = new String(p.getInputStream().readAllBytes());
        String err = new String(p.getErrorStream().readAllBytes());
        if (ec != 0) {
            throw new RuntimeException("helm uninstall failed (" + ec + "): " + err + "\n" + out);
        }
        log.info("helm uninstall output: {}", out);
    }

}