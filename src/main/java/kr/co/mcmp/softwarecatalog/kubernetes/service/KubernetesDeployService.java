package kr.co.mcmp.softwarecatalog.kubernetes.service;

import java.time.LocalDateTime;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.marcnuri.helm.Release;

import io.fabric8.kubernetes.client.KubernetesClient;
import kr.co.mcmp.softwarecatalog.SoftwareCatalog;
import kr.co.mcmp.softwarecatalog.application.constants.ActionType;
import kr.co.mcmp.softwarecatalog.application.constants.DeploymentType;
import kr.co.mcmp.softwarecatalog.application.model.DeploymentHistory;
import kr.co.mcmp.softwarecatalog.kubernetes.util.KubernetesClientFactory;
import kr.co.mcmp.softwarecatalog.kubernetes.util.KubernetesUtils;
import kr.co.mcmp.softwarecatalog.users.Entity.User;
import kr.co.mcmp.softwarecatalog.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class KubernetesDeployService {
    private final KubernetesClientFactory clientFactory;
    private final KubernetesNamespaceService namespaceService;
    private final HelmChartService helmChartService;
    private final UserRepository userRepository;

    public DeploymentHistory deployApplication(String namespace, String clusterName, SoftwareCatalog catalog,
            String username) {
        try (KubernetesClient client = clientFactory.getClient(namespace, clusterName)) {
            // KubernetesClient client = clientFactory.getClient(namespace, clusterName);
            namespaceService.ensureNamespaceExists(client, namespace);

            Release result = helmChartService.deployHelmChart(client, namespace, catalog, clusterName);

            String podStatus = KubernetesUtils.getPodStatus(client, namespace, catalog.getHelmChart().getChartName());
            Integer servicePort = KubernetesUtils.getServicePort(client, namespace,
                    catalog.getHelmChart().getChartName());

            return createDeploymentHistory(
                    namespace,
                    clusterName,
                    catalog,
                    username,
                    ActionType.INSTALL,
                    podStatus,
                    servicePort,
                    "SUCCESS");
        } catch (Exception e) {
            log.error("애플리케이션 배포 중 오류 발생", e);
            return createDeploymentHistory(
                    namespace,
                    clusterName,
                    catalog,
                    username,
                    ActionType.INSTALL,
                    "Failed",
                    null,
                    "FAILED");
        }
    }

    public DeploymentHistory stopApplication(String namespace, String clusterName, SoftwareCatalog catalog,
            String username) {
        try {
            KubernetesClient client = clientFactory.getClient(namespace, clusterName);

            helmChartService.uninstallHelmChart(namespace, catalog, clusterName);

            String podStatus = KubernetesUtils.getPodStatus(client, namespace, catalog.getHelmChart().getChartName());
            Integer servicePort = KubernetesUtils.getServicePort(client, namespace,
                    catalog.getHelmChart().getChartName());

            return createDeploymentHistory(
                    namespace,
                    clusterName,
                    catalog,
                    username,
                    ActionType.STOP,
                    podStatus,
                    servicePort, "STOP");
        } catch (Exception e) {
            log.error("애플리케이션 중지 중 오류 발생", e);
            throw new RuntimeException("애플리케이션 중지 실패", e);
        }
    }

    private DeploymentHistory createDeploymentHistory(
            String namespace, String clusterName, SoftwareCatalog catalog, String username, ActionType actionType,
            String podStatus, Integer servicePort, String status) {

        User user = StringUtils.isNotBlank(username) ? userRepository.findByUsername(username).orElse(null) : null;

        return DeploymentHistory.builder()
                .namespace(namespace)
                .clusterName(clusterName)
                .catalog(catalog)
                .executedBy(user)
                .podStatus(podStatus)
                .servicePort(servicePort)
                .deploymentType(DeploymentType.K8S)
                .status(status)
                .actionType(actionType)
                .executedAt(LocalDateTime.now())
                .build();
    }
}