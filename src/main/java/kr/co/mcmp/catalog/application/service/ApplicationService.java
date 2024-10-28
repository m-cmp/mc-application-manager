package kr.co.mcmp.catalog.application.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.co.mcmp.ape.cbtumblebug.api.CbtumblebugRestApi;
import kr.co.mcmp.ape.cbtumblebug.dto.K8sClusterDto;
import kr.co.mcmp.ape.cbtumblebug.dto.K8sSpec;
import kr.co.mcmp.ape.cbtumblebug.dto.Spec;
import kr.co.mcmp.ape.cbtumblebug.dto.VmDto;
import kr.co.mcmp.ape.dto.reqDto.JenkinsJobDto;
import kr.co.mcmp.catalog.CatalogDTO;
import kr.co.mcmp.catalog.CatalogEntity;
import kr.co.mcmp.catalog.CatalogService;
import kr.co.mcmp.catalog.application.dto.K8sApplicationHistoryDTO;
import kr.co.mcmp.catalog.application.dto.VmApplicationHistoryDTO;
import kr.co.mcmp.catalog.application.model.ApplicationStatus;
import kr.co.mcmp.catalog.application.model.K8sApplicationHistory;
import kr.co.mcmp.catalog.application.model.VmApplicationHistory;
import kr.co.mcmp.catalog.application.repository.K8sApplicationHistoryRepository;
import kr.co.mcmp.catalog.application.repository.VmApplicationHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class ApplicationService {
    
    private final VmApplicationHistoryRepository vmHistoryRepo;   
    private final K8sApplicationHistoryRepository k8sHistoryRepo;
    private final CatalogService catalogService;
    private final CbtumblebugRestApi cbtumblebugRestApi;

    public List<VmApplicationHistoryDTO> getActiveVmApplicationHistory(String namespace, String mciName, String vmName) {
        return vmHistoryRepo.findByNamespaceAndMciNameAndVmNameAndStatusNot(namespace, mciName, vmName, ApplicationStatus.UNINSTALL)
                .stream()
                .map(VmApplicationHistoryDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public List<K8sApplicationHistoryDTO> getActiveK8sApplicationHistory(String namespace, String clusterName) {
        return k8sHistoryRepo.findByNamespaceAndClusterNameAndStatusNot(namespace, clusterName, ApplicationStatus.UNINSTALL)
                .stream()
                .map(K8sApplicationHistoryDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public boolean isVmResourcesSufficient(String namespace, String mciName, String vmName, int availableCpu, int availableMemory) {
        List<VmApplicationHistory> activeApplications = vmHistoryRepo.findByNamespaceAndMciNameAndVmNameAndStatusNot(namespace, mciName, vmName, ApplicationStatus.UNINSTALL);
        
        int totalRecommendedCpu = activeApplications.stream().mapToInt(app -> app.getCatalog().getRecommendedCpu()).sum();
        int totalRecommendedMemory = activeApplications.stream().mapToInt(app -> app.getCatalog().getRecommendedMemory()).sum();

        return totalRecommendedCpu <= availableCpu && totalRecommendedMemory <= availableMemory;
    }

    public boolean canInstallApplicationOnK8s(String namespace, String clusterName, Integer catalogId) {
        CatalogDTO catalogDTO = catalogService.getCatalog(catalogId);
        K8sClusterDto k8sClusterDto = cbtumblebugRestApi.getK8sClusterByName(namespace, clusterName);

        Optional<String> vmSpecNameOpt = Optional.ofNullable(k8sClusterDto.getCspViewK8sClusterDetail())
            .map(K8sClusterDto.CspViewK8sClusterDetail::getNodeGroupList)
            .flatMap(nodeGroups -> nodeGroups.stream().map(K8sClusterDto.NodeGroup::getVmSpecName).filter(StringUtils::isNotBlank).findFirst());

        if (vmSpecNameOpt.isPresent()) {
            K8sSpec spec = cbtumblebugRestApi.lookupSpec(k8sClusterDto.getConnectionName(), vmSpecNameOpt.get());
            int availableCpu = Integer.parseInt(spec.getVCpu().getCount()) - catalogDTO.getRecommendedCpu();
            int availableMemGib = convertMemoryToGb(spec.getMem()) - catalogDTO.getRecommendedMemory();

            return isK8sResourcesSufficient(namespace, clusterName, availableCpu, availableMemGib);
        }

        return false;
    }
    
    public boolean canInstallApplicationOnVm(String namespace, String mciName, String vmName, Integer catalogId) {
        CatalogDTO catalogDTO = catalogService.getCatalog(catalogId);
        VmDto vmDto = cbtumblebugRestApi.getVmInfo(namespace, mciName, vmName);
        Spec spec = cbtumblebugRestApi.getSpecBySpecId(namespace, vmDto.getSpecId());

        int availableCpu = spec.getVCPU() - catalogDTO.getRecommendedCpu();
        int availableMemory = spec.getMemoryGiB() - catalogDTO.getRecommendedMemory();

        return isVmResourcesSufficient(namespace, mciName, vmName, availableCpu, availableMemory);
    }

    public boolean isK8sResourcesSufficient(String namespace, String clusterName, int availableCpu, int availableMemory) {
        List<K8sApplicationHistory> activeApplications = k8sHistoryRepo.findByNamespaceAndClusterNameAndStatusNot(namespace, clusterName, ApplicationStatus.UNINSTALL);

        int totalRecommendedCpu = activeApplications.stream().mapToInt(app -> app.getCatalog().getRecommendedCpu()).sum();
        int totalRecommendedMemory = activeApplications.stream().mapToInt(app -> app.getCatalog().getRecommendedMemory()).sum();

        return totalRecommendedCpu <= availableCpu && totalRecommendedMemory <= availableMemory;
    }

    public void logVmInstallation(JenkinsJobDto.VmApplicationInstall jobDto, ApplicationStatus status) {
        try {
            CatalogDTO catalogDto = getCatalogFromApplication(jobDto.getApplications());
            VmApplicationHistory history = createVmApplicationHistory(jobDto, catalogDto, status);
            vmHistoryRepo.save(history);
        } catch (Exception e) {
            log.error("VM 설치 로깅 중 오류 발생: " + e.getMessage());
        }
    }

    public void updateVmApplicationStatus(JenkinsJobDto.VmApplicationUninstall jobDto, ApplicationStatus status) {
        try {
            CatalogDTO catalogDto = getCatalogFromApplication(jobDto.getApplications());
            Optional<VmApplicationHistory> historyOpt = findActiveVmApplicationHistory(jobDto, catalogDto);
            historyOpt.ifPresent(history -> updateAndSaveVmApplicationHistory(history, jobDto, status));
        } catch (Exception e) {
            log.error("VM 애플리케이션 상태 업데이트 중 오류 발생: " + e.getMessage());
        }
    }

    public void logK8sInstallation(JenkinsJobDto.HelmChartInstall jobDto, ApplicationStatus status) {
        try {
            CatalogDTO catalogDto = getCatalogFromHelmChart(jobDto.getHelmCharts());
            K8sApplicationHistory history = createK8sApplicationHistory(jobDto, catalogDto, status);
            k8sHistoryRepo.save(history);
        } catch (Exception e) {
            log.error("K8S 설치 로깅 중 오류 발생: " + e.getMessage());
        }
    }

    public void updateK8sApplicationStatus(JenkinsJobDto.HelmChartUninstall jobDto, ApplicationStatus status) {
        try {
            CatalogDTO catalogDto = getCatalogFromHelmChart(jobDto.getHelmCharts());
            Optional<K8sApplicationHistory> historyOpt = findActiveK8sApplicationHistory(jobDto, catalogDto);
            historyOpt.ifPresent(history -> updateAndSaveK8sApplicationHistory(history, jobDto, status));
        } catch (Exception e) {
            log.error("K8S 애플리케이션 상태 업데이트 중 오류 발생: " + e.getMessage());
        }
    }

    private CatalogDTO getCatalogFromApplication(List<String> applications) {
        return catalogService.getCatalogListSearch(applications.get(0))
                .stream()
                .findFirst()
                .orElseThrow(() -> new RuntimeException("애플리케이션에 대한 카탈로그를 찾을 수 없습니다: " + applications));
    }

    private CatalogDTO getCatalogFromHelmChart(List<String> helmCharts) {
        return catalogService.getCatalogListSearch(helmCharts.get(0))
                .stream()
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Helm 차트에 대한 카탈로그를 찾을 수 없습니다: " + helmCharts));
    }

    private VmApplicationHistory createVmApplicationHistory(JenkinsJobDto.VmApplicationInstall jobDto, CatalogDTO catalogDto, ApplicationStatus status) {
        return VmApplicationHistory.builder()
                .namespace(jobDto.getNamespace())
                .mciName(jobDto.getMciName())
                .vmName(jobDto.getVmName())
                .catalog(new CatalogEntity(catalogDto))
                .status(status)
                .installedAt(LocalDateTime.now())
                .build();
    }

    private K8sApplicationHistory createK8sApplicationHistory(JenkinsJobDto.HelmChartInstall jobDto, CatalogDTO catalogDto, ApplicationStatus status) {
        return K8sApplicationHistory.builder()
                .namespace(jobDto.getNamespace())
                .clusterName(jobDto.getClusterName())
                .catalog(new CatalogEntity(catalogDto))
                .status(status)
                .installedAt(LocalDateTime.now())
                .build();
    }

    private Optional<VmApplicationHistory> findActiveVmApplicationHistory(JenkinsJobDto.VmApplicationUninstall jobDto, CatalogDTO catalogDto) {
        return vmHistoryRepo.findHistoryByNotUninstall(
                jobDto.getNamespace(), jobDto.getMciName(), jobDto.getVmName(), catalogDto.getCatalogIdx());
    }

    private Optional<K8sApplicationHistory> findActiveK8sApplicationHistory(JenkinsJobDto.HelmChartUninstall jobDto, CatalogDTO catalogDto) {
        return k8sHistoryRepo.findHistoryByUninstall(jobDto.getNamespace(), jobDto.getClusterName(), catalogDto.getCatalogIdx());
    }

    private void updateAndSaveVmApplicationHistory(VmApplicationHistory history, JenkinsJobDto.VmApplicationUninstall jobDto, ApplicationStatus status) {
        history.setStatus(status);
        history.setUninstalledAt(status == ApplicationStatus.UNINSTALL ? LocalDateTime.now() : null);
        history.setUpdatedAt(ApplicationStatus.UPDATE_STATUSES.contains(status) ? LocalDateTime.now() : null);
        vmHistoryRepo.save(history);
    }

    private void updateAndSaveK8sApplicationHistory(K8sApplicationHistory history, JenkinsJobDto.HelmChartUninstall jobDto, ApplicationStatus status) {
        history.setStatus(status);
        history.setUninstalledAt(status == ApplicationStatus.UNINSTALL ? LocalDateTime.now() : null);
        history.setUpdatedAt(ApplicationStatus.UPDATE_STATUSES.contains(status) ? LocalDateTime.now() : null);
        k8sHistoryRepo.save(history);
    }

    private int convertMemoryToGb(String memoryInMb) {
        try {
            int memoryMb = Integer.parseInt(memoryInMb);
            return memoryMb % 1024 == 0 ? memoryMb / 1024 : memoryMb;
        } catch (NumberFormatException e) {
            log.error("잘못된 메모리 크기: " + memoryInMb);
            return -1;
        }
    }
}