package kr.co.mcmp.softwarecatalog.kubernetes.service;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.springframework.stereotype.Service;

import com.marcnuri.helm.Helm;
import com.marcnuri.helm.InstallCommand;
import com.marcnuri.helm.Release;
import com.marcnuri.helm.UninstallCommand;

import io.fabric8.kubernetes.client.KubernetesClient;
import kr.co.mcmp.ape.cbtumblebug.api.CbtumblebugRestApi;
import kr.co.mcmp.ape.cbtumblebug.dto.K8sClusterDto;
import kr.co.mcmp.softwarecatalog.SoftwareCatalog;
import kr.co.mcmp.softwarecatalog.kubernetes.config.KubeConfigProviderFactory;
import kr.co.mcmp.softwarecatalog.kubernetes.config.KubeConfigProvider;
import kr.co.mcmp.softwarecatalog.kubernetes.util.ReleaseNameGenerator;
import kr.co.mcmp.softwarecatalog.application.dto.DeploymentConfigDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class HelmChartService {

    private final CbtumblebugRestApi cbtumblebugRestApi;
    private final KubeConfigProviderFactory providerFactory;
    private final ReleaseNameGenerator releaseNameGenerator;

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
        log.info("=== Helm Chart 배포 시작 ===");
        log.info("Chart: {}, Namespace: {}, Cluster: {}", 
                helmChart.getChartName(), namespace, clusterName);
        
        Path tempKubeconfigPath = null;

        try {
            // 1. 클러스터 정보 조회
            K8sClusterDto clusterDto = cbtumblebugRestApi.getK8sClusterByName(namespace, clusterName);
            log.info("클러스터 정보 조회 완료: {}", clusterName);
            log.info("Provider: {}", clusterDto.getConnectionConfig().getProviderName());

            // 2. Kubeconfig YAML 생성
            String kubeconfigYaml = providerFactory.getProvider(clusterDto.getConnectionConfig().getProviderName())
                    .getOriginalKubeconfigYaml(clusterDto);
            log.info("3. Kubeconfig YAML 생성 완료 (길이: {} bytes)", kubeconfigYaml.length());

            // 3. 임시 kubeconfig 파일 생성
            tempKubeconfigPath = createTempKubeconfigFile(kubeconfigYaml);
            log.info("4. 임시 kubeconfig 파일 생성 중...");
            log.info("임시 kubeconfig 파일 생성 완료: {}", tempKubeconfigPath);

            // 4. 릴리스 이름 생성
            String releaseName = releaseNameGenerator.generateReleaseName(helmChart.getChartName());
            log.info("새 릴리스 이름 생성: {}", releaseName);
            log.info("5. 릴리스 이름 결정: {}", releaseName);

            // 5. 기존 릴리스 확인 (현재 배포와 관련된 것만)
            log.info("6. 기존 릴리스 확인 중...");
            try {
                // 현재 카탈로그와 관련된 기존 릴리스만 확인
                String existingReleaseName = getReleaseNameFromHistory(catalog.getId(), clusterName, namespace);
                if (existingReleaseName != null) {
                    log.info("기존 릴리스 발견: {} (상태: 확인 중)", existingReleaseName);
                    try {
                        // 기존 릴리스 상태 확인
                        List<Release> existingReleases = Helm.list()
                                .withKubeConfig(tempKubeconfigPath)
                                .withNamespace(namespace)
                                .call();
                        
                        boolean found = false;
                        for (Release release : existingReleases) {
                            if (release.getName().equals(existingReleaseName)) {
                                log.info("기존 릴리스 상태: {} (상태: {})", existingReleaseName, release.getStatus());
                                found = true;
                                
                                // 기존 릴리스 삭제
                                Helm.uninstall(existingReleaseName)
                                        .withKubeConfig(tempKubeconfigPath)
                                        .withNamespace(namespace)
                                        .call();
                                log.info("기존 릴리스 삭제 완료: {}", existingReleaseName);
                                break;
                            }
                        }
                        
                        if (!found) {
                            log.info("기존 릴리스가 실제로 존재하지 않습니다: {}", existingReleaseName);
                        }
                    } catch (Exception e) {
                        log.warn("기존 릴리스 삭제 중 오류 발생: {}", e.getMessage());
                    }
                } else {
                    log.info("기존 릴리스가 없습니다. 새로 설치를 진행합니다.");
                }
            } catch (Exception e) {
                log.warn("기존 릴리스 확인 중 오류 발생. 새로 설치를 진행합니다. 오류: {}", e.getMessage());
            }

            // 6. Helm Repository 추가
            String repositoryName = helmChart.getRepositoryName();
            String chartRepositoryUrl = helmChart.getChartRepositoryUrl();
            log.info("7. Helm 명령어 생성 중... Chart: {}, Version: {}", helmChart.getChartName(), helmChart.getChartVersion());

            try {
                Helm.repo().add()
                        .withName(repositoryName)
                        .withUrl(URI.create(chartRepositoryUrl))
                        .call();
                log.info("Helm Repository 추가 완료: {}", repositoryName);
            } catch (Exception e) {
                log.warn("Helm Repository 추가 중 오류 발생 (이미 존재할 수 있음): {}", e.getMessage());
            }

            // 7. 새 릴리스 설치
            log.info("8. 새 릴리스 설치 실행 중...");
            String chartRef = repositoryName + "/" + helmChart.getChartName();

            InstallCommand installCommand = Helm.install(chartRef)
                    .withKubeConfig(tempKubeconfigPath)
                    .withName(releaseName)
                    .withNamespace(namespace)
                    .withVersion(helmChart.getChartVersion())
                    .set("replicaCount", catalog.getMinReplicas());
            
            // 이미지 설정 - Chart별로 다르게 처리
            String imageRepository = buildImageRepository(catalog, helmChart);
            if (helmChart.getChartName().equalsIgnoreCase("grafana")) {
                // Grafana의 경우 기본 이미지 사용
                installCommand
                        .set("image.repository", "grafana/grafana")
                        .set("image.tag", "latest")
                        .set("image.pullPolicy", "IfNotPresent");
            } else {
                // 다른 Chart의 경우 동적으로 설정
                installCommand
                        .set("image.repository", imageRepository)
                        .set("image.tag", "latest")
                        .set("image.pullPolicy", "IfNotPresent")
                        .set("image.registry", "docker.io");
            }
            
            // Bitnami 차트의 보안 검증 우회 설정
            installCommand
                    .set("global.security.allowInsecureImages", true)
                    .set("global.imageRegistry", "docker.io");
            
            installCommand
                    .set("service.port", catalog.getDefaultPort())
                    .set("service.type", "ClusterIP")
                    .set("resources.requests.cpu", catalog.getMinCpu().toString())
                    .set("resources.requests.memory", (int)(catalog.getMinMemory() * 1024) + "Mi")
                    .set("resources.limits.cpu", catalog.getRecommendedCpu().toString())
                    .set("resources.limits.memory", (int)(catalog.getRecommendedMemory() * 1024) + "Mi")
                    .set("persistence.enabled", false)
                    .set("securityContext.runAsNonRoot", false)
                    .set("containerSecurityContext.allowPrivilegeEscalation", false);

            // Ingress 설정 적용
            if (catalog.getIngressEnabled() != null && catalog.getIngressEnabled()) {
                log.info("Ingress 설정 적용 중...");
                
                // Ingress Controller 자동 설치 확인 및 설치
                // ensureIngressController(namespace, tempKubeconfigPath); // TODO 수정필요.
                
                installCommand
                        .set("ingress.enabled", true)
                        .set("ingress.host", catalog.getIngressHost() != null ? catalog.getIngressHost() : "localhost")
                        .set("ingress.path", catalog.getIngressPath() != null ? catalog.getIngressPath() : "/")
                        .set("ingress.className", catalog.getIngressClass() != null ? catalog.getIngressClass() : "nginx");
                
                // TLS 설정
                if (catalog.getIngressTlsEnabled() != null && catalog.getIngressTlsEnabled()) {
                    installCommand
                            .set("ingress.tls.enabled", true)
                            .set("ingress.tls.secretName", catalog.getIngressTlsSecret() != null ? 
                                catalog.getIngressTlsSecret() : releaseName + "-tls");
                }
                // TLS가 비활성화된 경우는 아무 설정도 하지 않음 (기본값 사용)
                
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
                installCommand
                        .set("autoscaling.enabled", true)
                        .set("autoscaling.minReplicas", catalog.getMinReplicas())
                        .set("autoscaling.maxReplicas", catalog.getMaxReplicas())
                                .set("autoscaling.targetCPUUtilizationPercentage", catalog.getCpuThreshold())
                                .set("autoscaling.targetMemoryUtilizationPercentage", catalog.getMemoryThreshold());
                }

                // CSP별 설정 적용
                switch (providerName.toUpperCase()) {
                    case "AWS":
                        log.info("AWS 특화 설정 적용 중...");
                        installCommand
                                .set("service.type", "LoadBalancer")
                                .set("persistence.enabled", true)
                                .set("persistence.storageClass", "gp2")
                                .set("persistence.size", "20Gi")
                                .set("persistence.accessMode", "ReadWriteOnce");
                        break;
                    case "AZURE":
                        log.info("Azure 특화 설정 적용 중...");
                        installCommand
                                .set("service.type", "LoadBalancer")
                                .set("persistence.enabled", true)
                                .set("persistence.storageClass", "managed-csi")
                                .set("persistence.size", "20Gi")
                                .set("persistence.accessMode", "ReadWriteOnce");
                        break;
                    case "GCP":
                        log.info("GCP 특화 설정 적용 중...");
                        installCommand
                                .set("service.type", "LoadBalancer")
                                .set("persistence.enabled", true)
                                .set("persistence.storageClass", "standard-rwo")
                                .set("persistence.size", "20Gi")
                                .set("persistence.accessMode", "ReadWriteOnce");
                        break;
                    default:
                        log.info("기본 설정 적용 중...");
                        installCommand
                                .set("service.type", "ClusterIP")
                                .set("persistence.enabled", false);
                        break;
                }

                // 공통 보안 설정 적용
                log.info("공통 보안 설정 적용 중...");
                installCommand
                        .set("podSecurityPolicy.enabled", false)
                        .set("rbac.create", false)
                        .set("serviceAccount.create", false)
                        .set("serviceAccount.name", "default");

            } catch (Exception e) {
                log.warn("CSP별 설정 적용 중 오류 발생: {}", e.getMessage());
            }

            Release result = installCommand.call();
            log.info("Helm Chart '{}' 설치 완료 (HPA: {})", releaseName, catalog.getHpaEnabled());
            log.info("=== Helm Chart 배포 성공 ===");
            log.info("릴리스명: {}, 상태: {}, 네임스페이스: {}", releaseName, result.getStatus(), namespace);

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

        try {
            // 1. 클러스터 정보 조회
            K8sClusterDto clusterDto = cbtumblebugRestApi.getK8sClusterByName(namespace, clusterName);
            if (clusterDto == null) {
                throw new RuntimeException("K8s cluster not found: " + clusterName);
            }

            // 2. kubeconfig 파일 생성
            String providerName = clusterDto.getConnectionConfig().getProviderName();
            KubeConfigProvider provider = providerFactory.getProvider(providerName);
            String kubeconfigYaml = provider.getOriginalKubeconfigYaml(clusterDto);
            
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

            // 7. Helm Chart 설치 명령어 구성
            String chartRef = repositoryName + "/" + helmChart.getChartName();

            InstallCommand installCommand = Helm.install(chartRef)
                    .withKubeConfig(tempKubeconfigPath)
                    .withName(releaseName)
                    .withNamespace(namespace)
                    .withVersion(helmChart.getChartVersion())
                    .set("replicaCount", config.getMinReplicas());
            
            // 이미지 설정 - Chart별로 다르게 처리
            String imageRepository = buildImageRepository(catalog, helmChart);
            if (helmChart.getChartName().equalsIgnoreCase("grafana")) {
                // Grafana의 경우 기본 이미지 사용
                installCommand
                        .set("image.repository", "grafana/grafana")
                        .set("image.tag", "latest")
                        .set("image.pullPolicy", "IfNotPresent");
            } else {
                // 다른 Chart의 경우 동적으로 설정
                installCommand
                        .set("image.repository", imageRepository)
                        .set("image.tag", "latest")
                        .set("image.pullPolicy", "IfNotPresent")
                        .set("image.registry", "docker.io");
            }
            
            // Bitnami 차트의 보안 검증 우회 설정
            installCommand
                    .set("global.security.allowInsecureImages", true)
                    .set("global.imageRegistry", "docker.io");
            
            installCommand
                    .set("service.port", catalog.getDefaultPort())
                    .set("service.type", "ClusterIP")
                    .set("resources.requests.cpu", catalog.getMinCpu().toString())
                    .set("resources.requests.memory", (int)(catalog.getMinMemory() * 1024) + "Mi")
                    .set("resources.limits.cpu", catalog.getRecommendedCpu().toString())
                    .set("resources.limits.memory", (int)(catalog.getRecommendedMemory() * 1024) + "Mi")
                    .set("persistence.enabled", false)
                    .set("securityContext.runAsNonRoot", false)
                    .set("containerSecurityContext.allowPrivilegeEscalation", false);

            // HPA 설정 적용
            if (config.isHpaEnabled()) {
                log.info("HPA 설정 적용 중...");
                installCommand
                        .set("autoscaling.enabled", true)
                        .set("autoscaling.minReplicas", config.getMinReplicas())
                        .set("autoscaling.maxReplicas", config.getMaxReplicas())
                        .set("autoscaling.targetCPUUtilizationPercentage", config.getCpuThreshold())
                        .set("autoscaling.targetMemoryUtilizationPercentage", config.getMemoryThreshold());
                log.info("HPA 설정 완료 - {}", config.getHpaConfigSummary());
            } else {
                installCommand.set("autoscaling.enabled", false);
                log.info("HPA 비활성화됨");
            }

            // Ingress 설정 적용
            if (config.isIngressEnabled()) {
                log.info("Ingress 설정 적용 중...");
                
                // Ingress Controller 자동 설치 확인 및 설치
                ensureIngressController(namespace, tempKubeconfigPath);
                
                installCommand
                        .set("ingress.enabled", true)
                        .set("ingress.host", config.getIngressHost())
                        .set("ingress.path", config.getIngressPath())
                        .set("ingress.className", config.getIngressClass());
                
                // TLS 설정
                if (config.isTlsEnabled()) {
                    installCommand
                            .set("ingress.tls.enabled", true)
                            .set("ingress.tls.secretName", config.getIngressTlsSecret() != null ? 
                                config.getIngressTlsSecret() : releaseName + "-tls");
                }
                // TLS가 비활성화된 경우는 아무 설정도 하지 않음 (기본값 사용)
                
                log.info("Ingress 설정 완료 - {}", config.getIngressConfigSummary());
            } else {
                installCommand.set("ingress.enabled", false);
                log.info("Ingress 비활성화됨");
            }

            // CSP별 및 설정에 따른 동적 설정 적용
            log.info("CSP별 및 설정에 따른 동적 설정 적용 중...");
            try {

                // 공통 보안 설정 적용
                log.info("공통 보안 설정 적용 중...");
                installCommand
                        .set("podSecurityPolicy.enabled", false)
                        .set("rbac.create", false)
                        .set("serviceAccount.create", false)
                        .set("serviceAccount.name", "default");

            } catch (Exception e) {
                log.warn("CSP별 설정 적용 중 오류 발생: {}", e.getMessage());
            }

            Release result = installCommand.call();
            log.info("Helm Chart '{}' 설치 완료 (HPA: {}, Ingress: {})", 
                    releaseName, config.isHpaEnabled(), config.isIngressEnabled());
            log.info("=== Helm Chart 배포 성공 ===");
            log.info("릴리스명: {}, 상태: {}, 네임스페이스: {}", releaseName, result.getStatus(), namespace);

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

    public void uninstallHelmChart(String namespace, SoftwareCatalog catalog, String clusterName) {
        uninstallHelmChart(namespace, catalog, catalog.getHelmChart(), clusterName);
    }
    
    public void uninstallHelmChart(String namespace, SoftwareCatalog catalog, 
                                 kr.co.mcmp.softwarecatalog.application.model.HelmChart helmChart, String clusterName) {
        log.info("=== Helm Chart 삭제 시작 ===");
        log.info("Chart: {}, Namespace: {}, Cluster: {}", 
                helmChart.getChartName(), namespace, clusterName);
        
        try {
            String releaseName = getReleaseNameFromHistory(catalog.getId(), clusterName, namespace);
            if (releaseName == null) {
                log.warn("배포 히스토리에서 릴리스 이름을 찾을 수 없습니다. 차트 이름으로 시도합니다.");
                releaseName = helmChart.getChartName();
            }
            
            log.info("1. 릴리스 '{}' 삭제 실행 중...", releaseName);
            
            String result = Helm.uninstall(releaseName)
                    .withNamespace(namespace)
                    .call();

            boolean deleted = result != null && !result.isEmpty();
            log.info("2. 삭제 결과: {}", deleted ? "성공" : "실패");

            if (deleted) {
                log.info("=== Helm Chart 삭제 성공 ===");
            } else {
                log.warn("=== Helm Chart 삭제 실패 ===");
            }
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
            
            String result = Helm.uninstall(releaseName)
                    .withKubeConfig(tempKubeconfigPath)
                    .withNamespace(namespace)
                .call();

            boolean deleted = result != null && !result.isEmpty();
            log.info("2. 삭제 결과: {}", deleted ? "성공" : "실패");
            
            if (deleted) {
                log.info("=== Helm Chart 삭제 성공 ===");
            } else {
                log.warn("=== Helm Chart 삭제 실패 ===");
            }
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

    private Path createTempKubeconfigFile(String kubeconfigYaml) throws IOException {
        Path tempFile = Files.createTempFile("kubeconfig", ".yaml");
        Files.write(tempFile, kubeconfigYaml.getBytes(StandardCharsets.UTF_8));
        return tempFile;
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
                UninstallCommand uninstallCommand = Helm.uninstall(existingReleaseName)
                        .withKubeConfig(tempKubeconfigPath)
                        .withNamespace(namespace);
                
                try {
                    uninstallCommand.call();
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
    private void ensureIngressController(String namespace, Path tempKubeconfigPath) {
        try {
            log.info("네임스페이스 '" + namespace + "'에서 NGINX Ingress Controller 확인 중...");
            
            if (isIngressControllerInstalled(namespace, tempKubeconfigPath)) {
                log.info("NGINX Ingress Controller가 이미 설치되어 있습니다.");
                return;
            }
            
            log.info("NGINX Ingress Controller가 설치되어 있지 않습니다. 설치를 시작합니다...");
            installIngressControllerWithHelm(namespace, tempKubeconfigPath);
            
            // 설치 완료 대기
            waitForIngressControllerReady(namespace, tempKubeconfigPath);
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
            List<Release> releases = Helm.list()
                    .withKubeConfig(tempKubeconfigPath)
                    .withNamespace(namespace)
                    .call();
            
            for (Release release : releases) {
                if (release.getName().contains("nginx-ingress") || 
                    release.getName().contains("ingress-nginx")) {
                    log.info("NGINX Ingress Controller 발견: " + release.getName());
                    return true;
                }
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
            
            InstallCommand installCommand = Helm.install("ingress-nginx/ingress-nginx")
                    .withKubeConfig(tempKubeconfigPath)
                    .withName(releaseName)
                    .withNamespace(namespace)
                    .set("controller.service.type", "LoadBalancer")
                    .set("controller.service.ports.http", "80")
                    .set("controller.service.ports.https", "443")
                    .set("controller.ingressClassResource.name", "nginx")
                    .set("controller.ingressClassResource.controllerValue", "k8s.io/ingress-nginx")
                    .set("controller.ingressClass", "nginx")
                    .set("controller.ingressClassByName", true)
                    .set("controller.watchIngressWithoutClass", true)
                    .set("controller.ingressClassResource.enabled", true)
                    .set("controller.ingressClassResource.default", true);
            
            Release result = installCommand.call();
            log.info("NGINX Ingress Controller 설치 완료: " + releaseName + " (상태: " + result.getStatus() + ")");
            
        } catch (Exception e) {
            System.err.println("NGINX Ingress Controller Helm 설치 실패: " + e.getMessage());
            throw new RuntimeException("NGINX Ingress Controller 설치 실패", e);
        }
    }

    /**
     * NGINX Ingress Controller가 준비될 때까지 대기합니다.
     */
    private void waitForIngressControllerReady(String namespace, Path tempKubeconfigPath) {
        int maxAttempts = 30; // 5분 대기 (10초 * 30)
        int attempt = 0;
        
        log.info("NGINX Ingress Controller 준비 상태 확인 중...");
        
        while (attempt < maxAttempts) {
            try {
                if (isIngressControllerReady(namespace, tempKubeconfigPath)) {
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
    private boolean isIngressControllerReady(String namespace, Path tempKubeconfigPath) {
        try {
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

    /**
     * Helm repository를 추가합니다.
     */
    private void addHelmRepository(kr.co.mcmp.softwarecatalog.application.model.HelmChart helmChart) throws Exception {
        String chartRepositoryUrl = helmChart.getChartRepositoryUrl();
        String repositoryName = helmChart.getRepositoryName();
        
        log.info("Adding Helm repository: {} -> {}", repositoryName, chartRepositoryUrl);
        Helm.repo().add()
                .withName(repositoryName)
                .withUrl(URI.create(chartRepositoryUrl))
                .call();
        Helm.repo().update();
        log.info("Helm repository added and updated successfully");
    }
}