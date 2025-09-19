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
        try {
            SoftwareCatalogDTO catalog = catalogService.getCatalog(catalogId);
            K8sSpec nodeSpec = getSpecForK8s(namespace, clusterName);
            
            List<DeploymentHistory> activeDeployments = deploymentHistoryRepository.findByNamespaceAndClusterNameAndActionTypeNotAndStatus(
                namespace, clusterName, ActionType.UNINSTALL, "SUCCESS"
            );

            double usedCpu = activeDeployments.stream().mapToDouble(d -> d.getCatalog().getRecommendedCpu()).sum();
            double usedMemory = activeDeployments.stream().mapToDouble(d -> d.getCatalog().getRecommendedMemory()).sum();

            double availableCpu = Double.parseDouble(nodeSpec.getVCpu().getCount()) - usedCpu;
            
            // 메모리 값을 MB에서 GB로 변환
            String memoryValue = getMemoryValueFromSpec(nodeSpec);
            double nodeMemoryInGB = convertMemoryToGB(memoryValue);
            double availableMemory = nodeMemoryInGB - usedMemory;

            log.info("K8s Node Spec Check - Raw Memory Value: {}", memoryValue);
            log.info("K8s Node Spec Check - Converted Memory: {} GB", nodeMemoryInGB);
            log.info("K8s Node Spec Check - Available vCPU: {}, Required vCPU: {}", availableCpu, catalog.getRecommendedCpu());
            log.info("K8s Node Spec Check - Available Memory: {} GiB, Required Memory: {} GiB", availableMemory, catalog.getRecommendedMemory());

            return availableCpu >= catalog.getRecommendedCpu() && availableMemory >= catalog.getRecommendedMemory();
        } catch (Exception e) {
            log.warn("K8s spec validation failed: {}. Skipping spec validation and allowing deployment.", e.getMessage());
            return true; // 스펙 검증 실패 시에도 배포 허용
        }
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
            if (clusterInfo == null) {
                throw new ApplicationException("Failed to retrieve K8s cluster info");
            }

            // spiderViewK8sClusterDetail 우선 사용, 없으면 cspViewK8sClusterDetail 사용
            K8sClusterDto.SpiderViewK8sClusterDetail spiderDetail = clusterInfo.getSpiderViewK8sClusterDetail();
            K8sClusterDto.CspViewK8sClusterDetail cspDetail = clusterInfo.getCspViewK8sClusterDetail();
            
            List<K8sClusterDto.NodeGroup> nodeGroups = null;
            if (spiderDetail != null) {
                nodeGroups = spiderDetail.getNodeGroupList();
            } else if (cspDetail != null) {
                nodeGroups = cspDetail.getNodeGroupList();
            }

            if (nodeGroups == null || nodeGroups.isEmpty()) {
                log.warn("No node groups found for K8s cluster: {}. Using default spec for validation.", clusterName);
                
                // 노드 그룹이 없으면 기본 스펙 사용
                K8sSpec defaultSpec = new K8sSpec();
                defaultSpec.setRegion("ap-northeast-2");
                defaultSpec.setName("default-eks-spec");
                
                K8sSpec.VCpu vCpu = new K8sSpec.VCpu();
                vCpu.setCount("2");
                vCpu.setClock("2.5");
                defaultSpec.setVCpu(vCpu);
                defaultSpec.setMem("4");
                
                log.info("Using default spec for K8s cluster without node groups: {}", defaultSpec);
                return defaultSpec;
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
    
    /**
     * K8sSpec에서 메모리 값을 추출합니다.
     * 여러 필드와 keyValueList에서 메모리 관련 정보를 찾습니다.
     * 
     * @param spec K8sSpec 객체
     * @return 메모리 값 문자열 (단위 포함)
     */
    private String getMemoryValueFromSpec(K8sSpec spec) {
        // 1. mem 필드 확인
        if (spec.getMem() != null && !spec.getMem().trim().isEmpty()) {
            log.debug("Using mem field: {}", spec.getMem());
            return spec.getMem();
        }
        
        // 2. keyValueList에서 메모리 관련 키 찾기
        if (spec.getKeyValueList() != null) {
            for (K8sSpec.KeyValue kv : spec.getKeyValueList()) {
                String key = kv.getKey();
                String value = kv.getValue();
                
                if (value == null || value.trim().isEmpty()) {
                    continue;
                }
                
                // 다양한 메모리 관련 키 확인
                if ("MemoryInMB".equals(key)) {
                    log.debug("Found MemoryInMB: {}", value);
                    return value + "MB";
                } else if ("MemoryInGB".equals(key)) {
                    log.debug("Found MemoryInGB: {}", value);
                    return value + "GB";
                } else if ("MemoryInGiB".equals(key)) {
                    log.debug("Found MemoryInGiB: {}", value);
                    return value + "GiB";
                } else if ("MemorySizeMib".equals(key)) {
                    log.debug("Found MemorySizeMib: {}", value);
                    return value + "MiB";
                } else if ("MemorySizeGib".equals(key)) {
                    log.debug("Found MemorySizeGib: {}", value);
                    return value + "GiB";
                } else if ("Memory".equals(key)) {
                    log.debug("Found Memory: {}", value);
                    return value; // 단위가 이미 포함되어 있을 수 있음
                } else if ("MemSize".equals(key)) {
                    log.debug("Found MemSize: {}", value);
                    return value; // 단위가 이미 포함되어 있을 수 있음
                }
            }
        }
        
        log.warn("No memory value found in K8sSpec, returning default 4GB");
        return "4GB"; // 기본값 (단위 명시)
    }
    
    /**
     * 메모리 값을 GB로 변환합니다.
     * 다양한 메모리 단위를 지원합니다: KB, MB, GB, TB, KiB, MiB, GiB, TiB
     * 
     * @param memoryValue 메모리 값 (문자열)
     * @return GB 단위의 메모리 값
     */
    private double convertMemoryToGB(String memoryValue) {
        if (memoryValue == null || memoryValue.trim().isEmpty()) {
            log.warn("Memory value is null or empty, returning 0");
            return 0.0;
        }
        
        try {
            String trimmedValue = memoryValue.trim().toLowerCase();
            
            // 숫자만 추출
            String numericValue = trimmedValue.replaceAll("[^0-9.]", "");
            if (numericValue.isEmpty()) {
                log.warn("No numeric value found in: {}", memoryValue);
                return 0.0;
            }
            
            double value = Double.parseDouble(numericValue);
            
            // 단위별 변환 (1024 기반)
            if (trimmedValue.contains("tib")) {
                // TiB를 GB로 변환 (1024^3)
                double gbValue = value * 1024.0 * 1024.0;
                log.debug("Converted {} TiB to {} GB", value, gbValue);
                return gbValue;
            } else if (trimmedValue.contains("tb")) {
                // TB를 GB로 변환 (1000^3)
                double gbValue = value * 1000.0 * 1000.0;
                log.debug("Converted {} TB to {} GB", value, gbValue);
                return gbValue;
            } else if (trimmedValue.contains("gib")) {
                // GiB는 이미 GB와 동일
                log.debug("Memory value {} GiB is already in GB", value);
                return value;
            } else if (trimmedValue.contains("gb")) {
                // GB는 그대로
                log.debug("Memory value {} GB is already in GB", value);
                return value;
            } else if (trimmedValue.contains("mib")) {
                // MiB를 GB로 변환 (1024로 나누기)
                double gbValue = value / 1024.0;
                log.debug("Converted {} MiB to {} GB", value, gbValue);
                return gbValue;
            } else if (trimmedValue.contains("mb")) {
                // MB를 GB로 변환 (1000으로 나누기)
                double gbValue = value / 1000.0;
                log.debug("Converted {} MB to {} GB", value, gbValue);
                return gbValue;
            } else if (trimmedValue.contains("kib")) {
                // KiB를 GB로 변환 (1024^2로 나누기)
                double gbValue = value / (1024.0 * 1024.0);
                log.debug("Converted {} KiB to {} GB", value, gbValue);
                return gbValue;
            } else if (trimmedValue.contains("kb")) {
                // KB를 GB로 변환 (1000^2로 나누기)
                double gbValue = value / (1000.0 * 1000.0);
                log.debug("Converted {} KB to {} GB", value, gbValue);
                return gbValue;
            } else {
                // 단위가 명시되지 않은 경우, 값의 크기로 추정
                if (value >= 1000000) {
                    // 1,000,000 이상이면 MB로 간주
                    double gbValue = value / 1000.0;
                    log.debug("No unit specified, assuming {} is MB, converted to {} GB", value, gbValue);
                    return gbValue;
                } else if (value >= 1000) {
                    // 1,000 이상이면 GB로 간주
                    log.debug("No unit specified, assuming {} is already in GB", value);
                    return value;
                } else {
                    // 1,000 미만이면 GB로 간주 (소수점 포함)
                    log.debug("No unit specified, assuming {} is already in GB", value);
                    return value;
                }
            }
        } catch (NumberFormatException e) {
            log.warn("Error parsing memory value: {}, returning 0", memoryValue, e);
            return 0.0;
        }
    }
}


