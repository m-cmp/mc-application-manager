package kr.co.mcmp.softwarecatalog.application.service.impl;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import kr.co.mcmp.ape.cbtumblebug.api.CbtumblebugRestApi;
import kr.co.mcmp.ape.cbtumblebug.dto.VmAccessInfo;
import kr.co.mcmp.ape.cbtumblebug.dto.VmSpecDto;
import kr.co.mcmp.softwarecatalog.CatalogService;
import kr.co.mcmp.softwarecatalog.SoftwareCatalogDTO;
import kr.co.mcmp.softwarecatalog.application.constants.ApplicationStatusValues;
import kr.co.mcmp.softwarecatalog.application.constants.DeploymentType;
import kr.co.mcmp.softwarecatalog.application.constants.LogType;
import kr.co.mcmp.softwarecatalog.application.constants.VmDeploymentMode;
import kr.co.mcmp.softwarecatalog.application.dto.DeploymentParameters;
import kr.co.mcmp.softwarecatalog.application.dto.DeploymentRequest;
import kr.co.mcmp.softwarecatalog.application.dto.ObjectStorageConfiguration;
import kr.co.mcmp.softwarecatalog.application.exception.ApplicationException;
import kr.co.mcmp.softwarecatalog.application.model.DeploymentHistory;
import kr.co.mcmp.softwarecatalog.application.model.InfraSpecSnapshot;
import kr.co.mcmp.softwarecatalog.application.repository.DeploymentHistoryRepository;
import kr.co.mcmp.softwarecatalog.application.repository.InfraSpecSnapshotRepository;
import kr.co.mcmp.softwarecatalog.application.service.ApplicationHistoryService;
import kr.co.mcmp.softwarecatalog.application.service.DeploymentService;
import kr.co.mcmp.softwarecatalog.application.service.ObjectStorageAccessGrantService;
import kr.co.mcmp.softwarecatalog.application.service.tunnel.ObjectStorageTunnelService;
import kr.co.mcmp.softwarecatalog.application.service.ObjectStorageAccessGrantService.IssuedAccess;
import kr.co.mcmp.softwarecatalog.application.service.VmSecurityGroupExposureService;
import kr.co.mcmp.softwarecatalog.application.config.NexusConfig;
import kr.co.mcmp.softwarecatalog.docker.model.ContainerDeployResult;
import kr.co.mcmp.softwarecatalog.docker.model.DockerHostResourceInfo;
import kr.co.mcmp.softwarecatalog.docker.model.DockerTarget;
import kr.co.mcmp.softwarecatalog.docker.service.DockerOperationService;
import kr.co.mcmp.softwarecatalog.docker.service.DockerSetupService;
import kr.co.mcmp.softwarecatalog.users.Entity.User;
import kr.co.mcmp.softwarecatalog.users.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Docker 배포를 담당하는 서비스
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DockerDeploymentService implements DeploymentService {
    
    private final CatalogService catalogService;
    private final DockerSetupService dockerSetupService;
    private final DockerOperationService dockerOperationService;
    private final CbtumblebugRestApi cbtumblebugRestApi;
    private final VmSecurityGroupExposureService vmSecurityGroupExposureService;
    private final ApplicationHistoryService applicationHistoryService;
    private final DeploymentHistoryRepository deploymentHistoryRepository;
    private final InfraSpecSnapshotRepository infraSpecSnapshotRepository;
    private final UserService userService;
    private final NexusConfig nexusConfig;
    private final ObjectStorageAccessGrantService objectStorageAccessGrantService;
    private final ObjectStorageTunnelService objectStorageTunnelService;
    private final ObjectMapper objectMapper;

    
    // 비동기 처리를 위한 스레드 풀
    private final Executor asyncExecutor = Executors.newFixedThreadPool(10);
    
    
    /**
     * 메시지 타입 열거형
     */
    private enum MessageType {
        SUCCESS, ERROR, BATCH_SUCCESS
    }
    
    /**
     * 애플리케이션 타입 열거형
     */
    private enum ApplicationType {
        ELASTICSEARCH, REDIS, GENERIC
    }
    
    

    @Override
    public DeploymentHistory deployApplication(DeploymentRequest request) {
        // PackageInfo가 포함된 완전한 카탈로그 정보 조회
        SoftwareCatalogDTO catalog = catalogService.getCatalog(request.getCatalogId());
        
        if (catalog.getPackageInfo() == null) {
            log.error("PackageInfo is null for catalog ID: {}", request.getCatalogId());
        }
        
        User user = userService.findUserByUsername(request.getUsername()).orElse(null);
        
        // 다중 VM 배포의 경우 VM별 DeploymentHistory 생성
        if (request.getVmIds() != null && request.getVmIds().size() > 1) {
            return deployToMultipleVms(request, catalog, user);
        } else {
            // 단일 VM 배포의 경우 기존 로직 사용
            DeploymentHistory history = applicationHistoryService.createDeploymentHistory(request, user);
            history = deploymentHistoryRepository.save(history);
            
            try {
                deployToVms(request, catalog, history, user);
                if ("SUCCESS".equals(history.getStatus()) || "PARTIAL_SUCCESS".equals(history.getStatus())) {
                    history.setResourceType(request.getResourceType());
                    deploymentHistoryRepository.save(history);
                    saveInfraSpecSnapshot(history, request, catalog);
                }
            } catch (Exception e) {
                log.error("Deployment failed", e);
                history.setStatus("FAILED");
                history.setUpdatedAt(LocalDateTime.now());
                deploymentHistoryRepository.save(history);
                applicationHistoryService.updateApplicationStatus(history, "FAILED", user);
                applicationHistoryService.addDeploymentLog(history, LogType.ERROR, "Deployment failed: " + e.getMessage());
            }

            return history;
        }
    }
    
    /**
     * 다중 VM 배포 - VM별 DeploymentHistory 생성
     */
    private DeploymentHistory deployToMultipleVms(DeploymentRequest request, SoftwareCatalogDTO catalog, User user) {
        List<String> vmIds = request.getVmIds();
        DeploymentHistory firstHistory = null;
        
        // 기존 설치 확인
        List<String> alreadyInstalledVms = checkExistingInstallations(request, catalog, vmIds);
        if (!alreadyInstalledVms.isEmpty()) {
            log.info("Found existing installations on VMs: {}", alreadyInstalledVms);
        }
        if (alreadyInstalledVms.size() == vmIds.size()) {
            throw new ApplicationException("Application is already installed on every selected VM");
        }
        
        // 클러스터 설정 생성 (클러스터링 모드인 경우에만)
        final Map<String, String> clusterConfig = request.getVmDeploymentMode() == VmDeploymentMode.CLUSTERING ? buildClusterConfig(request, catalog, vmIds) : null;
        
        // VM별 배포 작업 생성
        List<CompletableFuture<DeploymentResult>> deploymentFutures = new ArrayList<>();
        Map<String, DeploymentHistory> vmHistories = new HashMap<>();
        
        for (int i = 0; i < vmIds.size(); i++) {
            final String vmId = vmIds.get(i);
            final int vmIndex = i;
            
            // 기존 설치가 있는 VM은 건너뛰기
            if (alreadyInstalledVms.contains(vmId)) {
                log.info("Skipping deployment for VM {} due to existing installation", vmId);
                continue;
            }
            
            // VM별 DeploymentHistory 생성
            DeploymentHistory vmHistory = applicationHistoryService.createDeploymentHistoryForVm(request, vmId, user);
            deploymentHistoryRepository.save(vmHistory);
            applicationHistoryService.createApplicationStatusForVm(
                    vmHistory,
                    vmId,
                    vmHistory.getPublicIp(),
                    request.getServicePort(),
                    ApplicationStatusValues.PREPARING_RUNTIME,
                    user);
            vmHistories.put(vmId, vmHistory);
            
            if (firstHistory == null) {
                firstHistory = vmHistory;
            }
            
            CompletableFuture<DeploymentResult> future = CompletableFuture.supplyAsync(() -> {
                return deployToSingleVmAsync(request, catalog, vmHistory, user, vmId, vmIndex, vmIds, clusterConfig);
            }, asyncExecutor);
            
            deploymentFutures.add(future);
        }
        
        // 모든 배포 작업 완료 대기
        try {
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                deploymentFutures.toArray(new CompletableFuture[0])
            );
            
            // Each remote request/pull is bounded. Do not mark active workers
            // failed while queued VMs or large downloads are still running.
            allFutures.join();
            
            // 배포 결과 처리
            List<String> successfulVms = new ArrayList<>();
            List<String> failedVms = new ArrayList<>();
            
            for (CompletableFuture<DeploymentResult> deploymentFuture : deploymentFutures) {
                try {
                    DeploymentResult result = deploymentFuture.get();
                    String vmId = result.getVmId();
                    DeploymentHistory vmHistory = vmHistories.get(vmId);
                    
                    if (vmHistory == null) continue;
                    
                    VmAccessInfo vmAccessInfo = cbtumblebugRestApi.getVmInfo(request.getNamespace(), request.getMciId(), vmId);
                    Integer servicePort = request.getServicePort();
                    
                    if (result.isSuccess()) {
                        successfulVms.add(vmId);
                        vmHistory.setStatus("SUCCESS");
                        vmHistory.setContainerId(result.getContainerId());
                        vmHistory.setResourceType(request.getResourceType());
                        vmHistory.setUpdatedAt(LocalDateTime.now());
                        deploymentHistoryRepository.save(vmHistory);
                        saveInfraSpecSnapshot(vmHistory, request, catalog);
                        
                        String logMessage = createMessage(request, MessageType.SUCCESS, vmId, "deployment completed");
                        applicationHistoryService.addDeploymentLog(vmHistory, LogType.INFO, logMessage);
                        
                        // 성공한 VM의 ApplicationStatus 생성
                        applicationHistoryService.createApplicationStatusForVm(
                            vmHistory, vmId, vmAccessInfo.getPublicIP(), servicePort, "SUCCESS", user,
                            result.getContainerId());
                    } else {
                        failedVms.add(vmId + " (" + result.getErrorMessage() + ")");
                        vmHistory.setStatus("FAILED");
                        vmHistory.setUpdatedAt(LocalDateTime.now());
                        deploymentHistoryRepository.save(vmHistory);
                        
                        String errorMessage = createMessage(request, MessageType.ERROR, vmId, result.getErrorMessage());
                        applicationHistoryService.addDeploymentLog(vmHistory, LogType.ERROR, errorMessage);
                        
                        // 실패한 VM의 ApplicationStatus 생성
                        applicationHistoryService.createApplicationStatusForVm(
                            vmHistory, vmId, vmAccessInfo.getPublicIP(), servicePort, "FAILED", user);
                    }
                } catch (Exception e) {
                    log.error("Failed to get deployment result", e);
                    failedVms.add("Unknown VM (future failed)");
                }
            }
            
            // 배포 결과 처리
            processDeploymentResults(firstHistory, user, successfulVms, failedVms, request);
            
        } catch (Exception e) {
            log.error("Deployment timeout or error", e);
            // 모든 VM History를 FAILED로 설정
            for (DeploymentHistory vmHistory : vmHistories.values()) {
                vmHistory.setStatus("FAILED");
                vmHistory.setUpdatedAt(LocalDateTime.now());
                deploymentHistoryRepository.save(vmHistory);
                applicationHistoryService.createApplicationStatusForVm(
                        vmHistory,
                        vmHistory.getVmId(),
                        vmHistory.getPublicIp(),
                        request.getServicePort(),
                        "FAILED",
                        user);
                applicationHistoryService.addDeploymentLog(vmHistory, LogType.ERROR, "Deployment timeout or error: " + e.getMessage());
            }
        }
        
        return firstHistory;
    }
    
    /**
     * 통합 VM 배포 로직 - 비동기 병렬 처리로 개선
     */
    private void deployToVms(DeploymentRequest request, SoftwareCatalogDTO catalog,
                            DeploymentHistory history, User user) {
        List<String> vmIds = request.getVmIds();
        
        if (vmIds == null || vmIds.isEmpty()) {
            throw new ApplicationException("No VM IDs provided for deployment");
        }
        
        // 기존 설치 확인
        List<String> alreadyInstalledVms = checkExistingInstallations(request, catalog, vmIds);
        if (!alreadyInstalledVms.isEmpty()) {
            log.info("Found existing installations on VMs: {}", alreadyInstalledVms);
            applicationHistoryService.addDeploymentLog(history, LogType.WARNING, 
                "Skipping deployment on VMs with existing installations: " + String.join(", ", alreadyInstalledVms));
        }
        
        // 클러스터 설정 생성 (클러스터링 모드인 경우에만)
        final Map<String, String> clusterConfig = request.getVmDeploymentMode() == VmDeploymentMode.CLUSTERING ? buildClusterConfig(request, catalog, vmIds) : null;
        
        // 비동기 배포 작업 생성 (기존 설치가 없는 VM만)
        List<CompletableFuture<DeploymentResult>> deploymentFutures = new ArrayList<>();
        
        for (int i = 0; i < vmIds.size(); i++) {
            final String vmId = vmIds.get(i);
            final int vmIndex = i;
            
            // 기존 설치가 있는 VM은 건너뛰기
            if (alreadyInstalledVms.contains(vmId)) {
                log.info("Skipping deployment for VM {} due to existing installation", vmId);
                continue;
            }

            applicationHistoryService.createApplicationStatusForVm(
                    history,
                    vmId,
                    history.getPublicIp(),
                    request.getServicePort(),
                    ApplicationStatusValues.PREPARING_RUNTIME,
                    user);
            CompletableFuture<DeploymentResult> future = CompletableFuture.supplyAsync(() -> {
                return deployToSingleVmAsync(request, catalog, history, user, vmId, vmIndex, vmIds, clusterConfig);
            }, asyncExecutor);
            
            deploymentFutures.add(future);
        }
        
        // 모든 배포 작업 완료 대기
        CompletableFuture<Void> allDeployments = CompletableFuture.allOf(
            deploymentFutures.toArray(new CompletableFuture[0])
        );
        
        try {
            // Use the same completion policy for single- and multi-VM installs.
            allDeployments.join();
            
            // 결과 수집 및 VM별 ApplicationStatus 생성
            List<String> successfulVms = new ArrayList<>();
            List<String> failedVms = new ArrayList<>();
            
            for (int i = 0; i < deploymentFutures.size(); i++) {
                try {
                    CompletableFuture<DeploymentResult> future = deploymentFutures.get(i);
                    DeploymentResult result = future.get();
                    String vmId = result.getVmId();
                    
                    // VM 정보 조회
                    VmAccessInfo vmAccessInfo = cbtumblebugRestApi.getVmInfo(request.getNamespace(), request.getMciId(), vmId);
                    Integer servicePort = request.getServicePort(); // 원래 포트 사용 (각 VM은 독립적인 서버)
                    
                    if (result.isSuccess()) {
                        successfulVms.add(vmId);
                        String logMessage = createMessage(request, MessageType.SUCCESS, vmId, "deployment completed");
                        applicationHistoryService.addDeploymentLog(history, LogType.INFO, logMessage);
                        
                        // 성공한 VM의 ApplicationStatus 생성
                        applicationHistoryService.createApplicationStatusForVm(
                            history, vmId, vmAccessInfo.getPublicIP(), servicePort, "SUCCESS", user,
                            result.getContainerId());
                    } else {
                        failedVms.add(vmId + " (" + result.getErrorMessage() + ")");
                        String errorMessage = createMessage(request, MessageType.ERROR, vmId, result.getErrorMessage());
                        applicationHistoryService.addDeploymentLog(history, LogType.ERROR, errorMessage);
                        
                        // 실패한 VM의 ApplicationStatus 생성
                        applicationHistoryService.createApplicationStatusForVm(
                            history, vmId, vmAccessInfo.getPublicIP(), servicePort, "FAILED", user);
                    }
                } catch (Exception e) {
                    log.error("Failed to get deployment result", e);
                    failedVms.add("Unknown VM (future failed)");
                    applicationHistoryService.addDeploymentLog(history, LogType.ERROR, "Failed to get deployment result: " + e.getMessage());
                }
            }
            
            // 배포 결과 처리
            processDeploymentResults(history, user, successfulVms, failedVms, request);
            
        } catch (Exception e) {
            log.error("Deployment timeout or error", e);
            history.setStatus("FAILED");
            history.setUpdatedAt(LocalDateTime.now());
            deploymentHistoryRepository.save(history);
            applicationHistoryService.createApplicationStatusForVm(
                    history,
                    history.getVmId(),
                    history.getPublicIp(),
                    request.getServicePort(),
                    "FAILED",
                    user);
            applicationHistoryService.addDeploymentLog(history, LogType.ERROR, 
                "Deployment failed due to timeout or error: " + e.getMessage());
        }
    }
    
    /**
     * 단일 VM 비동기 배포
     */
    private DeploymentResult deployToSingleVmAsync(DeploymentRequest request, SoftwareCatalogDTO catalog,
                                                  DeploymentHistory history, User user, String vmId, int vmIndex,
                                                  List<String> vmIds, Map<String, String> clusterConfig) {
        try {
            log.info("Starting async deployment for VM: {}", vmId);
            
            // Docker 설치 확인 및 설치
            dockerSetupService.checkAndInstallDocker(request.getNamespace(), request.getMciId(), vmId);
            
            // 배포 파라미터 생성
            DeploymentParameters deployParams = createDeployParameters(
                    request, catalog, history, vmId, vmIndex, vmIds, clusterConfig);
            VmAccessInfo vmAccessInfo = cbtumblebugRestApi.getVmInfo(request.getNamespace(), request.getMciId(), vmId);
            applicationHistoryService.createApplicationStatusForVm(
                    history,
                    vmId,
                    vmAccessInfo.getPublicIP(),
                    request.getServicePort(),
                    ApplicationStatusValues.DEPLOYING,
                    user);
            
            // Peer IPs must be passed only for an explicitly requested cluster.
            // DockerOperationService interprets a non-empty peer list as Redis /
            // Elasticsearch clustering configuration, while NodeGroup deployment
            // intentionally creates independent standalone instances.
            List<String> vmPublicIps = new ArrayList<>();
            int clusterNodeIndex = -1;
            if (request.getVmDeploymentMode() == VmDeploymentMode.CLUSTERING) {
                clusterNodeIndex = vmIndex;
                for (String id : vmIds) {
                    try {
                        VmAccessInfo vmInfo = cbtumblebugRestApi.getVmInfo(request.getNamespace(), request.getMciId(), id);
                        vmPublicIps.add(vmInfo.getPublicIP());
                    } catch (Exception e) {
                        log.warn("Failed to get VM info for {}: {}", id, e.getMessage());
                    }
                }
            }
            
            // Docker 컨테이너 실행 (클러스터링 지원)
            DockerTarget dockerTarget = new DockerTarget(
                    request.getNamespace(), request.getMciId(), vmId);
            applicationHistoryService.addDeploymentLog(history, LogType.INFO,
                    "VM " + vmId + ": preparing the container image. The first download may take several minutes; "
                            + "container startup, Object Storage connection and inbound rules follow after it completes.");
            ContainerDeployResult deployResult = dockerOperationService.runDockerContainer(
                dockerTarget,
                convertToMap(deployParams, catalog.getId(), history.getId()),
                deployParams.getEnvironmentVariables(),
                deployParams.getVolumeMounts(),
                deployParams.getCommandArguments(),
                vmPublicIps,
                clusterNodeIndex
            );
            
            String containerId = deployResult.getContainerId();
            if (containerId != null && !containerId.isEmpty()) {
                boolean isRunning = dockerOperationService.isContainerRunning(dockerTarget, containerId);
                if (isRunning) {
                    history.setContainerId(containerId);
                    history.setVmId(vmId);
                    history.setPublicIp(vmAccessInfo.getPublicIP());
                    deploymentHistoryRepository.save(history);
                    try {
                        if (deployParams.getEnvironmentVariables() != null
                                && deployParams.getEnvironmentVariables().containsKey("MCMP_OBJECT_STORAGE_TOKEN")) {
                            objectStorageTunnelService.install(history.getId(), dockerTarget, containerId);
                            applicationHistoryService.addDeploymentLog(history, LogType.INFO,
                                    "Object Storage gateway setup completed for Jupyter.");
                        }
                        vmSecurityGroupExposureService.addRestrictedInboundRule(request, vmAccessInfo, history);
                    } catch (RuntimeException exposureError) {
                        try {
                            objectStorageTunnelService.remove(history.getId());
                        } catch (RuntimeException cleanupError) {
                            exposureError.addSuppressed(cleanupError);
                        }
                        try {
                            dockerOperationService.removeDockerContainer(dockerTarget, containerId);
                        } catch (RuntimeException rollbackError) {
                            exposureError.addSuppressed(rollbackError);
                            log.warn(
                                    "Failed to remove container {} after network exposure failure on VM {}",
                                    containerId,
                                    vmId,
                                    rollbackError);
                        }
                        throw exposureError;
                    }
                    history.setContainerId(containerId);
                    history.setVmId(vmId);
                    history.setPublicIp(vmAccessInfo.getPublicIP());
                    history.setUpdatedAt(LocalDateTime.now());
                    deploymentHistoryRepository.save(history);
                    log.info("Async deployment successful for VM: {} with container: {}", vmId, containerId);
                    return new DeploymentResult(vmId, true, containerId, null);
                } else {
                    String errorMsg = "Container is not running";
                    log.error("Async deployment failed for VM: {} - {}", vmId, errorMsg);
                    objectStorageAccessGrantService.revoke(history.getId(), vmId);
                    return new DeploymentResult(vmId, false, null, errorMsg);
                }
            } else {
                String errorMsg = deployResult.getDeploymentResult();
                if (errorMsg == null || errorMsg.isBlank()) {
                    errorMsg = "Container deployment returned no container ID";
                }
                log.error("Async deployment failed for VM: {} - {}", vmId, errorMsg);
                objectStorageAccessGrantService.revoke(history.getId(), vmId);
                return new DeploymentResult(vmId, false, null, errorMsg);
            }
            
        } catch (Exception e) {
            log.error("Async deployment failed for VM: {}", vmId, e);
            objectStorageAccessGrantService.revoke(history.getId(), vmId);
            try {
                objectStorageTunnelService.remove(history.getId());
            } catch (RuntimeException cleanupError) {
                log.warn("Object Storage tunnel cleanup queued for deployment {}", history.getId());
            }
            String errorMessage = e.getMessage();
            try {
                vmSecurityGroupExposureService.releaseRestrictedInboundRule(history.getId());
            } catch (RuntimeException cleanupError) {
                e.addSuppressed(cleanupError);
                errorMessage = errorMessage + "; failed to roll back inbound rule: " + cleanupError.getMessage();
                log.error("Failed to roll back VM Security Group exposure for deploymentId={}",
                        history.getId(), cleanupError);
            }
            return new DeploymentResult(vmId, false, null, errorMessage);
        }
    }
    
    /**
     * 기존 설치가 있는 VM들을 확인합니다.
     */
    private List<String> checkExistingInstallations(DeploymentRequest request, SoftwareCatalogDTO catalog, List<String> vmIds) {
        List<String> alreadyInstalledVms = new ArrayList<>();
        
        for (String vmId : vmIds) {
            try {
                // ApplicationStatus에서 해당 VM에 이미 설치된 애플리케이션이 있는지 확인
                boolean hasExistingInstallation = applicationHistoryService.hasExistingInstallation(
                    request.getNamespace(), request.getMciId(), vmId, catalog.getId());
                
                if (hasExistingInstallation) {
                    alreadyInstalledVms.add(vmId);
                }
            } catch (Exception e) {
                log.warn("Error checking existing installation for VM {}: {}", vmId, e.getMessage());
                // 오류가 발생해도 계속 진행
            }
        }
        
        return alreadyInstalledVms;
    }
    
    /**
     * 통합 배포 파라미터 생성기 - 모든 배포 타입을 하나의 메서드로 처리
     */
    private DeploymentParameters createDeployParameters(
            DeploymentRequest request,
            SoftwareCatalogDTO catalog,
            DeploymentHistory history,
            String vmId,
            int vmIndex,
            List<String> vmIds,
            Map<String, String> clusterConfig) {
        validateCatalog(catalog);
        
        String imageUrl = buildImageUrl(catalog);
        
        // 컨테이너명과 포트 설정 생성
        ContainerConfig containerConfig = createContainerConfig(request, catalog, vmIndex, vmIds, clusterConfig);
        
        DeploymentParameters parameters = DeploymentParameters.builder()
                .name(containerConfig.name)
                .image(imageUrl)
                .portBindings(containerConfig.portBindings)
                .debugKeepAlive(Boolean.TRUE.equals(request.getDebugKeepAlive()))
                .build();

        configureJupyterObjectStorage(parameters, request, catalog, history, vmId);
        return parameters;
    }

    private void configureJupyterObjectStorage(
            DeploymentParameters parameters,
            DeploymentRequest request,
            SoftwareCatalogDTO catalog,
            DeploymentHistory history,
            String vmId) {
        String packageName = catalog.getPackageInfo().getPackageName();
        if (packageName == null || !packageName.toLowerCase().contains("jupyter")) {
            return;
        }

        Map<String, Object> objectStorage = nestedMap(request.getAdditionalConfig(), "objectStorage");
        if (!Boolean.TRUE.equals(objectStorage.get("enabled"))) {
            throw new IllegalArgumentException("Object Storage configuration is required for JupyterLab.");
        }

        String jupyterToken = requiredString(
                objectStorage, "jupyterToken", "A Jupyter access token is required.");
        if (jupyterToken.length() < 12) {
            throw new IllegalArgumentException("The Jupyter access token must be at least 12 characters.");
        }

        ObjectStorageConfiguration configuration = objectMapper.convertValue(
                objectStorage,
                ObjectStorageConfiguration.class);
        IssuedAccess issuedAccess = objectStorageAccessGrantService.issue(
                history.getId(),
                vmId,
                request.getNamespace(),
                configuration);

        String gatewayUrl = objectStorageTunnelService.gatewayUrl();

        Map<String, String> environment = new LinkedHashMap<>();
        environment.put("MCMP_OBJECT_STORAGE_GATEWAY_URL", gatewayUrl);
        environment.put("MCMP_OBJECT_STORAGE_TOKEN", issuedAccess.token());
        environment.put("JUPYTER_TOKEN", jupyterToken);
        environment.put("MCMP_OBJECT_STORAGE_NOTEBOOK_B64", Base64.getEncoder().encodeToString(
                JUPYTER_OBJECT_STORAGE_NOTEBOOK.getBytes(StandardCharsets.UTF_8)));

        parameters.setEnvironmentVariables(environment);
        parameters.setVolumeMounts("mcmp-" + parameters.getName() + "-work:/home/jovyan/work");
        parameters.setCommandArguments(List.of(
                "bash",
                "-lc",
                "set -e; "
                        + "if [ ! -e /home/jovyan/work/sample-data.ipynb ]; then "
                        + "printf '%s' \"$MCMP_OBJECT_STORAGE_NOTEBOOK_B64\" | base64 -d > /home/jovyan/work/sample-data.ipynb; fi; "
                        + "chmod 600 /home/jovyan/work/sample-data.ipynb; "
                        + "exec start-notebook.py --ServerApp.token=\"$JUPYTER_TOKEN\" "
                        + "--ServerApp.default_url=/lab/tree/sample-data.ipynb"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> nestedMap(Map<String, Object> parent, String key) {
        if (parent == null || !(parent.get(key) instanceof Map<?, ?> value)) {
            return Map.of();
        }
        return (Map<String, Object>) value;
    }

    private String requiredString(Map<String, Object> values, String key, String message) {
        String value = stringValue(values.get(key));
        if (value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    /**
     * 카탈로그 유효성을 검증합니다.
     */
    private void validateCatalog(SoftwareCatalogDTO catalog) {
        if (catalog.getPackageInfo() == null) {
            throw new IllegalArgumentException("PackageInfo is not available for deployment");
        }
    }
    
    
    /**
     * 컨테이너 설정을 생성합니다.
     */
    private ContainerConfig createContainerConfig(DeploymentRequest request, SoftwareCatalogDTO catalog,
                                                int vmIndex, List<String> vmIds, Map<String, String> clusterConfig) {
        if (request.getVmDeploymentMode() == VmDeploymentMode.CLUSTERING && clusterConfig != null) {
            return createClusterContainerConfig(catalog, request.getServicePort(), vmIndex, clusterConfig);
        } else {
            return createStandardContainerConfig(catalog, request.getServicePort(), vmIndex, vmIds);
        }
    }
    
    /**
     * 클러스터 컨테이너 설정을 생성합니다.
     */
    private ContainerConfig createClusterContainerConfig(SoftwareCatalogDTO catalog, Integer servicePort,
                                                       int nodeIndex, Map<String, String> clusterConfig) {
        String clusterName = clusterConfig.get("clusterName");
        String appType = clusterConfig.get("appType");
        String nodeName = clusterName + "-node-" + (nodeIndex + 1);
        
        String portBindings = servicePort + ":" + catalog.getDefaultPort();
        
        // 애플리케이션별 특화 포트 설정
        if ("elasticsearch".equals(appType)) {
            // Elasticsearch 클러스터링: 모든 노드가 9300 포트 사용
            portBindings += ",9300:9300";
        } else if ("redis".equals(appType)) {
            // Redis 클러스터링: 6379 (Redis), 7000 (Cluster), 17000 (Cluster Bus)
            portBindings += ",6379:6379,7000:7000,17000:17000";
        }
        
        return new ContainerConfig(nodeName, portBindings);
    }
    
    /**
     * 일반 컨테이너 설정을 생성합니다.
     */
    private ContainerConfig createStandardContainerConfig(SoftwareCatalogDTO catalog, Integer servicePort,
                                                        int vmIndex, List<String> vmIds) {
        String containerName = catalog.getName().toLowerCase().replaceAll("\\s+", "-");
        if (vmIds.size() > 1) {
            containerName += "-vm" + vmIndex;
        }
        
        String portBindings = servicePort + ":" + catalog.getDefaultPort();
        
        return new ContainerConfig(containerName, portBindings);
    }
    
    /**
     * 컨테이너 설정 데이터 클래스
     */
    private static class ContainerConfig {
        final String name;
        final String portBindings;
        
        ContainerConfig(String name, String portBindings) {
            this.name = name;
            this.portBindings = portBindings;
        }
    }
    
    /**
     * 통합 메시지 생성기 - 모든 메시지 타입을 하나의 메서드로 처리
     */
    private String createMessage(DeploymentRequest request, MessageType type, String vmId, String additionalInfo) {
        String prefix = getMessagePrefix(request, type);
        String suffix = getMessageSuffix(type, additionalInfo);
        
        if (vmId != null) {
            return prefix + vmId + suffix;
        } else {
            return prefix + additionalInfo;
        }
    }
    
    /**
     * 메시지 접두사를 생성합니다.
     */
    private String getMessagePrefix(DeploymentRequest request, MessageType type) {
        if (request.getVmDeploymentMode() == VmDeploymentMode.CLUSTERING) {
            return type == MessageType.SUCCESS ? "Cluster node " : "Cluster node ";
        } else if (request.isSingleVm()) {
            return type == MessageType.SUCCESS ? "Single VM deployment successful. VM: " : "VM ";
        } else {
            return type == MessageType.SUCCESS ? "VM " : "VM ";
        }
    }
    
    /**
     * 메시지 접미사를 생성합니다.
     */
    private String getMessageSuffix(MessageType type, String additionalInfo) {
        switch (type) {
            case SUCCESS:
                return " deployment successful. Container ID: " + additionalInfo;
            case ERROR:
                return " deployment failed: " + additionalInfo;
            case BATCH_SUCCESS:
                return " deployment successful. " + additionalInfo;
            default:
                return additionalInfo;
        }
    }
    
    /**
     * Persists an infrastructure spec snapshot upon successful deployment.
     */
    private void saveInfraSpecSnapshot(DeploymentHistory history, DeploymentRequest request, SoftwareCatalogDTO catalog) {
        try {
            if (infraSpecSnapshotRepository.existsByDeploymentId(history.getId())) return;

            VmAccessInfo vmInfo = resolveVmInfo(request, history);
            DockerHostResourceInfo dockerHostResourceInfo = resolveDockerHostResourceInfo(history, vmInfo);
            VmSpecDto.VmSpecInfo vmSpecInfo = resolveVmSpecInfo(vmInfo);
            Integer vmCpuCores = dockerHostResourceInfo != null ? dockerHostResourceInfo.getCpuCores() : extractVmCpuCores(vmSpecInfo);
            Double vmMemoryGb = dockerHostResourceInfo != null ? dockerHostResourceInfo.getMemoryGb() : extractVmMemoryGb(vmSpecInfo);

            InfraSpecSnapshot snapshot = InfraSpecSnapshot.builder()
                    .deploymentId(history.getId())
                    .capturedAt(java.time.LocalDateTime.now())
                    .resourceType(request.getResourceType())
                    .deploymentType("DOCKER")
                    .vmInstanceType(vmInfo != null ? firstNonBlank(vmInfo.getCspSpecName(), vmInfo.getSpecId()) : null)
                    .vmCpuCores(vmCpuCores)
                    .vmMemoryGb(vmMemoryGb)
                    .catalogMinCpu(catalog.getMinCpu() != null ? catalog.getMinCpu().doubleValue() : null)
                    .catalogRecCpu(catalog.getRecommendedCpu() != null ? catalog.getRecommendedCpu().doubleValue() : null)
                    .catalogMinMemoryMb(toMemoryMb(catalog.getMinMemory()))
                    .build();

            infraSpecSnapshotRepository.save(snapshot);
            log.info("InfraSpecSnapshot saved for deployment: {}", history.getId());
        } catch (Exception e) {
            log.warn("Failed to save InfraSpecSnapshot for deployment: {}", history.getId(), e);
        }
    }

    private DockerHostResourceInfo resolveDockerHostResourceInfo(DeploymentHistory history, VmAccessInfo vmInfo) {
        if (history.getNamespace() == null || history.getMciId() == null || history.getVmId() == null) {
            return null;
        }
        return dockerOperationService.getHostResourceInfo(new DockerTarget(
                history.getNamespace(), history.getMciId(), history.getVmId()));
    }

    private VmAccessInfo resolveVmInfo(DeploymentRequest request, DeploymentHistory history) {
        String vmId = history.getVmId() != null ? history.getVmId() : request.getFirstVmId();
        if (request.getNamespace() == null || request.getMciId() == null || vmId == null) {
            return null;
        }
        return cbtumblebugRestApi.getVmInfo(request.getNamespace(), request.getMciId(), vmId);
    }

    private VmSpecDto.VmSpecInfo resolveVmSpecInfo(VmAccessInfo vmInfo) {
        if (vmInfo == null || vmInfo.getConnectionName() == null) {
            return null;
        }

        String vmSpecName = firstNonBlank(vmInfo.getCspSpecName(), vmInfo.getSpecId());
        if (vmSpecName == null) {
            return null;
        }

        try {
            VmSpecDto vmSpecDto = cbtumblebugRestApi.lookupVmSpec(vmInfo.getConnectionName(), vmSpecName);
            if (vmSpecDto == null || vmSpecDto.getVmspec() == null || vmSpecDto.getVmspec().isEmpty()) {
                return null;
            }
            return vmSpecDto.getVmspec().get(0);
        } catch (Exception e) {
            log.warn("Failed to resolve VM spec info for deployment snapshot. connectionName={}, vmSpecName={}",
                    vmInfo.getConnectionName(), vmSpecName, e);
            return null;
        }
    }

    private Integer extractVmCpuCores(VmSpecDto.VmSpecInfo vmSpecInfo) {
        if (vmSpecInfo == null || vmSpecInfo.getVCpu() == null || vmSpecInfo.getVCpu().getCount() == null) {
            return null;
        }
        try {
            return Integer.parseInt(vmSpecInfo.getVCpu().getCount());
        } catch (NumberFormatException e) {
            log.debug("Failed to parse VM CPU core count: {}", vmSpecInfo.getVCpu().getCount(), e);
            return null;
        }
    }

    private Double extractVmMemoryGb(VmSpecDto.VmSpecInfo vmSpecInfo) {
        if (vmSpecInfo == null || vmSpecInfo.getMemSizeMib() == null) {
            return null;
        }
        try {
            return Double.parseDouble(vmSpecInfo.getMemSizeMib()) / 1024.0;
        } catch (NumberFormatException e) {
            log.debug("Failed to parse VM memory size MiB: {}", vmSpecInfo.getMemSizeMib(), e);
            return null;
        }
    }

    private Integer toMemoryMb(Double memoryGb) {
        if (memoryGb == null || memoryGb <= 0) {
            return null;
        }
        return (int) Math.ceil(memoryGb * 1024.0);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    /**
     * 배포 결과를 처리합니다.
     */
    private void processDeploymentResults(DeploymentHistory history, User user, List<String> successfulVms, 
                                        List<String> failedVms, DeploymentRequest request) {
        if (history == null) {
            throw new ApplicationException("No eligible VM deployment target was found");
        }
        DeploymentStatusResult result = determineDeploymentResult(successfulVms, failedVms);
        
        history.setStatus(result.status);
        // Multi-VM histories were saved individually above. The batch summary
        // returned via firstHistory must not overwrite that VM's own status.
        if (request.getVmIds() == null || request.getVmIds().size() <= 1) {
            history.setUpdatedAt(LocalDateTime.now());
            deploymentHistoryRepository.save(history);
        }
        // updateApplicationStatus는 각 VM별로 이미 호출했으므로 여기서는 호출하지 않음
        applicationHistoryService.addDeploymentLog(history, result.logType, result.message);
    }
    
    /**
     * 배포 결과를 결정합니다.
     */
    private DeploymentStatusResult determineDeploymentResult(List<String> successfulVms, List<String> failedVms) {
        if (successfulVms.isEmpty()) {
            return new DeploymentStatusResult("FAILED", LogType.ERROR, 
                "All VM deployments failed. Failed VMs: " + String.join(", ", failedVms));
        } else if (failedVms.isEmpty()) {
            return new DeploymentStatusResult("SUCCESS", LogType.INFO, 
                "All VM deployments successful. Successful VMs: " + String.join(", ", successfulVms));
        } else {
            return new DeploymentStatusResult("PARTIAL_SUCCESS", LogType.WARNING,
                "Partial deployment success. Successful VMs: " + String.join(", ", successfulVms) + 
                ". Failed VMs: " + String.join(", ", failedVms));
        }
    }
    
    /**
     * 배포 결과 데이터 클래스 (비동기 처리용)
     */
    private static class DeploymentResult {
        private final String vmId;
        private final boolean success;
        private final String containerId;
        private final String errorMessage;
        
        public DeploymentResult(String vmId, boolean success, String containerId, String errorMessage) {
            this.vmId = vmId;
            this.success = success;
            this.containerId = containerId;
            this.errorMessage = errorMessage;
        }
        
        public String getVmId() { return vmId; }
        public boolean isSuccess() { return success; }
        public String getContainerId() { return containerId; }
        public String getErrorMessage() { return errorMessage; }
    }
    
    /**
     * 배포 결과 처리용 데이터 클래스
     */
    private static class DeploymentStatusResult {
        final String status;
        final LogType logType;
        final String message;
        
        DeploymentStatusResult(String status, LogType logType, String message) {
            this.status = status;
            this.logType = logType;
            this.message = message;
        }
    }
    
    /**
     * 클러스터 설정을 생성합니다.
     */
    private Map<String, String> buildClusterConfig(DeploymentRequest request, SoftwareCatalogDTO catalog, List<String> vmIds) {
        Map<String, String> config = new HashMap<>();
        
        // 클러스터 이름 생성
        String clusterName = generateClusterName(catalog);
        config.put("clusterName", clusterName);
        
        // 애플리케이션 타입별 설정 적용
        ApplicationType appType = determineApplicationType(catalog);
        applyApplicationSpecificConfig(config, appType);
        
        return config;
    }
    
    /**
     * 클러스터 이름을 생성합니다.
     */
    private String generateClusterName(SoftwareCatalogDTO catalog) {
        return catalog.getName().toLowerCase().replaceAll("\\s+", "-") + "-cluster-" + System.currentTimeMillis();
    }
    
    /**
     * 애플리케이션 타입을 결정합니다.
     */
    private ApplicationType determineApplicationType(SoftwareCatalogDTO catalog) {
        String catalogName = catalog.getName().toLowerCase();
        
        if (catalogName.contains("elasticsearch")) {
            return ApplicationType.ELASTICSEARCH;
        } else if (catalogName.contains("redis")) {
            return ApplicationType.REDIS;
        } else {
            return ApplicationType.GENERIC;
        }
    }
    
    /**
     * 애플리케이션별 특화 설정을 적용합니다.
     */
    private void applyApplicationSpecificConfig(Map<String, String> config, ApplicationType appType) {
        switch (appType) {
            case ELASTICSEARCH:
                config.put("appType", "elasticsearch");
                config.put("discoveryType", "zen");
                config.put("securityEnabled", "false");
                break;
            case REDIS:
                config.put("appType", "redis");
                config.put("clusterEnabled", "yes");
                break;
            default:
                config.put("appType", "generic");
                break;
        }
    }
    
    @Override
    public DeploymentService getDeploymentService(DeploymentType deploymentType) {
        if (deploymentType == DeploymentType.VM) {
            return this;
        }
        // throw new IllegalArgumentException("Unsupported deployment type: " + deploymentType);
        return null;
    }
    
    
    /**
     * 소스 타입에 따라 적절한 이미지 URL을 생성합니다.
     */
    private String buildImageUrl(SoftwareCatalogDTO catalog) {
        String imageName = catalog.getPackageInfo().getPackageName().toLowerCase();
        String imageTag = catalog.getPackageInfo().getPackageVersion().toLowerCase();

        String firstPathSegment = imageName.contains("/")
                ? imageName.substring(0, imageName.indexOf('/'))
                : imageName;
        if (firstPathSegment.contains(".")
                || firstPathSegment.contains(":")
                || "localhost".equals(firstPathSegment)) {
            return imageName + ":" + imageTag;
        }

        return nexusConfig.getImageUrlBySourceType(imageName, imageTag, "DOCKERHUB");
    }

    private static final String JUPYTER_OBJECT_STORAGE_NOTEBOOK = loadObjectStorageNotebook();

    private static String loadObjectStorageNotebook() {
        try (var input = new org.springframework.core.io.ClassPathResource(
                "notebooks/object-storage.ipynb").getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Cannot load the Object Storage notebook template", e);
        }
    }

    static String jupyterObjectStorageNotebook() {
        return JUPYTER_OBJECT_STORAGE_NOTEBOOK;
    }
    
    private Map<String, String> convertToMap(
            DeploymentParameters params, Long catalogId, Long deploymentId) {
        Map<String, String> map = new java.util.HashMap<>();
        map.put("name", params.getName());
        map.put("image", params.getImage());
        map.put("portBindings", params.getPortBindings());
        map.put("debugKeepAlive", String.valueOf(Boolean.TRUE.equals(params.getDebugKeepAlive())));
        if (catalogId != null) {
            map.put("catalogId", String.valueOf(catalogId));
        }
        if (deploymentId != null) {
            map.put("deploymentId", String.valueOf(deploymentId));
        }
        return map;
    }
}
