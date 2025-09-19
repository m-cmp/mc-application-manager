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
import kr.co.mcmp.softwarecatalog.kubernetes.config.KubernetesClientFactory;
import kr.co.mcmp.softwarecatalog.kubernetes.util.KubernetesUtils;
import kr.co.mcmp.softwarecatalog.users.Entity.User;
import kr.co.mcmp.softwarecatalog.application.repository.DeploymentHistoryRepository;
import kr.co.mcmp.softwarecatalog.users.repository.UserRepository;
import kr.co.mcmp.softwarecatalog.service.SoftwareSourceService;
import kr.co.mcmp.softwarecatalog.application.model.HelmChart;
import kr.co.mcmp.softwarecatalog.application.model.PackageInfo;
import kr.co.mcmp.ape.cbtumblebug.api.CbtumblebugRestApi;
import kr.co.mcmp.softwarecatalog.kubernetes.config.KubeConfigProviderFactory;
import kr.co.mcmp.ape.cbtumblebug.dto.K8sClusterDto;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@RequiredArgsConstructor
public class KubernetesDeployService {
    
    private static final Logger log = LoggerFactory.getLogger(KubernetesDeployService.class);
    private final KubernetesClientFactory clientFactory;
    private final KubernetesNamespaceService namespaceService;
    private final HelmChartService helmChartService;
    private final UserRepository userRepository;
    private final DeploymentHistoryRepository deploymentHistoryRepository;
    private final SoftwareSourceService softwareSourceService;
    private final CbtumblebugRestApi cbtumblebugRestApi;
    private final KubeConfigProviderFactory providerFactory;

    /**
     * 입력 파라미터 검증 공통 메서드
     */
    private void validateInputParameters(String namespace, String clusterName, SoftwareCatalog catalog) {
        if (namespace == null || namespace.trim().isEmpty()) {
            throw new IllegalArgumentException("Namespace cannot be null or empty");
        }
        if (clusterName == null || clusterName.trim().isEmpty()) {
            throw new IllegalArgumentException("Cluster name cannot be null or empty");
        }
        if (catalog == null) {
            throw new IllegalArgumentException("SoftwareCatalog cannot be null");
        }
    }

    /**
     * Helm Chart 정보 검증
     */
    private void validateHelmChart(SoftwareCatalog catalog) {
        if (catalog.getHelmChart() == null) {
            throw new IllegalStateException("Helm Chart information is missing for catalog: " + catalog.getId());
        }
    }

    /**
     * kubeconfig YAML을 가져오는 공통 메서드
     */
    private String getKubeconfigYaml(String namespace, String clusterName) {
        try {
            K8sClusterDto clusterDto = cbtumblebugRestApi.getK8sClusterByName(namespace, clusterName);
            return providerFactory.getProvider(clusterDto.getConnectionConfig().getProviderName())
                    .getOriginalKubeconfigYaml(clusterDto);
        } catch (Exception e) {
            log.error("kubeconfig 획득 중 오류 발생: {}", e.getMessage(), e);
            throw new RuntimeException("kubeconfig 획득 실패", e);
        }
    }

