package kr.co.mcmp.softwarecatalog.application.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.co.mcmp.softwarecatalog.application.constants.ActionType;
import kr.co.mcmp.softwarecatalog.application.dto.ApplicationStatusDto;
import kr.co.mcmp.softwarecatalog.application.dto.DeploymentRequest;
import kr.co.mcmp.softwarecatalog.application.model.DeploymentHistory;
import kr.co.mcmp.softwarecatalog.application.model.DeploymentLog;
import kr.co.mcmp.ape.cbtumblebug.dto.K8sSpec;
import kr.co.mcmp.ape.cbtumblebug.dto.Spec;
import kr.co.mcmp.softwarecatalog.SoftwareCatalogDTO;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 애플리케이션 서비스 - 기존 호환성을 위한 래퍼 클래스
 * @deprecated 새로운 ApplicationOrchestrationService를 사용하세요
 */
@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
@Deprecated
public class ApplicationService {

    private final ApplicationOrchestrationService applicationOrchestrationService;

    public Map<String, Object> performDockerOperation(ActionType operation, Long applicationStatusId, String reason, String username) throws Exception {
        log.debug("performDockerOperation is deprecated. Use ApplicationOrchestrationService.performOperation instead.");
        return applicationOrchestrationService.performOperation(operation, applicationStatusId, reason, username);
    }

    @Transactional
    public DeploymentHistory deployApplication(String namespace, String mciId, String vmId, Long catalogId, Integer servicePort, String username) {
        log.debug("deployApplication is deprecated. Use ApplicationOrchestrationService.deployApplication instead.");
        DeploymentRequest request = DeploymentRequest.forVm(namespace, mciId, vmId, catalogId, servicePort, username);
        return applicationOrchestrationService.deployApplication(request);
    }

    @Deprecated
    public Map<String, String> buildDeployParameters(SoftwareCatalogDTO catalog, Integer servicePort) {
        log.debug("buildDeployParameters is deprecated. This functionality is now handled by DockerDeploymentService.");
        // 호환성을 위한 간단한 구현
        if (catalog.getPackageInfo() == null) {
            throw new IllegalArgumentException("PackageInfo is not available for deployment");
        }
        Integer containerPort = catalog.getDefaultPort() != null ? catalog.getDefaultPort() : servicePort;
        Map<String, String> params = new java.util.HashMap<>();
        params.put("name", catalog.getTitle().toLowerCase().replaceAll("\\s+", "-"));
        params.put("image", catalog.getPackageInfo().getPackageName().toLowerCase() + ":" + 
                   catalog.getPackageInfo().getPackageVersion().toLowerCase());
        params.put("portBindings", servicePort + ":" + containerPort);
        return params;
    }
    

    public List<ApplicationStatusDto> getApplicationGroups() {
        log.debug("getApplicationGroups is deprecated. Use ApplicationOrchestrationService.getApplicationGroups instead.");
        return applicationOrchestrationService.getApplicationGroups();
    }
    

    public List<DeploymentHistory> getDeploymentHistories(Long catalogId, String username) {
        log.debug("getDeploymentHistories is deprecated. Use ApplicationOrchestrationService.getDeploymentHistories instead.");
        return applicationOrchestrationService.getDeploymentHistories(catalogId, username);
    }

    public List<DeploymentLog> getDeploymentLogs(Long deploymentId, String username) {
        log.debug("getDeploymentLogs is deprecated. Use ApplicationOrchestrationService.getDeploymentLogs instead.");
        return applicationOrchestrationService.getDeploymentLogs(deploymentId, username);
    }

    public ApplicationStatusDto getLatestApplicationStatus(String username) {
        log.debug("getLatestApplicationStatus is deprecated. Use ApplicationOrchestrationService.getLatestApplicationStatus instead.");
        return applicationOrchestrationService.getLatestApplicationStatus(username);
    }

    public boolean checkSpecForVm(String namespace, String mciId, String vmId, Long catalogId) {
        log.debug("checkSpecForVm is deprecated. Use ApplicationOrchestrationService.checkSpecForVm instead.");
        return applicationOrchestrationService.checkSpecForVm(namespace, mciId, vmId, catalogId);
    }

    public boolean checkSpecForK8s(String namespace, String clusterName, Long catalogId) {
        log.debug("checkSpecForK8s is deprecated. Use ApplicationOrchestrationService.checkSpecForK8s instead.");
        return applicationOrchestrationService.checkSpecForK8s(namespace, clusterName, catalogId);
    }

    public Spec getSpecForVm(String namespace, String mciId, String vmId) {
        log.debug("getSpecForVm is deprecated. Use ApplicationOrchestrationService.getSpecForVm instead.");
        return applicationOrchestrationService.getSpecForVm(namespace, mciId, vmId);
    }

    public K8sSpec getSpecForK8s(String namespace, String clusterName) {
        log.debug("getSpecForK8s is deprecated. Use ApplicationOrchestrationService.getSpecForK8s instead.");
        return applicationOrchestrationService.getSpecForK8s(namespace, clusterName);
    }

    public DeploymentHistory deployApplicationToK8s(String namespace, String clusterName, Long catalogId, String username) {
        log.debug("deployApplicationToK8s is deprecated. Use ApplicationOrchestrationService.deployApplication instead.");
        DeploymentRequest request = DeploymentRequest.forKubernetes(namespace, clusterName, catalogId, username);
        return applicationOrchestrationService.deployApplication(request);
    }

    public Map<String, Object> performDockerOperationForK8s(ActionType operation, Long applicationStatusId, String reason, String username) {
        log.debug("performDockerOperationForK8s is deprecated. Use ApplicationOrchestrationService.performOperation instead.");
        return applicationOrchestrationService.performOperation(operation, applicationStatusId, reason, username);
    }

}