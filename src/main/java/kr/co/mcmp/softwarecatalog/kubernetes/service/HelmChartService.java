package kr.co.mcmp.softwarecatalog.kubernetes.service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import kr.co.mcmp.softwarecatalog.application.model.HelmChart;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import com.marcnuri.helm.Release;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import kr.co.mcmp.ape.cbtumblebug.api.CbtumblebugRestApi;
import kr.co.mcmp.ape.cbtumblebug.dto.K8sClusterDto;
import kr.co.mcmp.softwarecatalog.CatalogRepository;
import kr.co.mcmp.softwarecatalog.SoftwareCatalog;
import kr.co.mcmp.softwarecatalog.kubernetes.config.KubeconfigResolver;
import kr.co.mcmp.softwarecatalog.kubernetes.util.ReleaseNameGenerator;
import kr.co.mcmp.softwarecatalog.application.dto.DeploymentConfigDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class HelmChartService {

    private static final String DEFAULT_HELM_NAMESPACE = "default";
    private static final String METRICS_SERVER_NAMESPACE = "kube-system";
    private static final String METRICS_SERVER_RELEASE = "metrics-server";
    private static final String METRICS_SERVER_REPOSITORY = "metrics-server";
    private static final String METRICS_SERVER_REPOSITORY_URL = "https://kubernetes-sigs.github.io/metrics-server/";
    private static final String METRICS_SERVER_CHART = "metrics-server/metrics-server";
    private static final String METRICS_SERVER_CHART_VERSION = "3.13.1";
    private static final String INGRESS_NGINX_REPOSITORY = "ingress-nginx";
    private static final String INGRESS_NGINX_REPOSITORY_URL = "https://kubernetes.github.io/ingress-nginx";
    private static final String INGRESS_NGINX_CHART = "ingress-nginx/ingress-nginx";
    private static final String INGRESS_NGINX_CHART_VERSION = "4.14.0";
    private static final String HELM_WAIT_TIMEOUT = "10m";

    private final CbtumblebugRestApi cbtumblebugRestApi;
    private final KubeconfigResolver kubeconfigResolver;
    private final ReleaseNameGenerator releaseNameGenerator;
    private final CatalogRepository catalogRepository;
    private final KubernetesStorageClassService kubernetesStorageClassService;

    public Release deployHelmChart(KubernetesClient client, String namespace, SoftwareCatalog catalog, String clusterName) {
        return deployHelmChart(client, namespace, catalog, catalog.getHelmChart(), clusterName);
    }
    
    public Release deployHelmChart(KubernetesClient client, String namespace, SoftwareCatalog catalog, 
                                 kr.co.mcmp.softwarecatalog.application.model.HelmChart helmChart, String clusterName,
                                 kr.co.mcmp.softwarecatalog.application.dto.DeploymentRequest request) {
        return deployHelmChartWithRequest(client, namespace, catalog, helmChart, clusterName, request);
    }
    
    public Release deployHelmChart(KubernetesClient client, String namespace, SoftwareCatalog catalog, 
                                 kr.co.mcmp.softwarecatalog.application.model.HelmChart helmChart, String clusterName) {
        
        Path tempKubeconfigPath = null;

        try {
            // 1. 클러스터 정보 조회
            K8sClusterDto clusterDto = cbtumblebugRestApi.getK8sClusterByName(namespace, clusterName);
            String kubeconfigYaml = kubeconfigResolver.getKubeconfigYaml(namespace, clusterName);

            // 2. Kubeconfig YAML 생성

            // 3. 임시 kubeconfig 파일 생성
            tempKubeconfigPath = createTempKubeconfigFile(kubeconfigYaml);

            // 4. 릴리스 이름 생성
            String releaseName = releaseNameGenerator.generateReleaseName(helmChart.getChartName());

            // 5. 기존 릴리스 확인 (현재 배포와 관련된 것만)
            try {
                // 현재 카탈로그와 관련된 기존 릴리스만 확인
                String existingReleaseName = getReleaseNameFromHistory(catalog.getId(), clusterName, namespace);
                if (existingReleaseName != null) {
                    try {
                        // 기존 릴리스 상태 확인 및 삭제
                        boolean found = runHelmListAndCheckRelease(existingReleaseName, namespace, tempKubeconfigPath);
                        
                        if (found) {
                            // 기존 릴리스 삭제
                            runHelmUninstallCli(existingReleaseName, namespace, tempKubeconfigPath);
                        }
                    } catch (Exception e) {
                        log.warn("기존 릴리스 삭제 중 오류 발생: {}", e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.warn("기존 릴리스 확인 중 오류 발생. 새로 설치를 진행합니다. 오류: {}", e.getMessage());
            }

            // 6. Helm Repository 추가
            String repositoryName = helmChart.getRepositoryName();
            String chartRepositoryUrl = helmChart.getChartRepositoryUrl();
            log.info("7. Helm 명령어 생성 중... Chart: {}, Version: {}", helmChart.getChartName(), helmChart.getChartVersion());

            try {
                // Helm CLI로 repository 추가
                runHelmRepoAddCli(repositoryName, chartRepositoryUrl);
                log.info("Helm Repository 추가 완료: {}", repositoryName);
            } catch (Exception e) {
                log.warn("Helm Repository 추가 중 오류 발생 (이미 존재할 수 있음): {}", e.getMessage());
            }

            // 7. 새 릴리스 설치 - CLI 방식으로 변경
            log.info("8. 새 릴리스 설치 실행 중...");
            String chartRef = repositoryName + "/" + helmChart.getChartName();
            
            // 이미지 설정 - Chart별로 다르게 처리
            String imageRepository = buildImageRepository(catalog, helmChart);
            
            // Values 맵 구성
            java.util.Map<String, String> values = new java.util.HashMap<>();
            values.put("replicaCount", String.valueOf(catalog.getMinReplicas()));
            values.put("service.port", String.valueOf(catalog.getDefaultPort()));
            values.put("service.type", "ClusterIP");
            values.put("resources.requests.cpu", catalog.getMinCpu().toString());
            putHelmValueIfPresent(values, "resources.requests.memory", formatMemoryMi(catalog.getMinMemory()));
            values.put("resources.limits.cpu", catalog.getRecommendedCpu().toString());
            putHelmValueIfPresent(values, "resources.limits.memory", formatMemoryMi(catalog.getRecommendedMemory()));
            values.put("persistence.enabled", "false");
            values.put("securityContext.runAsNonRoot", "false");
            values.put("containerSecurityContext.allowPrivilegeEscalation", "false");
            values.put("global.security.allowInsecureImages", "true");
            boolean lokiChart = helmChart.getChartName().equalsIgnoreCase("loki");
            if (!lokiChart) {
                values.put("global.imageRegistry", "docker.io");
            }

            if (helmChart.getChartName().equalsIgnoreCase("grafana")) {
                values.put("image.repository", "grafana/grafana");
                values.put("image.tag", "latest");
                values.put("image.pullPolicy", "IfNotPresent");
            } else if (!lokiChart) {
                values.put("image.repository", imageRepository);
                values.put("image.tag", "latest");
                values.put("image.pullPolicy", "IfNotPresent");
                values.put("image.registry", "docker.io");
            }

            // Ingress 설정 적용
            if (catalog.getIngressEnabled() != null && catalog.getIngressEnabled()) {
                log.info("Ingress 설정 적용 중...");
                values.put("ingress.enabled", "true");
                values.put("ingress.host", catalog.getIngressHost() != null ? catalog.getIngressHost() : "localhost");
                values.put("ingress.path", catalog.getIngressPath() != null ? catalog.getIngressPath() : "/");
                values.put("ingress.className", catalog.getIngressClass() != null ? catalog.getIngressClass() : "nginx");
                
                if (catalog.getIngressTlsEnabled() != null && catalog.getIngressTlsEnabled()) {
                    values.put("ingress.tls.enabled", "true");
                    values.put("ingress.tls.secretName", catalog.getIngressTlsSecret() != null ? 
                        catalog.getIngressTlsSecret() : releaseName + "-tls");
                }
                
                log.info("Ingress 설정 완료 - Host: {}, Path: {}, Class: {}", 
                        catalog.getIngressHost(), catalog.getIngressPath(), catalog.getIngressClass());
            }

            // CSP별 및 설정에 따른 동적 설정 적용
            log.info("CSP별 및 설정에 따른 동적 설정 적용 중...");
            try {
                String providerName = clusterDto.getConnectionConfig().getProviderName();
                log.info("Provider: {}, HPA Enabled: {}", providerName, catalog.getHpaEnabled());

                // HPA 설정 적용
                if (catalog.getHpaEnabled()) {
                    log.info("HPA 활성화 설정 적용 중...");
                    values.put("autoscaling.enabled", "true");
                    values.put("autoscaling.minReplicas", String.valueOf(catalog.getMinReplicas()));
                    values.put("autoscaling.maxReplicas", String.valueOf(catalog.getMaxReplicas()));
                    values.put("autoscaling.targetCPUUtilizationPercentage", String.valueOf(catalog.getCpuThreshold().intValue()));
                    values.put("autoscaling.targetMemoryUtilizationPercentage", String.valueOf(catalog.getMemoryThreshold().intValue()));
                }

                // CSP별 설정 적용
                switch (providerName.toUpperCase()) {
                    case "AWS":
                        log.info("AWS 특화 설정 적용 중...");
                        values.put("service.type", "LoadBalancer");
                        values.put("persistence.enabled", "true");
                        values.put("persistence.storageClass", "gp2");
                        values.put("persistence.size", "20Gi");
                        values.put("persistence.accessMode", "ReadWriteOnce");
                        break;
                    case "AZURE":
                        log.info("Azure 특화 설정 적용 중...");
                        values.put("service.type", "LoadBalancer");
                        values.put("persistence.enabled", "true");
                        values.put("persistence.storageClass", "managed-csi");
                        values.put("persistence.size", "20Gi");
                        values.put("persistence.accessMode", "ReadWriteOnce");
                        break;
                    case "GCP":
                        log.info("GCP 특화 설정 적용 중...");
                        values.put("service.type", "LoadBalancer");
                        values.put("persistence.enabled", "true");
                        values.put("persistence.storageClass", "standard-rwo");
                        values.put("persistence.size", "20Gi");
                        values.put("persistence.accessMode", "ReadWriteOnce");
                        break;
                    default:
                        log.info("기본 설정 적용 중...");
                        values.put("service.type", "ClusterIP");
                        values.put("persistence.enabled", "false");
                        break;
                }

                // 공통 보안 설정 적용
                log.info("공통 보안 설정 적용 중...");
                values.put("podSecurityPolicy.enabled", "false");
                values.put("rbac.create", "false");
                values.put("serviceAccount.create", "false");
                values.put("serviceAccount.name", "default");

            } catch (Exception e) {
                log.warn("CSP별 설정 적용 중 오류 발생: {}", e.getMessage());
            }

            // Helm CLI로 설치 실행
            if (isRcloneChart(helmChart)) {
                applyRcloneGuiDefaults(values, catalog.getDefaultPort());
            }

            runHelmInstallCli(releaseName, chartRef, namespace, helmChart.getChartVersion(), tempKubeconfigPath, values);
            
            // 간단한 Release 스텁 반환 - null 반환으로 변경
            Release result = null;
            
            log.info("Helm Chart '{}' 설치 완료 (HPA: {})", releaseName, catalog.getHpaEnabled());
            log.info("=== Helm Chart 배포 성공 ===");
            log.info("릴리스명: {}, 상태: {}, 네임스페이스: {}", releaseName, "deployed", namespace);

            return result;

        } catch (Exception e) {
            log.error("=== Helm Chart 배포 실패 ===");
            log.error("오류: {}", e.getMessage(), e);
            throw new RuntimeException("Helm Chart 배포 실패", e);
        } finally {
            // 8. 임시 kubeconfig 파일 삭제
            if (tempKubeconfigPath != null) {
                try {
                    log.info("8. 임시 kubeconfig 파일 삭제 중...");
                    Files.deleteIfExists(tempKubeconfigPath);
                    log.info("임시 kubeconfig 파일 삭제 완료");
                } catch (IOException e) {
                    log.warn("임시 kubeconfig 파일 삭제 중 오류 발생: {}", e.getMessage());
                }
            }
        }
    }
    
    /**
     * DeploymentRequest를 사용하여 Helm Chart를 배포합니다.
     */
    public Release deployHelmChartWithRequest(KubernetesClient client, String namespace, SoftwareCatalog catalog, 
                                            kr.co.mcmp.softwarecatalog.application.model.HelmChart helmChart, String clusterName,
                                            kr.co.mcmp.softwarecatalog.application.dto.DeploymentRequest request) {
        log.info("=== Helm Chart 배포 시작 (Request 기반) ===");
        log.info("Chart: {}, Namespace: {}, Cluster: {}", 
                helmChart.getChartName(), namespace, clusterName);
        
        Path tempKubeconfigPath = null;
        Path tempObjectStorageValuesPath = null;

        try {
            // 1. 클러스터 정보 조회
            K8sClusterDto clusterDto = cbtumblebugRestApi.getK8sClusterByName(namespace, clusterName);
            if (clusterDto == null) {
                throw new RuntimeException("K8s cluster not found: " + clusterName);
            }

            // 2. kubeconfig 파일 생성
            String providerName = clusterDto.getConnectionConfig().getProviderName();
            String kubeconfigYaml = kubeconfigResolver.getKubeconfigYaml(namespace, clusterName);
            
            tempKubeconfigPath = Files.createTempFile("kubeconfig-", ".yaml");
            Files.write(tempKubeconfigPath, kubeconfigYaml.getBytes());

            // 3. Helm repository 추가
            addHelmRepository(helmChart);

            // 4. 릴리스 이름 생성
            String releaseName = releaseNameGenerator.generateReleaseName(helmChart.getChartName());

            // 5. 기존 릴리스 확인 및 제거
            handleExistingRelease(releaseName, namespace, tempKubeconfigPath, catalog.getId(), clusterName);

            // 6. 배포 설정 DTO 생성 (우선순위: Request > Catalog > Default)
            DeploymentConfigDTO config = DeploymentConfigDTO.from(request, catalog);
            log.info("배포 설정 생성 완료 - {}", config);

            // 7. Helm Chart 설치 - CLI 방식으로 변경
            String chartRef = helmChart.getRepositoryName() + "/" + helmChart.getChartName();
            
            // 이미지 설정 - Chart별로 다르게 처리
            String imageRepository = buildImageRepository(catalog, helmChart);
            
            // Values 맵 구성
            java.util.Map<String, String> values = new java.util.HashMap<>();
            java.util.Map<String, Object> objectStorageValues = new java.util.HashMap<>();
            values.put("replicaCount", String.valueOf(config.getMinReplicas()));
            values.put("service.port", String.valueOf(config.getServicePort()));
            values.put("service.type", "ClusterIP");
            values.put("resources.requests.cpu", catalog.getMinCpu().toString());
            putHelmValueIfPresent(values, "resources.requests.memory", formatMemoryMi(catalog.getMinMemory()));
            values.put("resources.limits.cpu", catalog.getRecommendedCpu().toString());
            putHelmValueIfPresent(values, "resources.limits.memory", formatMemoryMi(catalog.getRecommendedMemory()));
            values.put("persistence.enabled", "false");
            values.put("securityContext.runAsNonRoot", "false");
            values.put("containerSecurityContext.allowPrivilegeEscalation", "false");
            values.put("global.security.allowInsecureImages", "true");
            boolean lokiChart = helmChart.getChartName().equalsIgnoreCase("loki");
            if (!lokiChart) {
                values.put("global.imageRegistry", "docker.io");
            }

            if (helmChart.getChartName().equalsIgnoreCase("grafana")) {
                values.put("image.repository", "grafana/grafana");
                values.put("image.tag", "latest");
                values.put("image.pullPolicy", "IfNotPresent");
            } else if (!lokiChart) {
                values.put("image.repository", imageRepository);
                values.put("image.tag", "latest");
                values.put("image.pullPolicy", "IfNotPresent");
                values.put("image.registry", "docker.io");
            }
            // HPA 설정 적용
            if (config.isHpaEnabled()) {
                log.info("HPA 설정 적용 중...");
                values.put("autoscaling.enabled", "true");
                values.put("autoscaling.minReplicas", String.valueOf(config.getMinReplicas()));
                values.put("autoscaling.maxReplicas", String.valueOf(config.getMaxReplicas()));
                values.put("autoscaling.targetCPUUtilizationPercentage", String.valueOf(config.getCpuThreshold().intValue()));
                values.put("autoscaling.targetMemoryUtilizationPercentage", String.valueOf(config.getMemoryThreshold().intValue()));
                log.info("HPA 설정 완료 - {}", config.getHpaConfigSummary());
            } else {
                values.put("autoscaling.enabled", "false");
                log.info("HPA 비활성화됨");
            }

            // Ingress 설정 적용
            if (config.isIngressEnabled()) {
                log.info("Ingress 설정 적용 중...");
                
                // Ingress Controller 자동 설치 확인 및 설치
                values.put("ingress.enabled", "true");
                values.put("ingress.hosts[0]", config.getIngressHost());
                values.put("ingress.path", config.getIngressPath());
                values.put("ingress.ingressClassName", config.getIngressClass());
                
                // TLS 설정
                if (config.isTlsEnabled()) {
                    values.put("ingress.tls.enabled", "true");
                    values.put("ingress.tls.secretName", config.getIngressTlsSecret() != null ? 
                        config.getIngressTlsSecret() : releaseName + "-tls");
                }
                
                log.info("Ingress 설정 완료 - {}", config.getIngressConfigSummary());
            } else {
                values.put("ingress.enabled", "false");
                log.info("Ingress 비활성화됨");
            }

            // 공통 보안 설정 적용
            log.info("공통 보안 설정 적용 중...");
            values.put("podSecurityPolicy.enabled", "false");
            values.put("rbac.create", "false");
            values.put("serviceAccount.create", "false");
            values.put("serviceAccount.name", "default");

            if (isRcloneChart(helmChart)) {
                applyRcloneGuiDefaults(values, config.getServicePort());
            }

            applyObjectStorageValues(catalog, request, providerName, helmChart.getChartName(), objectStorageValues);
            if (!objectStorageValues.isEmpty()) {
                tempObjectStorageValuesPath = createTempValuesFile(objectStorageValues);
            }

            // Helm CLI로 설치 실행
            runHelmInstallCli(releaseName, chartRef, namespace, helmChart.getChartVersion(), tempKubeconfigPath, values, tempObjectStorageValuesPath);
            
            // 간단한 Release 스텁 반환 - null 반환으로 변경
            Release result = null;

            log.info("Helm Chart '{}' 설치 완료 (HPA: {}, Ingress: {})", 
                    releaseName, config.isHpaEnabled(), config.isIngressEnabled());
            log.info("=== Helm Chart 배포 성공 ===");
            log.info("릴리스명: {}, 상태: {}, 네임스페이스: {}", releaseName, "deployed", namespace);

            return result;

        } catch (Exception e) {
            log.error("=== Helm Chart 배포 실패 ===");
            log.error("오류: {}", e.getMessage(), e);
            throw new RuntimeException("Helm Chart 배포 실패", e);
        } finally {
            // 8. 임시 kubeconfig 파일 삭제
            if (tempKubeconfigPath != null) {
                try {
                    log.info("8. 임시 kubeconfig 파일 삭제 중...");
                    Files.deleteIfExists(tempKubeconfigPath);
                    log.info("임시 kubeconfig 파일 삭제 완료");
                } catch (IOException e) {
                    log.warn("임시 kubeconfig 파일 삭제 중 오류 발생: {}", e.getMessage());
                }
            }
            if (tempObjectStorageValuesPath != null) {
                try {
                    Files.deleteIfExists(tempObjectStorageValuesPath);
                } catch (IOException e) {
                    log.warn("Failed to delete temporary Object Storage values file: {}", e.getMessage());
                }
            }
        }
    }

    public void uninstallHelmChart(String namespace, SoftwareCatalog catalog, String clusterName) {
        uninstallHelmChart(namespace, catalog, catalog.getHelmChart(), clusterName);
    }
    
    public void uninstallHelmChart(String namespace, SoftwareCatalog catalog, 
                                 kr.co.mcmp.softwarecatalog.application.model.HelmChart helmChart, String clusterName) {
        log.info("=== Helm Chart 삭제 시작 ===");
        log.info("Chart: {}, Namespace: {}, Cluster: {}", 
                helmChart.getChartName(), namespace, clusterName);
        
        try {
            // kubeconfig 생성
            String kubeconfigYaml = getKubeconfigForCluster(namespace, clusterName);
            java.nio.file.Path tempKubeconfigPath = createTempKubeconfigFile(kubeconfigYaml);
            
            try {
                String releaseName = getReleaseNameFromHistory(catalog.getId(), clusterName, namespace);
                if (releaseName == null) {
                    log.warn("배포 히스토리에서 릴리스 이름을 찾을 수 없습니다. 차트 이름으로 시도합니다.");
                    releaseName = helmChart.getChartName();
                }
                
                log.info("1. 릴리스 '{}' 삭제 실행 중...", releaseName);
                
                runHelmUninstallCli(releaseName, namespace, tempKubeconfigPath);

                log.info("2. 삭제 결과: 성공");
            } finally {
                // 임시 kubeconfig 파일 삭제
                if (tempKubeconfigPath != null) {
                    try {
                        java.nio.file.Files.deleteIfExists(tempKubeconfigPath);
                        log.info("임시 kubeconfig 파일 삭제 완료");
                    } catch (IOException e) {
                        log.warn("임시 kubeconfig 파일 삭제 중 오류 발생: {}", e.getMessage());
                    }
                }
            }
            log.info("=== Helm Chart 삭제 성공 ===");
        } catch (Exception e) {
            log.error("=== Helm Chart 삭제 중 오류 발생 ===");
            log.error("오류: {}", e.getMessage(), e);
            throw new RuntimeException("Helm Release 삭제 실패", e);
        }
    }

    public void uninstallHelmChartWithKubeconfig(String namespace, SoftwareCatalog catalog, 
                                               String clusterName, String kubeconfigYaml) {
        log.info("=== Helm Chart 삭제 시작 (kubeconfig 사용) ===");
        log.info("Chart: {}, Namespace: {}, Cluster: {}", 
                catalog.getHelmChart().getChartName(), namespace, clusterName);
        
        Path tempKubeconfigPath = null;
        try {
            // 임시 kubeconfig 파일 생성
            tempKubeconfigPath = createTempKubeconfigFile(kubeconfigYaml);
            log.info("임시 kubeconfig 파일 생성 완료: {}", tempKubeconfigPath);
            
            // 배포 히스토리에서 릴리스 이름 조회
            String releaseName = getReleaseNameFromHistory(catalog.getId(), clusterName, namespace);
            if (releaseName == null) {
                log.warn("배포 히스토리에서 릴리스 이름을 찾을 수 없습니다. 차트 이름으로 시도합니다.");
                releaseName = catalog.getHelmChart().getChartName();
            }
            
            log.info("1. 릴리스 '{}' 삭제 실행 중...", releaseName);
            
            runHelmUninstallCli(releaseName, namespace, tempKubeconfigPath);

            log.info("2. 삭제 결과: 성공");
            log.info("=== Helm Chart 삭제 성공 ===");
        } catch (Exception e) {
            log.error("=== Helm Chart 삭제 중 오류 발생 ===");
            log.error("오류: {}", e.getMessage(), e);
            throw new RuntimeException("Helm Chart 삭제 실패", e);
        } finally {
            // 임시 kubeconfig 파일 삭제
            if (tempKubeconfigPath != null) {
                try {
                    Files.deleteIfExists(tempKubeconfigPath);
                    log.info("임시 kubeconfig 파일 삭제 완료");
                } catch (IOException e) {
                    log.warn("임시 kubeconfig 파일 삭제 중 오류 발생: {}", e.getMessage());
                }
            }
        }
    }

    private String buildImageRepository(SoftwareCatalog catalog, kr.co.mcmp.softwarecatalog.application.model.HelmChart helmChart) {
        String imageName = helmChart.getImageRepository();
        
        // imageRepository가 null이거나 비어있으면 기본값 사용
        if (imageName == null || imageName.isEmpty()) {
            // Helm Chart의 chartName을 기반으로 이미지 이름 생성
            imageName = helmChart.getChartName().toLowerCase().replaceAll("\\s+", "-");
        }
        
        // Nexus 이미지 경로인지 확인 (IP:포트/저장소/이미지:태그 형식)
        if (imageName.matches("^\\d+\\.\\d+\\.\\d+\\.\\d+:\\d+/[^/]+/.*")) {
            // Nexus 이미지 경로인 경우 그대로 사용
            log.info("Nexus 이미지 경로 사용: {}", imageName);
            return imageName;
        }
        
        // docker.io/ 중복 제거
        String cleanImageName = imageName.replaceAll("^(docker\\.io/)+", "");
        cleanImageName = cleanImageName.trim();
        
        // 여전히 비어있으면 chartName 사용
        if (cleanImageName.isEmpty()) {
            cleanImageName = helmChart.getChartName().toLowerCase().replaceAll("\\s+", "-");
        }
        
        // 숫자만 있는 경우 문자열로 변환하고 적절한 prefix 추가
        if (cleanImageName.matches("^\\d+$")) {
            cleanImageName = "app-" + cleanImageName;
        }
        
        log.info("이미지 레포지토리 보정: '{}' -> '{}'", helmChart.getImageRepository(), cleanImageName);
        return cleanImageName;
    }

    private String getReleaseNameFromHistory(Long catalogId, String clusterName, String namespace) {
        try {
            // DeploymentHistory에서 해당 카탈로그의 최신 릴리스 이름 조회
            // 이는 KubernetesDeployService의 getReleaseNameFromHistory와 동일한 로직
            return null; // 현재는 간단히 null 반환, 필요시 구현
        } catch (Exception e) {
            log.warn("배포 히스토리에서 릴리스 이름 조회 중 오류 발생: {}", e.getMessage());
            return null;
        }
    }

    public Path createTempKubeconfigFile(String kubeconfigYaml) throws IOException {
        Path tempFile = Files.createTempFile("kubeconfig", ".yaml");
        Files.write(tempFile, kubeconfigYaml.getBytes(StandardCharsets.UTF_8));
        return tempFile;
    }

    public String getKubeconfigForCluster(String namespace, String clusterName) throws Exception {
        // K8sClusterDto 조회
        K8sClusterDto clusterDto = cbtumblebugRestApi.getK8sClusterByName(namespace, clusterName);
        if (clusterDto == null) {
            throw new RuntimeException("K8s 클러스터를 찾을 수 없습니다: " + clusterName);
        }

        // Provider별 kubeconfig 생성
        return kubeconfigResolver.getKubeconfigYaml(namespace, clusterName);
    }

    public String findLatestReleaseNameForChart(String namespace, String clusterName, String chartName) {
        Path tempKubeconfigPath = null;
        try {
            tempKubeconfigPath = createTempKubeconfigFile(getKubeconfigForCluster(namespace, clusterName));
            String releaseList = runHelmListCli(DEFAULT_HELM_NAMESPACE, tempKubeconfigPath);
            com.fasterxml.jackson.databind.JsonNode releases =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(releaseList);
            if (!releases.isArray()) {
                return null;
            }

            for (com.fasterxml.jackson.databind.JsonNode release : releases) {
                String name = release.path("name").asText(null);
                String chart = release.path("chart").asText("");
                if (name != null && chart.toLowerCase().contains(chartName.toLowerCase())) {
                    return name;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to resolve Helm release name for chart '{}': {}", chartName, e.getMessage());
        } finally {
            deleteTempFile(tempKubeconfigPath);
        }
        return null;
    }

    public void ensureMetricsServer(KubernetesClient client, String namespace, String clusterName) {
        Path tempKubeconfigPath = null;
        try {
            tempKubeconfigPath = createTempKubeconfigFile(getKubeconfigForCluster(namespace, clusterName));
            ensureMetricsServer(client, tempKubeconfigPath);
        } catch (Exception e) {
            throw new RuntimeException("metrics-server installation failed", e);
        } finally {
            deleteTempFile(tempKubeconfigPath);
        }
    }

    public void ensureIngressController(KubernetesClient client, String namespace, String clusterName) {
        Path tempKubeconfigPath = null;
        try {
            tempKubeconfigPath = createTempKubeconfigFile(getKubeconfigForCluster(namespace, clusterName));
            ensureIngressController(client, namespace, tempKubeconfigPath);
        } catch (Exception e) {
            throw new RuntimeException("ingress-nginx installation failed", e);
        } finally {
            deleteTempFile(tempKubeconfigPath);
        }
    }

    private void deleteTempFile(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Failed to delete temporary kubeconfig file: {}", e.getMessage());
        }
    }

    private void ensureMetricsServer(KubernetesClient client, Path tempKubeconfigPath) {
        if (isMetricsServerReady(client)) {
            log.info("metrics-server is already ready.");
            return;
        }

        if (!isMetricsServerReleaseInstalled(tempKubeconfigPath)) {
            installMetricsServerWithHelm(tempKubeconfigPath);
        } else {
            log.info("metrics-server Helm release already exists. Waiting for readiness.");
        }

        waitForMetricsServerReady(client);
    }

    private boolean isMetricsServerReleaseInstalled(Path tempKubeconfigPath) {
        try {
            String releaseList = runHelmListCliInNamespace(METRICS_SERVER_NAMESPACE, tempKubeconfigPath);
            return releaseList != null && releaseList.contains("\"name\":\"" + METRICS_SERVER_RELEASE + "\"");
        } catch (Exception e) {
            log.info("Failed to check metrics-server Helm release: {}", e.getMessage());
            return false;
        }
    }

    private void installMetricsServerWithHelm(Path tempKubeconfigPath) {
        try {
            HelmChart metricsHelmChart = new HelmChart();
            metricsHelmChart.setChartRepositoryUrl(METRICS_SERVER_REPOSITORY_URL);
            metricsHelmChart.setRepositoryName(METRICS_SERVER_REPOSITORY);
            addHelmRepository(metricsHelmChart);

            runHelmInstallCliInNamespace(
                    METRICS_SERVER_RELEASE,
                    METRICS_SERVER_CHART,
                    METRICS_SERVER_NAMESPACE,
                    METRICS_SERVER_CHART_VERSION,
                    tempKubeconfigPath,
                    new java.util.HashMap<>(),
                    null,
                    true);
        } catch (Exception e) {
            throw new RuntimeException("metrics-server Helm install failed", e);
        }
    }

    private void waitForMetricsServerReady(KubernetesClient client) {
        int maxAttempts = 30;
        int attempt = 0;

        while (attempt < maxAttempts) {
            if (isMetricsServerReady(client)) {
                log.info("metrics-server is ready.");
                return;
            }

            attempt++;
            log.info("Waiting for metrics-server readiness... ({}/{})", attempt, maxAttempts);
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("metrics-server readiness wait interrupted", e);
            }
        }

        throw new RuntimeException("metrics-server was not ready before timeout");
    }

    private boolean isMetricsServerReady(KubernetesClient client) {
        try {
            boolean metricsApiAvailable = isMetricsApiAvailable(client);
            var deployment = client.apps().deployments()
                    .inNamespace(METRICS_SERVER_NAMESPACE)
                    .withName(METRICS_SERVER_RELEASE)
                    .get();
            if (deployment == null) {
                if (metricsApiAvailable) {
                    log.info("metrics.k8s.io API is already available without Helm-managed metrics-server deployment.");
                    return true;
                }
                return false;
            }

            Integer readyReplicas = deployment != null && deployment.getStatus() != null
                    ? deployment.getStatus().getReadyReplicas()
                    : null;
            Integer desiredReplicas = deployment != null && deployment.getSpec() != null
                    ? deployment.getSpec().getReplicas()
                    : null;
            boolean deploymentReady = readyReplicas != null
                    && readyReplicas > 0
                    && (desiredReplicas == null || readyReplicas >= desiredReplicas);

            return deploymentReady && metricsApiAvailable;
        } catch (Exception e) {
            log.debug("metrics-server readiness check failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean isMetricsApiAvailable(KubernetesClient client) {
        ResourceDefinitionContext context = new ResourceDefinitionContext.Builder()
                .withGroup("metrics.k8s.io")
                .withVersion("v1beta1")
                .withKind("NodeMetrics")
                .withPlural("nodes")
                .withNamespaced(false)
                .build();

        try {
            client.genericKubernetesResources(context).list();
            return true;
        } catch (Exception e) {
            log.debug("metrics.k8s.io API is not ready yet: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 기존 릴리스를 확인하고 제거하는 메서드
     */
    private void handleExistingRelease(String releaseName, String namespace, Path tempKubeconfigPath, Long catalogId, String clusterName) {
        try {
            log.info("기존 릴리스 확인 중: {}", releaseName);
            
            // 현재 배포와 관련된 릴리스만 확인
            String existingReleaseName = getReleaseNameFromHistory(catalogId, clusterName, namespace);
            
            if (existingReleaseName != null && !existingReleaseName.isEmpty()) {
                log.info("기존 릴리스 발견: {} (현재 배포와 관련됨)", existingReleaseName);
                
                // 기존 릴리스 제거
                try {
                    runHelmUninstallCli(existingReleaseName, namespace, tempKubeconfigPath);
                    log.info("기존 릴리스 제거 완료: {}", existingReleaseName);
                } catch (Exception e) {
                    log.warn("기존 릴리스 제거 중 오류 발생 (무시하고 계속): {}", e.getMessage());
                }
            } else {
                log.info("제거할 기존 릴리스 없음");
            }
        } catch (Exception e) {
            log.warn("기존 릴리스 처리 중 오류 발생 (무시하고 계속): {}", e.getMessage());
        }
    }

    /**
     * 네임스페이스에 NGINX Ingress Controller가 설치되어 있는지 확인하고, 없으면 설치합니다.
     */
    private void ensureIngressController(KubernetesClient client, String namespace, Path tempKubeconfigPath) {
        try {
            log.info("네임스페이스 '" + namespace + "'에서 NGINX Ingress Controller 확인 중...");
            
            if (isIngressControllerInstalled(namespace, tempKubeconfigPath)) {
                log.info("NGINX Ingress Controller가 이미 설치되어 있습니다.");
                waitForIngressControllerReady(client, namespace);
                return;
            }
            
            log.info("NGINX Ingress Controller가 설치되어 있지 않습니다. 설치를 시작합니다...");
            installIngressControllerWithHelm(namespace, tempKubeconfigPath);
            
            // 설치 완료 대기
            waitForIngressControllerReady(client, namespace);
            log.info("NGINX Ingress Controller 설치 및 준비 완료");
            
        } catch (Exception e) {
            System.err.println("NGINX Ingress Controller 설치 중 오류 발생: " + e.getMessage());
            throw new RuntimeException("NGINX Ingress Controller 설치 실패", e);
        }
    }

    /**
     * 네임스페이스에 NGINX Ingress Controller가 설치되어 있는지 확인합니다.
     */
    private boolean isIngressControllerInstalled(String namespace, Path tempKubeconfigPath) {
        try {
            // Helm 릴리스 목록에서 nginx-ingress 확인
            String releaseList = runHelmListCli(namespace, tempKubeconfigPath);
            String releaseName = "nginx-ingress-" + namespace;
            
            if (releaseList != null && releaseList.contains("\"name\":\"" + releaseName + "\"")) {
                log.info("NGINX Ingress Controller 발견: nginx-ingress 또는 ingress-nginx");
                return true;
            }
            
            return false;
        } catch (Exception e) {
            log.info("NGINX Ingress Controller 설치 확인 중 오류 발생: " + e.getMessage());
            return false;
        }
    }

    /**
     * Helm을 사용하여 NGINX Ingress Controller를 설치합니다.
     */
    private void installIngressControllerWithHelm(String namespace, Path tempKubeconfigPath) {
        try {
            log.info("NGINX Ingress Controller Helm 설치 시작...");
            
            // NGINX Ingress Controller Helm Chart 설치
            String releaseName = "nginx-ingress-" + namespace;
            
            // Values 맵 구성
            java.util.Map<String, String> values = new java.util.HashMap<>();
            values.put("controller.service.type", "NodePort");
            values.put("controller.service.nodePorts.http", "30880");

            // Helm CLI로 repository 추가
            kr.co.mcmp.softwarecatalog.application.model.HelmChart ingressHelmChart = new HelmChart();
            ingressHelmChart.setChartRepositoryUrl(INGRESS_NGINX_REPOSITORY_URL);
            ingressHelmChart.setRepositoryName(INGRESS_NGINX_REPOSITORY);
            addHelmRepository(ingressHelmChart);

            // Helm CLI로 설치 실행
            runHelmInstallCli(releaseName, INGRESS_NGINX_CHART, namespace, INGRESS_NGINX_CHART_VERSION, tempKubeconfigPath, values);
            
            // 간단한 Release 스텁 반환 - null 반환으로 변경
            Release result = null;
            log.info("NGINX Ingress Controller 설치 완료: " + releaseName + " (상태: deployed)");
            
        } catch (Exception e) {
            System.err.println("NGINX Ingress Controller Helm 설치 실패: " + e.getMessage());
            throw new RuntimeException("NGINX Ingress Controller 설치 실패", e);
        }
    }

    /**
     * NGINX Ingress Controller가 준비될 때까지 대기합니다.
     */
    private void waitForIngressControllerReady(KubernetesClient client, String namespace) {
        int maxAttempts = 30; // 5분 대기 (10초 * 30)
        int attempt = 0;
        
        log.info("NGINX Ingress Controller 준비 상태 확인 중...");
        
        while (attempt < maxAttempts) {
            try {
                if (isIngressControllerReady(client, namespace)) {
                    log.info("NGINX Ingress Controller가 준비되었습니다.");
                    return;
                }
                
                attempt++;
                log.info("NGINX Ingress Controller 준비 대기 중... (" + attempt + "/" + maxAttempts + ")");
                Thread.sleep(10000); // 10초 대기
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("NGINX Ingress Controller 준비 대기 중 중단됨", e);
            } catch (Exception e) {
                log.info("NGINX Ingress Controller 준비 상태 확인 중 오류 발생: " + e.getMessage());
                attempt++;
            }
        }
        
        throw new RuntimeException("NGINX Ingress Controller가 준비되지 않았습니다. 최대 대기 시간 초과");
    }

    /**
     * NGINX Ingress Controller가 준비되었는지 확인합니다.
     */
    private boolean isIngressControllerReady(KubernetesClient client, String namespace) {
        try {
            String releaseName = "nginx-ingress-" + namespace;
            String controllerName = releaseName + "-ingress-nginx-controller";
            String admissionServiceName = controllerName + "-admission";
            String helmNamespace = "default";

            var deployment = client.apps().deployments()
                    .inNamespace(helmNamespace)
                    .withName(controllerName)
                    .get();
            Integer readyReplicas = deployment != null && deployment.getStatus() != null
                    ? deployment.getStatus().getReadyReplicas()
                    : null;
            Integer desiredReplicas = deployment != null && deployment.getSpec() != null
                    ? deployment.getSpec().getReplicas()
                    : null;
            boolean deploymentReady = readyReplicas != null
                    && readyReplicas > 0
                    && (desiredReplicas == null || readyReplicas >= desiredReplicas);

            var admissionEndpoints = client.endpoints()
                    .inNamespace(helmNamespace)
                    .withName(admissionServiceName)
                    .get();
            boolean admissionReady = admissionEndpoints != null
                    && admissionEndpoints.getSubsets() != null
                    && admissionEndpoints.getSubsets().stream().anyMatch(subset ->
                            subset.getAddresses() != null && !subset.getAddresses().isEmpty()
                                    && subset.getPorts() != null && !subset.getPorts().isEmpty());

            log.info("NGINX Ingress Controller readiness - deploymentReady={}, admissionReady={}, readyReplicas={}, desiredReplicas={}",
                    deploymentReady, admissionReady, readyReplicas, desiredReplicas);
            if (!deploymentReady || !admissionReady) {
                return false;
            }

            // KubernetesClient를 사용하여 Pod 상태 확인
            // 여기서는 간단히 true를 반환하도록 구현
            // 실제로는 kubernetesClient를 사용하여 Pod 상태를 확인해야 함
            log.info("NGINX Ingress Controller 준비 상태 확인 중...");
            return true; // 임시로 true 반환
        } catch (Exception e) {
            log.info("NGINX Ingress Controller 준비 상태 확인 중 오류 발생: " + e.getMessage());
            return false;
        }
    }

    private boolean isRcloneChart(HelmChart helmChart) {
        return helmChart != null && StringUtils.equalsIgnoreCase(helmChart.getChartName(), "rclone");
    }

    private void applyRcloneGuiDefaults(Map<String, String> values, Integer servicePort) {
        String port = String.valueOf(servicePort != null ? servicePort : 5572);
        boolean ingressEnabled = Boolean.parseBoolean(values.getOrDefault("ingress.enabled", "false"));
        String ingressHost = values.get("ingress.hosts[0]");
        String ingressPath = values.getOrDefault("ingress.path", "/");
        String ingressClassName = values.getOrDefault("ingress.ingressClassName",
                values.getOrDefault("ingress.className", "nginx"));
        boolean ingressTlsEnabled = Boolean.parseBoolean(values.getOrDefault("ingress.tls.enabled", "false"));
        String ingressTlsSecretName = values.get("ingress.tls.secretName");

        values.remove("persistence.enabled");
        values.remove("persistence.storageClass");
        values.remove("persistence.size");
        values.remove("persistence.accessMode");
        values.remove("service.type");
        values.remove("service.port");
        values.remove("ingress.enabled");
        values.remove("ingress.host");
        values.remove("ingress.hosts[0]");
        values.remove("ingress.path");
        values.remove("ingress.ingressClassName");
        values.remove("ingress.className");
        values.remove("ingress.tls.enabled");
        values.remove("ingress.tls.secretName");

        values.put("persistence.config.enabled", "false");
        values.put("service.main.enabled", "true");
        values.put("service.main.type", "ClusterIP");
        values.put("service.main.ports.http.enabled", "true");
        values.put("service.main.ports.http.primary", "true");
        values.put("service.main.ports.http.port", port);
        values.put("probes.liveness.enabled", "false");
        values.put("probes.readiness.enabled", "false");
        values.put("probes.startup.enabled", "false");

        if (ingressEnabled && StringUtils.isNotBlank(ingressHost)) {
            values.put("ingress.main.enabled", "true");
            values.put("ingress.main.ingressClassName", ingressClassName);
            values.put("ingress.main.hosts[0].host", ingressHost);
            values.put("ingress.main.hosts[0].paths[0].path", StringUtils.defaultIfBlank(ingressPath, "/"));
            values.put("ingress.main.hosts[0].paths[0].pathType", "Prefix");

            if (ingressTlsEnabled && StringUtils.isNotBlank(ingressTlsSecretName)) {
                values.put("ingress.main.tls[0].secretName", ingressTlsSecretName);
                values.put("ingress.main.tls[0].hosts[0]", ingressHost);
            }
        } else {
            values.put("ingress.main.enabled", "false");
        }
    }

    private void applyObjectStorageValues(SoftwareCatalog catalog,
                                          kr.co.mcmp.softwarecatalog.application.dto.DeploymentRequest request,
                                          String providerName,
                                          String chartName,
                                          Map<String, Object> valuesFile) {
        if (!StringUtils.equalsIgnoreCase(chartName, "loki")) {
            return;
        }
        if (request == null || request.getAdditionalConfig() == null) {
            throw new IllegalArgumentException("Object Storage configuration is required for Loki deployment.");
        }

        Object rawConfig = request.getAdditionalConfig().get("objectStorage");
        if (!(rawConfig instanceof Map<?, ?> objectStorage)) {
            throw new IllegalArgumentException("Object Storage configuration is required for Loki deployment.");
        }

        if (!asBoolean(objectStorage.get("enabled"))) {
            throw new IllegalArgumentException("Object Storage configuration is required for Loki deployment.");
        }

        String backendType = stringValue(objectStorage.get("backendType"), "s3");
        if (!"s3".equalsIgnoreCase(backendType) && !"s3-compatible".equalsIgnoreCase(backendType)) {
            log.warn("Unsupported Object Storage backend type for current deployment path: {}", backendType);
            return;
        }

        String endpoint = stringValue(objectStorage.get("endpoint"), null);
        String region = stringValue(objectStorage.get("region"), null);
        String bucket = stringValue(objectStorage.get("bucket"), null);
        String accessKey = stringValue(objectStorage.get("accessKey"), null);
        String secretKey = stringValue(objectStorage.get("secretKey"), null);
        boolean forcePathStyle = asBoolean(objectStorage.get("forcePathStyle"));
        boolean insecure = isHttpEndpoint(endpoint);

        if (StringUtils.isAnyBlank(region, bucket, accessKey, secretKey)) {
            throw new IllegalArgumentException("Object Storage region, bucket, access key, and secret key are required for Loki deployment.");
        }
        if (!isAwsProvider(providerName) && StringUtils.isBlank(endpoint)) {
            throw new IllegalArgumentException("Object Storage endpoint is required for non-AWS S3-compatible Loki deployment.");
        }

        String storageClassName = stringValue(request.getAdditionalConfig().get("storageClass"), null);
        if (StringUtils.isBlank(storageClassName)) {
            throw new IllegalArgumentException("Storage Class is required for Loki deployment.");
        }
        if (!kubernetesStorageClassService.exists(request.getNamespace(), request.getClusterName(), storageClassName)) {
            throw new IllegalArgumentException("Storage Class not found in the selected cluster: " + storageClassName);
        }

        Map<String, Object> loki = nestedMap(valuesFile, "loki");
        loki.put("configStorageType", "Secret");
        loki.put("auth_enabled", false);
        nestedMap(loki, "commonConfig").put("replication_factor", 1);
        applyDefaultLokiSchemaConfig(loki);

        Map<String, Object> storage = nestedMap(loki, "storage");
        storage.put("type", "s3");

        Map<String, Object> s3 = nestedMap(storage, "s3");
        if (StringUtils.isNotBlank(endpoint)) {
            s3.put("endpoint", normalizeObjectStorageEndpoint(endpoint));
        }
        s3.put("region", region);
        s3.put("accessKeyId", accessKey);
        s3.put("secretAccessKey", secretKey);
        s3.put("s3ForcePathStyle", forcePathStyle);
        s3.put("insecure", insecure);

        Map<String, Object> bucketNames = nestedMap(storage, "bucketNames");
        bucketNames.put("chunks", bucket);
        bucketNames.put("ruler", bucket);
        bucketNames.put("admin", bucket);

        Map<String, Object> minio = nestedMap(valuesFile, "minio");
        minio.put("enabled", false);

        applyLokiObjectStorageRuntimeDefaults(valuesFile, storageClassName, catalog);

        log.info("Object Storage Helm values prepared for provider={}, backend=s3-compatible, bucketNames configured", providerName);
    }

    private void applyLokiObjectStorageRuntimeDefaults(Map<String, Object> valuesFile, String storageClassName, SoftwareCatalog catalog) {
        nestedMap(valuesFile, "gateway").put("verboseLogging", false);
        nestedMap(valuesFile, "chunksCache").put("enabled", false);
        nestedMap(valuesFile, "resultsCache").put("enabled", false);
        nestedMap(valuesFile, "lokiCanary").put("enabled", false);
        nestedMap(valuesFile, "test").put("enabled", false);

        Map<String, Object> singleBinary = nestedMap(valuesFile, "singleBinary");
        Map<String, Object> persistence = nestedMap(singleBinary, "persistence");
        persistence.put("enabled", true);
        persistence.put("storageClass", storageClassName);
        applyLokiResourceValues(singleBinary, catalog);

        Map<String, Object> gateway = nestedMap(valuesFile, "gateway");
        applyLokiResourceValues(gateway, catalog);
    }

    private void applyLokiResourceValues(Map<String, Object> component, SoftwareCatalog catalog) {
        if (catalog == null) {
            return;
        }

        Map<String, Object> resources = nestedMap(component, "resources");
        Map<String, Object> requests = nestedMap(resources, "requests");
        Map<String, Object> limits = nestedMap(resources, "limits");

        if (catalog.getMinCpu() != null) {
            requests.put("cpu", catalog.getMinCpu().toString());
        }
        String minMemory = formatMemoryMi(catalog.getMinMemory());
        if (StringUtils.isNotBlank(minMemory)) {
            requests.put("memory", minMemory);
        }
        if (catalog.getRecommendedCpu() != null) {
            limits.put("cpu", catalog.getRecommendedCpu().toString());
        }
        String recommendedMemory = formatMemoryMi(catalog.getRecommendedMemory());
        if (StringUtils.isNotBlank(recommendedMemory)) {
            limits.put("memory", recommendedMemory);
        }
    }

    private void applyDefaultLokiSchemaConfig(Map<String, Object> loki) {
        Object existingSchemaConfig = loki.get("schemaConfig");
        if (existingSchemaConfig instanceof Map<?, ?> existingSchemaMap && !existingSchemaMap.isEmpty()) {
            return;
        }

        Map<String, Object> index = new java.util.LinkedHashMap<>();
        index.put("prefix", "loki_index_");
        index.put("period", "24h");

        Map<String, Object> config = new java.util.LinkedHashMap<>();
        config.put("from", "2024-04-01");
        config.put("store", "tsdb");
        config.put("object_store", "s3");
        config.put("schema", "v13");
        config.put("index", index);

        Map<String, Object> schemaConfig = new java.util.LinkedHashMap<>();
        schemaConfig.put("configs", java.util.List.of(config));
        loki.put("schemaConfig", schemaConfig);
    }

    private boolean hasObjectStorageCapability(SoftwareCatalog catalog) {
        if (catalog == null || catalog.getId() == null) {
            return false;
        }

        SoftwareCatalog catalogWithRefs = catalogRepository.findByIdWithCatalogRefs(catalog.getId()).orElse(catalog);
        if (catalogWithRefs.getCatalogRefs() == null) {
            return false;
        }

        return catalogWithRefs.getCatalogRefs().stream().anyMatch(ref ->
                "object-storage".equalsIgnoreCase(ref.getRefValue())
                        && ("CAPABILITY".equalsIgnoreCase(ref.getRefType()) || "TAG".equalsIgnoreCase(ref.getRefType())));
    }

    private boolean isAwsProvider(String providerName) {
        return StringUtils.containsIgnoreCase(providerName, "aws");
    }

    private boolean isHttpEndpoint(String endpoint) {
        return StringUtils.startsWithIgnoreCase(StringUtils.trimToEmpty(endpoint), "http://");
    }

    private String normalizeObjectStorageEndpoint(String endpoint) {
        String trimmed = StringUtils.trimToEmpty(endpoint);
        if (StringUtils.startsWithIgnoreCase(trimmed, "http://")
                || StringUtils.startsWithIgnoreCase(trimmed, "https://")) {
            return trimmed;
        }
        return "https://" + trimmed;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> nestedMap(Map<String, Object> parent, String key) {
        Object existing = parent.get(key);
        if (existing instanceof Map<?, ?>) {
            return (Map<String, Object>) existing;
        }
        Map<String, Object> created = new java.util.LinkedHashMap<>();
        parent.put(key, created);
        return created;
    }

    private String stringValue(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String stringValue = String.valueOf(value).trim();
        return stringValue.isEmpty() ? defaultValue : stringValue;
    }

    private boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return false;
        }
        return "true".equalsIgnoreCase(String.valueOf(value));
    }

    private Path createTempValuesFile(Map<String, Object> values) throws IOException {
        Path valuesFile = Files.createTempFile("helm-object-storage-", ".yaml");
        String yaml = new Yaml().dump(values);
        Files.writeString(valuesFile, yaml, StandardCharsets.UTF_8);
        return valuesFile;
    }

    private void putHelmValueIfPresent(Map<String, String> values, String key, String value) {
        if (StringUtils.isNotBlank(value)) {
            values.put(key, value);
        }
    }

    private String formatMemoryMi(Double memoryGb) {
        if (memoryGb == null || memoryGb <= 0) {
            return null;
        }
        return (long) Math.ceil(memoryGb * 1024.0) + "Mi";
    }

    private void runHelmInstallCli(String releaseName,
                                   String chartRef,
                                   String namespace,
                                   String version,
                                   Path kubeconfig,
                                   java.util.Map<String,String> values) throws Exception {
        runHelmInstallCli(releaseName, chartRef, namespace, version, kubeconfig, values, null);
    }

    private void runHelmInstallCli(String releaseName,
                                   String chartRef,
                                   String namespace,
                                   String version,
                                   Path kubeconfig,
                                   java.util.Map<String,String> values,
                                   Path valuesFile) throws Exception {
        java.util.List<String> cmd = new java.util.ArrayList<>();
        // Helm 경로 지정 (관리자 권한 없이 사용)
        String helmPath = getHelmPath();
        cmd.add(helmPath); cmd.add("install");
        cmd.add(releaseName); cmd.add(chartRef);
        cmd.add("--namespace"); cmd.add("default");
        cmd.add("--version"); cmd.add(version);
        cmd.add("--kubeconfig"); cmd.add(kubeconfig.toString());
        if (valuesFile != null) {
            cmd.add("--values");
            cmd.add(valuesFile.toString());
        }
        for (java.util.Map.Entry<String,String> e : values.entrySet()) {
            cmd.add("--set");
            cmd.add(e.getKey() + "=" + e.getValue());
        }
        ProcessBuilder pb = new ProcessBuilder(cmd);
        Process p = pb.start();
        int ec = p.waitFor();
        String out = new String(p.getInputStream().readAllBytes());
        String err = new String(p.getErrorStream().readAllBytes());
        if (ec != 0) {
            throw new RuntimeException("helm install failed (" + ec + "): " + err + "\n" + out);
        }
        log.info("helm install output: {}", out);
    }

    private void runHelmInstallCliInNamespace(String releaseName,
                                             String chartRef,
                                             String helmNamespace,
                                             String version,
                                             Path kubeconfig,
                                             java.util.Map<String,String> values,
                                             Path valuesFile,
                                             boolean waitForReady) throws Exception {
        java.util.List<String> cmd = new java.util.ArrayList<>();
        String helmPath = getHelmPath();
        cmd.add(helmPath); cmd.add("install");
        cmd.add(releaseName); cmd.add(chartRef);
        cmd.add("--namespace"); cmd.add(helmNamespace);
        cmd.add("--version"); cmd.add(version);
        cmd.add("--kubeconfig"); cmd.add(kubeconfig.toString());
        if (waitForReady) {
            cmd.add("--wait");
            cmd.add("--timeout");
            cmd.add(HELM_WAIT_TIMEOUT);
        }
        if (valuesFile != null) {
            cmd.add("--values");
            cmd.add(valuesFile.toString());
        }
        for (java.util.Map.Entry<String,String> e : values.entrySet()) {
            cmd.add("--set");
            cmd.add(e.getKey() + "=" + e.getValue());
        }
        ProcessBuilder pb = new ProcessBuilder(cmd);
        Process p = pb.start();
        int ec = p.waitFor();
        String out = new String(p.getInputStream().readAllBytes());
        String err = new String(p.getErrorStream().readAllBytes());
        if (ec != 0) {
            throw new RuntimeException("helm install failed (" + ec + "): " + err + "\n" + out);
        }
        log.info("helm install output: {}", out);
    }
    
    /**
     * Helm 실행 파일 경로를 반환합니다.
     * 관리자 권한 없이 사용할 수 있도록 여러 경로를 확인합니다.
     */
    String getHelmPath() {
        // 1. 프로젝트 루트의 helm 폴더에서 확인
        String projectRoot = System.getProperty("user.dir");
        String localHelmPath = projectRoot + File.separator + "helm" + File.separator + "helm.exe";
        if (new File(localHelmPath).exists()) {
            log.info("Using local Helm: {}", localHelmPath);
            return localHelmPath;
        }
        
        // 2. 환경변수에서 Helm 경로 확인
        String helmHome = System.getenv("HELM_HOME");
        if (helmHome != null && !helmHome.isEmpty()) {
            String helmPath = helmHome + File.separator + "helm.exe";
            if (new File(helmPath).exists()) {
                log.info("Using Helm from HELM_HOME: {}", helmPath);
                return helmPath;
            }
        }
        
        // 3. 다운로드 폴더에서 확인 (사용자가 다운로드한 위치)
        String userHome = System.getProperty("user.home");
        String downloadHelmPath = userHome + File.separator + "Downloads" + File.separator + "helm-v3.18.6-windows-amd64" + File.separator + "windows-amd64" + File.separator + "helm.exe";
        if (new File(downloadHelmPath).exists()) {
            log.info("Using Helm from Downloads: {}", downloadHelmPath);
            return downloadHelmPath;
        }
        
        // 4. PATH에서 helm 명령어 확인
        try {
            ProcessBuilder pb = new ProcessBuilder("helm", "version");
            Process p = pb.start();
            int exitCode = p.waitFor();
            if (exitCode == 0) {
                log.info("Using Helm from PATH");
                return "helm";
            }
        } catch (Exception e) {
            log.debug("Helm not found in PATH: {}", e.getMessage());
        }
        
        // 5. 기본값 (실패 시)
        log.warn("Helm not found. Please copy helm.exe to project/helm/ folder or set HELM_HOME environment variable.");
        return "helm"; // 기본값으로 시도
    }

    private void runHelmRepoAddCli(String repositoryName, String repositoryUrl) throws Exception {
        java.util.List<String> cmd = new java.util.ArrayList<>();
        String helmPath = getHelmPath();
        cmd.add(helmPath); cmd.add("repo"); cmd.add("add");
        cmd.add(repositoryName); cmd.add(repositoryUrl);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        Process p = pb.start();
        int ec = p.waitFor();
        String out = new String(p.getInputStream().readAllBytes());
        String err = new String(p.getErrorStream().readAllBytes());
        if (ec != 0) {
            throw new RuntimeException("helm repo add failed (" + ec + "): " + err + "\n" + out);
        }
        log.info("helm repo add output: {}", out);
    }

    private void runHelmRepoUpdateCli() throws Exception {
        java.util.List<String> cmd = new java.util.ArrayList<>();
        String helmPath = getHelmPath();
        cmd.add(helmPath); cmd.add("repo"); cmd.add("update");
        ProcessBuilder pb = new ProcessBuilder(cmd);
        Process p = pb.start();
        int ec = p.waitFor();
        String out = new String(p.getInputStream().readAllBytes());
        String err = new String(p.getErrorStream().readAllBytes());
        if (ec != 0) {
            throw new RuntimeException("helm repo update failed (" + ec + "): " + err + "\n" + out);
        }
        log.info("helm repo update output: {}", out);
    }

    private void runHelmUninstallCli(String releaseName, String namespace, Path kubeconfig) throws Exception {
        java.util.List<String> cmd = new java.util.ArrayList<>();
        String helmPath = getHelmPath();
        cmd.add(helmPath); cmd.add("uninstall");
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

    private String runHelmListCli(String namespace, Path kubeconfig) throws Exception {
        java.util.List<String> cmd = new java.util.ArrayList<>();
        cmd.add(getHelmPath()); cmd.add("list");
        cmd.add("--namespace"); cmd.add("default");
        if (kubeconfig != null) {
            cmd.add("--kubeconfig"); cmd.add(kubeconfig.toString());
        }
        cmd.add("--output"); cmd.add("json");
        ProcessBuilder pb = new ProcessBuilder(cmd);
        Process p = pb.start();
        int ec = p.waitFor();
        String out = new String(p.getInputStream().readAllBytes());
        String err = new String(p.getErrorStream().readAllBytes());
        if (ec != 0) {
            throw new RuntimeException("helm list failed (" + ec + "): " + err + "\n" + out);
        }
        return out;
    }

    private String runHelmListCliInNamespace(String helmNamespace, Path kubeconfig) throws Exception {
        java.util.List<String> cmd = new java.util.ArrayList<>();
        cmd.add(getHelmPath()); cmd.add("list");
        cmd.add("--namespace"); cmd.add(helmNamespace);
        if (kubeconfig != null) {
            cmd.add("--kubeconfig"); cmd.add(kubeconfig.toString());
        }
        cmd.add("--output"); cmd.add("json");
        ProcessBuilder pb = new ProcessBuilder(cmd);
        Process p = pb.start();
        int ec = p.waitFor();
        String out = new String(p.getInputStream().readAllBytes());
        String err = new String(p.getErrorStream().readAllBytes());
        if (ec != 0) {
            throw new RuntimeException("helm list failed (" + ec + "): " + err + "\n" + out);
        }
        return out;
    }

    private boolean runHelmListAndCheckRelease(String releaseName, String namespace, Path kubeconfig) throws Exception {
        String releaseList = runHelmListCli(namespace, kubeconfig);
        return releaseList != null && releaseList.contains("\"name\":\"" + releaseName + "\"");
    }

    /**
     * Helm repository를 추가합니다.
     */
    private void addHelmRepository(kr.co.mcmp.softwarecatalog.application.model.HelmChart helmChart) throws Exception {
        String chartRepositoryUrl = helmChart.getChartRepositoryUrl();
        String repositoryName = helmChart.getRepositoryName();
        
        log.info("Adding Helm repository: {} -> {}", repositoryName, chartRepositoryUrl);
        try {
            runHelmRepoAddCli(repositoryName, chartRepositoryUrl);
            runHelmRepoUpdateCli();
            log.info("Helm repository added and updated successfully");
        } catch (Exception e) {
            log.warn("Helm repository 추가/업데이트 중 오류 발생: {}", e.getMessage());
        }
    }
}
