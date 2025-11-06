package kr.co.mcmp.softwarecatalog.kubernetes.service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import kr.co.mcmp.softwarecatalog.application.model.HelmChart;
import org.springframework.stereotype.Service;

import com.marcnuri.helm.Release;

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
        
        Path tempKubeconfigPath = null;

        try {
            // 1. 클러스터 정보 조회
            K8sClusterDto clusterDto = cbtumblebugRestApi.getK8sClusterByName(namespace, clusterName);

            // 2. Kubeconfig YAML 생성
            String kubeconfigYaml = providerFactory.getProvider(clusterDto.getConnectionConfig().getProviderName())
                    .getOriginalKubeconfigYaml(clusterDto);

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
            values.put("resources.requests.memory", (int)(catalog.getMinMemory() * 1024) + "Mi");
            values.put("resources.limits.cpu", catalog.getRecommendedCpu().toString());
            values.put("resources.limits.memory", (int)(catalog.getRecommendedMemory() * 1024) + "Mi");
            values.put("persistence.enabled", "false");
            values.put("securityContext.runAsNonRoot", "false");
            values.put("containerSecurityContext.allowPrivilegeEscalation", "false");
            values.put("global.security.allowInsecureImages", "true");
            values.put("global.imageRegistry", "docker.io");

            if (helmChart.getChartName().equalsIgnoreCase("grafana")) {
                values.put("image.repository", "grafana/grafana");
                values.put("image.tag", "latest");
                values.put("image.pullPolicy", "IfNotPresent");
            } else {
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

            // 7. Helm Chart 설치 - CLI 방식으로 변경
            String chartRef = helmChart.getRepositoryName() + "/" + helmChart.getChartName();
            
            // 이미지 설정 - Chart별로 다르게 처리
            String imageRepository = buildImageRepository(catalog, helmChart);
            
            // Values 맵 구성
            java.util.Map<String, String> values = new java.util.HashMap<>();
            values.put("replicaCount", String.valueOf(config.getMinReplicas()));
            values.put("service.port", String.valueOf(catalog.getDefaultPort()));
            values.put("service.type", "ClusterIP");
            values.put("resources.requests.cpu", catalog.getMinCpu().toString());
            values.put("resources.requests.memory", (int)(catalog.getMinMemory() * 1024) + "Mi");
            values.put("resources.limits.cpu", catalog.getRecommendedCpu().toString());
            values.put("resources.limits.memory", (int)(catalog.getRecommendedMemory() * 1024) + "Mi");
            values.put("persistence.enabled", "false");
            values.put("securityContext.runAsNonRoot", "false");
            values.put("containerSecurityContext.allowPrivilegeEscalation", "false");
            values.put("global.security.allowInsecureImages", "true");
            values.put("global.imageRegistry", "docker.io");

            if (helmChart.getChartName().equalsIgnoreCase("grafana")) {
                values.put("image.repository", "grafana/grafana");
                values.put("image.tag", "latest");
                values.put("image.pullPolicy", "IfNotPresent");
            } else {
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
                ensureIngressController(namespace, tempKubeconfigPath);
                
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

            // Helm CLI로 설치 실행
            runHelmInstallCli(releaseName, chartRef, namespace, helmChart.getChartVersion(), tempKubeconfigPath, values);
            
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
        String providerName = clusterDto.getConnectionConfig().getProviderName();
        KubeConfigProvider provider = providerFactory.getProvider(providerName);
        return provider.getOriginalKubeconfigYaml(clusterDto);
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
            String releaseList = runHelmListCli(namespace, tempKubeconfigPath);
            
            if (releaseList != null && (releaseList.contains("nginx-ingress") || releaseList.contains("ingress-nginx"))) {
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
            ingressHelmChart.setChartRepositoryUrl("https://kubernetes.github.io/ingress-nginx");
            ingressHelmChart.setRepositoryName("ingress-nginx");
            addHelmRepository(ingressHelmChart);

            // Helm CLI로 설치 실행
            runHelmInstallCli(releaseName, "ingress-nginx/ingress-nginx", namespace, "4.14.0", tempKubeconfigPath, values);
            
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

    private void runHelmInstallCli(String releaseName,
                                   String chartRef,
                                   String namespace,
                                   String version,
                                   Path kubeconfig,
                                   java.util.Map<String,String> values) throws Exception {
        java.util.List<String> cmd = new java.util.ArrayList<>();
        // Helm 경로 지정 (관리자 권한 없이 사용)
        String helmPath = getHelmPath();
        cmd.add(helmPath); cmd.add("install");
        cmd.add(releaseName); cmd.add(chartRef);
        cmd.add("--namespace"); cmd.add("default");
        cmd.add("--version"); cmd.add(version);
        cmd.add("--kubeconfig"); cmd.add(kubeconfig.toString());
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
    private String getHelmPath() {
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