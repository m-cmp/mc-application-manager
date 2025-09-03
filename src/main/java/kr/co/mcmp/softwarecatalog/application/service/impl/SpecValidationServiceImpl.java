package kr.co.mcmp.softwarecatalog.application.service.impl;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import kr.co.mcmp.ape.cbtumblebug.api.CbtumblebugRestApi;
import kr.co.mcmp.ape.cbtumblebug.dto.K8sClusterDto;
import kr.co.mcmp.ape.cbtumblebug.dto.K8sSpec;
import kr.co.mcmp.ape.cbtumblebug.dto.Spec;
import kr.co.mcmp.ape.cbtumblebug.dto.VmAccessInfo;
import kr.co.mcmp.softwarecatalog.CatalogService;
import kr.co.mcmp.softwarecatalog.SoftwareCatalogDTO;
import kr.co.mcmp.softwarecatalog.application.constants.ActionType;
import kr.co.mcmp.softwarecatalog.application.exception.ApplicationException;
import kr.co.mcmp.softwarecatalog.application.model.DeploymentHistory;
import kr.co.mcmp.softwarecatalog.application.repository.DeploymentHistoryRepository;
import kr.co.mcmp.softwarecatalog.application.service.SpecValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 스펙 검증을 담당하는 서비스 구현체
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SpecValidationServiceImpl implements SpecValidationService {
    
    private final CatalogService catalogService;
    private final CbtumblebugRestApi cbtumblebugRestApi;
    private final DeploymentHistoryRepository deploymentHistoryRepository;
    
    @Override
    public boolean checkSpecForVm(String namespace, String mciId, String vmId, Long catalogId) {
        SoftwareCatalogDTO catalog = catalogService.getCatalog(catalogId);
        Spec vmSpec = getSpecForVm(namespace, mciId, vmId);
        
        List<DeploymentHistory> activeDeployments = deploymentHistoryRepository.findByNamespaceAndMciIdAndVmIdAndActionTypeNotAndStatus(
            namespace, mciId, vmId, ActionType.UNINSTALL, "SUCCESS"
        );

        double usedCpu = activeDeployments.stream().mapToDouble(d -> d.getCatalog().getRecommendedCpu()).sum();
        double usedMemory = activeDeployments.stream().mapToDouble(d -> d.getCatalog().getRecommendedMemory()).sum();

        double availableCpu = vmSpec.getVCPU() - usedCpu;
        double availableMemory = vmSpec.getMemoryGiB() - usedMemory;

        log.info("VM Spec Check - Available vCPU: {}, Required vCPU: {}", availableCpu, catalog.getRecommendedCpu());
        log.info("VM Spec Check - Available Memory: {} GiB, Required Memory: {} GiB", availableMemory, catalog.getRecommendedMemory());

        return availableCpu >= catalog.getRecommendedCpu() && availableMemory >= catalog.getRecommendedMemory();
    }
    
    @Override
    public boolean checkSpecForK8s(String namespace, String clusterName, Long catalogId) {
        SoftwareCatalogDTO catalog = catalogService.getCatalog(catalogId);
        K8sSpec nodeSpec = getSpecForK8s(namespace, clusterName);
        
        List<DeploymentHistory> activeDeployments = deploymentHistoryRepository.findByNamespaceAndClusterNameAndActionTypeNotAndStatus(
            namespace, clusterName, ActionType.UNINSTALL, "SUCCESS"
        );

        double usedCpu = activeDeployments.stream().mapToDouble(d -> d.getCatalog().getRecommendedCpu()).sum();
        double usedMemory = activeDeployments.stream().mapToDouble(d -> d.getCatalog().getRecommendedMemory()).sum();

        double availableCpu = Double.parseDouble(nodeSpec.getVCpu().getCount()) - usedCpu;
        double availableMemory = Double.parseDouble(nodeSpec.getMem()) - usedMemory;

        log.info("K8s Node Spec Check - Available vCPU: {}, Required vCPU: {}", availableCpu, catalog.getRecommendedCpu());
        log.info("K8s Node Spec Check - Available Memory: {} GiB, Required Memory: {} GiB", availableMemory, catalog.getRecommendedMemory());

        return availableCpu >= catalog.getRecommendedCpu() && availableMemory >= catalog.getRecommendedMemory();
    }
    
    @Override
    public Spec getSpecForVm(String namespace, String mciId, String vmId) {
        log.info("Retrieving spec for VM: namespace={}, mciId={}, vmId={}", namespace, mciId, vmId);
        VmAccessInfo vmInfo = cbtumblebugRestApi.getVmInfo(namespace, mciId, vmId);

        try {
            if (vmInfo == null || StringUtils.isBlank(vmInfo.getSpecId())) {
                throw new ApplicationException("Failed to retrieve VM info or spec ID is blank");
            }
            return cbtumblebugRestApi.getSpecBySpecId(namespace, vmInfo.getSpecId());
        } catch (Exception e) {
            log.error("Error retrieving spec for VM: {}", e.getMessage());
            return cbtumblebugRestApi.getSpecBySpecId("system", vmInfo.getSpecId());
        }
    }
    
    @Override
    public K8sSpec getSpecForK8s(String namespace, String clusterName) {
        log.info("Retrieving spec for K8s cluster: namespace={}, clusterName={}", namespace, clusterName);
        try {
            K8sClusterDto clusterInfo = cbtumblebugRestApi.getK8sClusterByName(namespace, clusterName);
            if (clusterInfo == null || clusterInfo.getCspViewK8sClusterDetail() == null) {
                throw new ApplicationException("Failed to retrieve K8s cluster info");
            }

            List<K8sClusterDto.NodeGroup> nodeGroups = clusterInfo.getCspViewK8sClusterDetail().getNodeGroupList();
            if (nodeGroups == null || nodeGroups.isEmpty()) {
                throw new ApplicationException("No node groups found for K8s cluster: " + clusterName);
            }

            K8sClusterDto.NodeGroup firstNodeGroup = nodeGroups.get(0);
            if (StringUtils.isBlank(firstNodeGroup.getVmSpecName())) {
                throw new ApplicationException("VM spec name is blank for the first node group");
            }

            K8sSpec spec = cbtumblebugRestApi.lookupSpec(clusterInfo.getConnectionName(), firstNodeGroup.getVmSpecName());
            log.info("Retrieved spec for K8s cluster: {}", spec);
            return spec;
        } catch (Exception e) {
            log.error("Error retrieving spec for K8s cluster: {}", e.getMessage());
            throw new ApplicationException("Failed to retrieve spec for K8s cluster : " +  e.getMessage());
        }
    }
}