    public DeploymentHistory deployApplication(String namespace, String clusterName, SoftwareCatalog catalog,
            String username, kr.co.mcmp.softwarecatalog.application.dto.DeploymentRequest request) {
        // 입력 파라미터 검증
        validateInputParameters(namespace, clusterName, catalog);
        
        // 소스 선택 로직 추가
        HelmChart helmChart = softwareSourceService.getArtifactHubSource(catalog.getId())
                .orElseThrow(() -> new IllegalStateException("No ArtifactHub source found for catalog: " + catalog.getId()));
        
        log.info("선택된 Helm Chart: {} (버전: {})", helmChart.getChartName(), helmChart.getChartVersion());
        
        try (KubernetesClient client = clientFactory.getClient(namespace, clusterName)) {
            namespaceService.ensureNamespaceExists(client, namespace);

            Release result;
            if (request != null) {
                result = helmChartService.deployHelmChart(client, namespace, catalog, helmChart, clusterName, request);
            } else {
                result = helmChartService.deployHelmChart(client, namespace, catalog, helmChart, clusterName);
            }

            String podStatus = KubernetesUtils.getPodStatus(client, namespace, helmChart.getChartName());
            Integer servicePort = KubernetesUtils.getServicePort(client, namespace, helmChart.getChartName());

            // 릴리스 이름을 배포 히스토리에 저장
            String releaseName = result != null ? result.getName() : null;
            log.info("배포된 릴리스 이름: {}", releaseName);

            return createDeploymentHistory(
                    namespace,
                    clusterName,
                    catalog,
                    username,
                    ActionType.INSTALL,
                    podStatus,
                    servicePort,
                    "SUCCESS",
                    releaseName);
        } catch (Exception e) {
            log.error("애플리케이션 배포 중 오류 발생", e);
            throw new RuntimeException("애플리케이션 배포 실패", e);
        }
    }

    public DeploymentHistory stopApplication(String namespace, String clusterName, SoftwareCatalog catalog,
            String username) {
        // 입력 파라미터 검증
        validateInputParameters(namespace, clusterName, catalog);
        validateHelmChart(catalog);
        
        try (KubernetesClient client = clientFactory.getClient(namespace, clusterName)) {
            // 배포 히스토리에서 릴리스 이름 조회
            String releaseName = getReleaseNameFromHistory(catalog.getId(), clusterName, namespace);
            if (releaseName == null) {
                log.warn("배포 히스토리에서 릴리스 이름을 찾을 수 없습니다. 차트 이름으로 시도합니다.");
                releaseName = catalog.getHelmChart().getChartName();
            }

            // Pod 개수를 0으로 설정 (Scale Down)
            log.info("애플리케이션 중지 중 - Pod 개수를 0으로 설정: {}", releaseName);
            KubernetesUtils.scaleDeployment(client, namespace, releaseName, 0);

            String podStatus = "STOPPED";
            Integer servicePort = KubernetesUtils.getServicePort(client, namespace, catalog.getHelmChart().getChartName());

            return createDeploymentHistory(
                    namespace,
                    clusterName,
                    catalog,
                    username,
                    ActionType.STOP,
                    podStatus,
                    servicePort, 
                    "STOPPED",
                    releaseName);
        } catch (Exception e) {
            log.error("애플리케이션 중지 중 오류 발생", e);
            throw new RuntimeException("애플리케이션 중지 실패", e);
        }
    }

    private DeploymentHistory createDeploymentHistory(
            String namespace, String clusterName, SoftwareCatalog catalog, String username, ActionType actionType,
            String podStatus, Integer servicePort, String status) {
        return createDeploymentHistory(namespace, clusterName, catalog, username, actionType, podStatus, servicePort, status, null);
    }

    private DeploymentHistory createDeploymentHistory(
            String namespace, String clusterName, SoftwareCatalog catalog, String username, ActionType actionType,
            String podStatus, Integer servicePort, String status, String releaseName) {

        User user = StringUtils.isNotBlank(username) ? userRepository.findByUsername(username).orElse(null) : null;

        log.info("=== DeploymentHistory 생성 ===");
        log.info("namespace: {}, clusterName: {}, catalogId: {}", namespace, clusterName, catalog != null ? catalog.getId() : "null");
        log.info("actionType: {}, status: {}, deploymentType: {}", actionType, status, DeploymentType.K8S);
        log.info("podStatus: {}, servicePort: {}, releaseName: {}", podStatus, servicePort, releaseName);

        DeploymentHistory history = new DeploymentHistory();
        history.setNamespace(namespace);
        history.setClusterName(clusterName);
        history.setCatalog(catalog);
        history.setExecutedBy(user);
        history.setPodStatus(podStatus);
        history.setServicePort(servicePort);
        history.setDeploymentType(DeploymentType.K8S);
        history.setStatus(status);
        history.setActionType(actionType);
        history.setExecutedAt(LocalDateTime.now());
        history.setReleaseName(releaseName);
        return history;
    }
    
