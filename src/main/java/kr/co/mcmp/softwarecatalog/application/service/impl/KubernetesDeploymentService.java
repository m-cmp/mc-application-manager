package kr.co.mcmp.softwarecatalog.application.service.impl;

import org.springframework.stereotype.Service;

import kr.co.mcmp.softwarecatalog.application.constants.DeploymentType;
import kr.co.mcmp.softwarecatalog.application.dto.DeploymentRequest;
import kr.co.mcmp.softwarecatalog.application.model.DeploymentHistory;
import kr.co.mcmp.softwarecatalog.application.service.DeploymentService;
import kr.co.mcmp.softwarecatalog.kubernetes.service.KubernetesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Kubernetes 배포를 담당하는 서비스
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KubernetesDeploymentService implements DeploymentService {
    
    private final KubernetesService kubernetesService;
    
    @Override
    public DeploymentHistory deployApplication(DeploymentRequest request) {
        return kubernetesService.deployApplication(
            request.getNamespace(), 
            request.getClusterName(), 
            request.getCatalogId(), 
            request.getUsername()
        );
    }
    
    @Override
    public DeploymentService getDeploymentService(DeploymentType deploymentType) {
        if (deploymentType == DeploymentType.K8S) {
            return this;
        }
        return null; // K8S가 아닌 경우 null 반환
    }
}


