package kr.co.mcmp.softwarecatalog.application.service.impl;

import java.util.Map;

import org.springframework.stereotype.Service;

import kr.co.mcmp.ape.cbtumblebug.api.CbtumblebugRestApi;
import kr.co.mcmp.ape.cbtumblebug.dto.VmAccessInfo;
import kr.co.mcmp.softwarecatalog.CatalogService;
import kr.co.mcmp.softwarecatalog.SoftwareCatalogDTO;
import kr.co.mcmp.softwarecatalog.application.constants.DeploymentType;
import kr.co.mcmp.softwarecatalog.application.constants.LogType;
import kr.co.mcmp.softwarecatalog.application.dto.DeploymentParameters;
import kr.co.mcmp.softwarecatalog.application.dto.DeploymentRequest;
import kr.co.mcmp.softwarecatalog.application.exception.ApplicationException;
import kr.co.mcmp.softwarecatalog.application.model.DeploymentHistory;
import kr.co.mcmp.softwarecatalog.application.service.ApplicationHistoryService;
import kr.co.mcmp.softwarecatalog.application.service.DeploymentService;
import kr.co.mcmp.softwarecatalog.docker.model.ContainerDeployResult;
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
    private final ApplicationHistoryService applicationHistoryService;
    private final UserService userService;
    
    @Override
    public DeploymentHistory deployApplication(DeploymentRequest request) {
        SoftwareCatalogDTO catalog = catalogService.getCatalog(request.getCatalogId());
        User user = userService.findUserByUsername(request.getUsername()).orElse(null);
        DeploymentHistory history = applicationHistoryService.createDeploymentHistory(request, user);

        try {
            // Docker 설치 확인 및 설치
            dockerSetupService.checkAndInstallDocker(request.getNamespace(), request.getMciId(), request.getVmId());

            // 배포 파라미터 생성
            DeploymentParameters deployParams = buildDeployParameters(catalog, request.getServicePort());
            VmAccessInfo vmAccessInfo = cbtumblebugRestApi.getVmInfo(request.getNamespace(), request.getMciId(), request.getVmId());
            
            // Docker 컨테이너 실행
            ContainerDeployResult deployResult = dockerOperationService.runDockerContainer(
                vmAccessInfo.getPublicIP(), 
                convertToMap(deployParams)
            );
            
            String containerId = deployResult.getContainerId();
            String result = deployResult.getDeploymentResult();
            
            log.info("Deployment result: {}", result);
            log.info("Container ID: {}", containerId);

            if (containerId != null && !containerId.isEmpty()) {
                boolean isRunning = dockerOperationService.isContainerRunning(vmAccessInfo.getPublicIP(), containerId);
                if (isRunning) {
                    log.info("Container is running");
                    history.setStatus("SUCCESS");
                    applicationHistoryService.updateApplicationStatus(history, "RUNNING", user);
                    applicationHistoryService.addDeploymentLog(history, LogType.INFO, 
                        "Deployment successful and container is running. Container ID: " + containerId);
                } else {
                    log.warn("Container is not running");
                    throw new ApplicationException("Deployment failed: Container is not running");
                }
            } else {
                throw new ApplicationException("Deployment failed: Could not retrieve container ID");
            }
        } catch (Exception e) {
            log.error("Deployment failed", e);
            history.setStatus("FAILED");
            applicationHistoryService.updateApplicationStatus(history, "FAILED", user);
            applicationHistoryService.addDeploymentLog(history, LogType.ERROR, "Deployment failed: " + e.getMessage());
        }

        return history;
    }
    
    @Override
    public DeploymentService getDeploymentService(DeploymentType deploymentType) {
        if (deploymentType == DeploymentType.VM) {
            return this;
        }
        throw new IllegalArgumentException("Unsupported deployment type: " + deploymentType);
    }
    
    private DeploymentParameters buildDeployParameters(SoftwareCatalogDTO catalog, Integer servicePort) {
        if (catalog.getPackageInfo() == null) {
            throw new IllegalArgumentException("PackageInfo is not available for deployment");
        }
        
        Integer containerPort = catalog.getDefaultPort() != null ? catalog.getDefaultPort() : servicePort;
        
        return DeploymentParameters.builder()
                .name(catalog.getTitle().toLowerCase().replaceAll("\\s+", "-"))
                .image(catalog.getPackageInfo().getPackageName().toLowerCase() + ":" + 
                       catalog.getPackageInfo().getPackageVersion().toLowerCase())
                .portBindings(servicePort + ":" + containerPort)
                .build();
    }
    
    private Map<String, String> convertToMap(DeploymentParameters params) {
        Map<String, String> map = new java.util.HashMap<>();
        map.put("name", params.getName());
        map.put("image", params.getImage());
        map.put("portBindings", params.getPortBindings());
        return map;
    }
}