    /**
     * 배포 히스토리에서 릴리스 이름을 조회합니다.
     */
    private String getReleaseNameFromHistory(Long catalogId, String clusterName, String namespace) {
        try {
            DeploymentHistory latestDeployment = deploymentHistoryRepository
                    .findTopByCatalogIdAndClusterNameAndNamespaceAndActionTypeOrderByExecutedAtDesc(
                            catalogId, clusterName, namespace, ActionType.INSTALL);
            
            if (latestDeployment != null && latestDeployment.getReleaseName() != null) {
                log.info("배포 히스토리에서 릴리스 이름 조회: {}", latestDeployment.getReleaseName());
                return latestDeployment.getReleaseName();
            }
        } catch (Exception e) {
            log.error("배포 히스토리에서 릴리스 이름 조회 중 오류 발생: {}", e.getMessage());
        }
        
        return null;
    }
    
    /**
     * 애플리케이션을 재시작합니다.
     */
    public DeploymentHistory restartApplication(String namespace, String clusterName, SoftwareCatalog catalog, String username) {
        // 입력 파라미터 검증
        validateInputParameters(namespace, clusterName, catalog);
        validateHelmChart(catalog);
        
        try (KubernetesClient client = clientFactory.getClient(namespace, clusterName)) {
            // 배포 히스토리에서 릴리스 이름 조회
            String releaseName = getReleaseNameFromHistory(catalog.getId(), clusterName, namespace);
            if (releaseName == null) {
                log.warn("배포 히스토리에서 릴리스 이름을 찾을 수 없습니다. 차트 이름으로 시도합니다.");
                releaseName = catalog.getHelmChart().getChartName();
            }

            // Pod 개수를 원래 개수로 복원 (Scale Up)
            log.info("애플리케이션 재시작 중 - Pod 개수를 원래 개수로 복원: {}", releaseName);
            KubernetesUtils.scaleDeployment(client, namespace, releaseName, catalog.getMinReplicas());

            String podStatus = KubernetesUtils.getPodStatus(client, namespace, catalog.getHelmChart().getChartName());
            Integer servicePort = KubernetesUtils.getServicePort(client, namespace, catalog.getHelmChart().getChartName());

            return createDeploymentHistory(
                    namespace,
                    clusterName,
                    catalog,
                    username,
                    ActionType.RESTART,
                    podStatus,
                    servicePort,
                    "RESTARTED",
                    releaseName);
        } catch (Exception e) {
            log.error("애플리케이션 재시작 중 오류 발생", e);
            throw new RuntimeException("애플리케이션 재시작 실패", e);
        }
    }
    
    /**
     * 애플리케이션을 제거합니다.
     */
    public DeploymentHistory uninstallApplication(String namespace, String clusterName, SoftwareCatalog catalog, String username) {
        // 입력 파라미터 검증
        validateInputParameters(namespace, clusterName, catalog);
        validateHelmChart(catalog);
        
        try (KubernetesClient client = clientFactory.getClient(namespace, clusterName)) {
            // kubeconfig를 생성하여 HelmChartService에 전달
            String kubeconfigYaml = getKubeconfigYaml(namespace, clusterName);
            helmChartService.uninstallHelmChartWithKubeconfig(namespace, catalog, clusterName, kubeconfigYaml);
            
            String podStatus = "UNINSTALLED";
            Integer servicePort = null;
            
            return createDeploymentHistory(
                    namespace,
                    clusterName,
                    catalog,
                    username,
                    ActionType.UNINSTALL,
                    podStatus,
                    servicePort,
                    "SUCCESS",
                    null);
        } catch (Exception e) {
            log.error("애플리케이션 제거 중 오류 발생", e);
            throw new RuntimeException("애플리케이션 제거 실패", e);
        }
    }
    
}