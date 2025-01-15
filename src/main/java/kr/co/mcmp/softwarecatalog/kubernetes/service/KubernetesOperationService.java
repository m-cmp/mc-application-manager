package kr.co.mcmp.softwarecatalog.kubernetes.service;

import org.springframework.stereotype.Service;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import kr.co.mcmp.softwarecatalog.SoftwareCatalog;
import kr.co.mcmp.softwarecatalog.application.repository.OperationHistoryRepository;
import kr.co.mcmp.softwarecatalog.kubernetes.util.KubernetesClientFactory;
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
            KubernetesClient client = clientFactory.getClient(namespace, clusterName);
            String deploymentName = catalog.getHelmChart().getChartName();
            
            // 현재 디플로이먼트의 상태 확인
            Deployment deployment = client.apps().deployments()
                    .inNamespace(namespace)
                    .withName(deploymentName)
                    .get();
            
            if (deployment == null) {
                throw new RuntimeException("디플로이먼트를 찾을 수 없습니다: " + deploymentName);
            }
            
            int currentReplicas = deployment.getSpec().getReplicas();
            
            if (currentReplicas > 0) {
                // 실행 중인 경우: 롤링 재시작 수행
                client.apps().deployments().inNamespace(namespace)
                        .withName(deploymentName)
                        .rolling()
                        .restart();
                log.info("애플리케이션 {} 롤링 재시작 완료", deploymentName);
            } else {
                // 중지된 경우: 레플리카를 1개로 설정하여 시작
                client.apps().deployments().inNamespace(namespace)
                        .withName(deploymentName)
                        .scale(1);
                log.info("애플리케이션 {} 재시작 완료 (레플리카 1개로 설정)", deploymentName);
            }
        } catch (Exception e) {
            log.error("애플리케이션 재시작 중 오류 발생", e);
            throw new RuntimeException("애플리케이션 재시작 실패", e);
        }
    }

    public void stopApplication(String namespace, String clusterName, SoftwareCatalog catalog, String username) {
        try {
            KubernetesClient client = clientFactory.getClient(namespace, clusterName);
            client.apps().deployments().inNamespace(namespace)
                    .withName(catalog.getHelmChart().getChartName())
                    .scale(0);

        } catch (Exception e) {
            log.error("애플리케이션 중지 중 오류 발생", e);
            throw new RuntimeException("애플리케이션 중지 실패", e);
        }
    }

    public void uninstallApplication(String namespace, String clusterName, SoftwareCatalog catalog, String username) {
        try {
            helmChartService.uninstallHelmChart(namespace, catalog,clusterName);
        } catch (Exception e) {
            log.error("애플리케이션 제거 중 오류 발생", e);
            throw new RuntimeException("애플리케이션 제거 실패", e);
        }
    }

}