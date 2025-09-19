package kr.co.mcmp.softwarecatalog.application.service.impl;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import kr.co.mcmp.softwarecatalog.application.constants.PackageType;
import kr.co.mcmp.softwarecatalog.application.model.HelmChart;
import kr.co.mcmp.softwarecatalog.application.model.PackageInfo;
import kr.co.mcmp.softwarecatalog.application.model.ApplicationStatus;
import kr.co.mcmp.softwarecatalog.application.model.DeploymentHistory;
import kr.co.mcmp.softwarecatalog.application.model.DeploymentLog;
import kr.co.mcmp.softwarecatalog.application.model.OperationHistory;
import kr.co.mcmp.softwarecatalog.application.dto.IntegratedApplicationInfoDTO;
import kr.co.mcmp.softwarecatalog.application.repository.HelmChartRepository;
import kr.co.mcmp.softwarecatalog.application.repository.PackageInfoRepository;
import kr.co.mcmp.softwarecatalog.application.repository.ApplicationStatusRepository;
import kr.co.mcmp.softwarecatalog.application.repository.DeploymentHistoryRepository;
import kr.co.mcmp.softwarecatalog.application.repository.DeploymentLogRepository;
import kr.co.mcmp.softwarecatalog.application.repository.OperationHistoryRepository;
import kr.co.mcmp.softwarecatalog.SoftwareCatalog;
import kr.co.mcmp.softwarecatalog.CatalogRepository;
import kr.co.mcmp.softwarecatalog.category.dto.KeyValueDTO;
import kr.co.mcmp.softwarecatalog.category.dto.SoftwareCatalogRequestDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.co.mcmp.softwarecatalog.application.service.ApplicationService;
import kr.co.mcmp.softwarecatalog.application.service.NexusIntegrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 애플리케이션 등록 및 수정을 담당하는 서비스 구현체
 * 배포 및 운영 작업은 ApplicationOrchestrationService를 사용하세요
 */
@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class ApplicationServiceImpl implements ApplicationService {

    private final NexusIntegrationService nexusIntegrationService;

    private final PackageInfoRepository packageInfoRepository;

    private final HelmChartRepository helmChartRepository;
    
    private final ApplicationStatusRepository applicationStatusRepository;
    
    private final DeploymentHistoryRepository deploymentHistoryRepository;
    
    private final DeploymentLogRepository deploymentLogRepository;
    
    private final OperationHistoryRepository operationHistoryRepository;
    
    private final CatalogRepository catalogRepository;

    // ===== 넥서스 연동 관련 메서드 (애플리케이션 배포/운영용) =====
    
    /**
     * 넥서스에서 애플리케이션을 조회합니다.
     * 
     * @param applicationName 애플리케이션 이름
     * @return 넥서스 애플리케이션 정보
     */
    @Override
    public Object getApplicationFromNexus(String applicationName) {
        log.info("Getting application from Nexus: {}", applicationName);
        
        return nexusIntegrationService.getFromNexus(applicationName);
    }
    
    /**
     * 넥서스에서 모든 애플리케이션을 조회합니다.
     * 
     * @return 넥서스 애플리케이션 목록
     */
    @Override
    public List<Object> getAllApplicationsFromNexus() {
        log.info("Getting all applications from Nexus");
        
        return (List<Object>) (List<?>) nexusIntegrationService.getAllFromNexus();
    }
    
    /**
     * 넥서스에서 이미지 태그 목록을 조회합니다.
     * 
     * @param imageName 이미지 이름
     * @return 태그 목록
     */
    @Override
    public List<String> getImageTagsFromNexus(String imageName) {
        log.info("Getting image tags from Nexus: {}", imageName);
        
        return nexusIntegrationService.getImageTagsFromNexus(imageName);
    }
    
    /**
     * 넥서스에서 이미지를 풀합니다.
     * 
     * @param imageName 이미지 이름
     * @param tag 이미지 태그
     * @return 풀 결과
     */
    @Override
    public Object pullImageFromNexus(String imageName, String tag) {
        log.info("Pulling image from Nexus: {}:{}", imageName, tag);
        
        return nexusIntegrationService.pullImageFromNexus(imageName, tag);
    }

    /**
     * DB에 Application의 Category를 조회합니다.
     *
     * @param dto
     */
    @Override
    public List<KeyValueDTO> getCategoriesFromDB(SoftwareCatalogRequestDTO.SearchCatalogListDTO dto) {
        log.info("Getting categories from DB: {}", dto.getTarget());
        List<String> result = new ArrayList<>();

        // VM
        if(PackageType.valueOf("DOCKER").equals(dto.getTarget()))
            result = packageInfoRepository.findDistinctCategories();

        // K8S
        else if(PackageType.valueOf("HELM").equals(dto.getTarget()))
            result = helmChartRepository.findDistinctCategories();

        // String -> KeyValueDTO 변환
        return result.stream()
                .filter(Objects::nonNull) // null 값 제거
                .map(category -> KeyValueDTO.builder()
                        .key(category)
                        .value(category) // 필요하다면 다른 값 매핑 가능
                        .build())
                .collect(Collectors.toList());
    }


    /**
     * DB에 Application를 조회합니다.
     *
     * @param dto
     */
    @Override
    public List<KeyValueDTO> getPackageInfoFromDB(SoftwareCatalogRequestDTO.SearchPackageListDTO dto) {
        log.info("Getting package info from DB: {} {}", dto.getTarget(), dto.getCategory());

        List<KeyValueDTO> result = new ArrayList<>();

        // VM
        if(PackageType.valueOf("DOCKER").equals(dto.getTarget())) {
            List<PackageInfo> packageInfoList = packageInfoRepository.findByCategories(dto.getCategory());
            result = packageInfoList.stream()
                    .filter(Objects::nonNull)
                    .map(packageInfo -> KeyValueDTO.builder()
                            .key(packageInfo.getPackageName())
                            .value(packageInfo.getPackageName())
                            .build())
                    .collect(Collectors.toList());
        }

        // K8S
        else if(PackageType.valueOf("HELM").equals(dto.getTarget())) {
            List<HelmChart> helmChartList = helmChartRepository.findByCategory(dto.getCategory());
            result = helmChartList.stream()
                    .map(helmChart -> KeyValueDTO.builder()
                            .key(helmChart.getChartName())
                            .value(helmChart.getChartName())
                            .build())  // 헬름차트 전용 변환 메서드
                    .collect(Collectors.toList());
        }

        return result;
    }

    /**
     * DB에 Application Version을 조회합니다.
     *
     * @param dto
     */
    @Override
    public List<KeyValueDTO> getPackageVersionFromDB(SoftwareCatalogRequestDTO.SearchPackageVersionListDTO dto) {
        log.info("Getting package versions from DB: {}", dto.getApplicationName());
        List<KeyValueDTO> result = new ArrayList<>();
        
        if(PackageType.valueOf("DOCKER").equals(dto.getTarget())) {
            List<Object[]> packageInfoList = packageInfoRepository.findDistinctPackageVersionByPackageName(dto.getApplicationName());
            result = packageInfoList.stream()
                    .filter(Objects::nonNull)
                    .map(row -> KeyValueDTO.builder()
                            .key((String) row[0]) // packageVersion
                            .value((String) row[0]) // packageVersion
                            .isUsed(row[1] != null) // catalog.id가 null이 아니면 사용중
                            .build())
                    .collect(Collectors.toList());
        }
        else if(PackageType.valueOf("HELM").equals(dto.getTarget())) {
            List<Object[]> helmChartList = helmChartRepository.findDistinctPackageVersionByChartName(dto.getApplicationName());
            result = helmChartList.stream()
                    .filter(Objects::nonNull)
                    .map(row -> KeyValueDTO.builder()
                            .key((String) row[0]) // chartVersion
                            .value((String) row[0]) // chartVersion
                            .isUsed(row[1] != null) // catalog.id가 null이 아니면 사용중
                            .build())
                    .collect(Collectors.toList());
        }
        
        return result;
    }

    // ===== 애플리케이션 상태/배포 관련 조회 메서드 =====

    /**
     * 모든 애플리케이션 상태를 조회합니다.
     */
    @Override
    public List<ApplicationStatus> getAllApplicationStatus() {
        log.info("Getting all application status");
        return applicationStatusRepository.findAll();
    }

    /**
     * 특정 애플리케이션 상태의 에러 로그를 조회합니다.
     */
    @Override
    public List<String> getApplicationErrorLogs(Long applicationStatusId) {
        log.info("Getting error logs for application status ID: {}", applicationStatusId);
        ApplicationStatus status = applicationStatusRepository.findById(applicationStatusId).orElse(null);
        if (status == null) {
            log.warn("Application status not found for ID: {}", applicationStatusId);
            return List.of();
        }
        return status.getErrorLogs();
    }

    /**
     * 모든 배포 이력을 조회합니다.
     */
    @Override
    public List<DeploymentHistory> getAllDeploymentHistory() {
        log.info("Getting all deployment history");
        return deploymentHistoryRepository.findAll();
    }

    /**
     * 모든 배포 로그를 조회합니다.
     */
    @Override
    public List<DeploymentLog> getAllDeploymentLogs() {
        log.info("Getting all deployment logs");
        return deploymentLogRepository.findAll();
    }

    /**
     * 모든 운영 이력을 조회합니다.
     */
    @Override
    public List<OperationHistory> getAllOperationHistory() {
        log.info("Getting all operation history");
        return operationHistoryRepository.findAll();
    }

    /**
     * 특정 애플리케이션의 모든 상태/배포/로그 정보를 통합 조회합니다.
     */
    @Override
    public Map<String, Object> getIntegratedApplicationInfo(Long catalogId) {
        log.info("Getting integrated application info for catalog ID: {}", catalogId);
        
        Map<String, Object> result = new HashMap<>();
        
        // 애플리케이션 상태 조회
        List<ApplicationStatus> applicationStatuses = applicationStatusRepository.findByCatalogId(catalogId).map(List::of).orElse(List.of());
        result.put("applicationStatuses", applicationStatuses);
        log.debug("Found {} application statuses for catalog ID {}", applicationStatuses.size(), catalogId);
        
        // 배포 이력 조회
        List<DeploymentHistory> deploymentHistories = deploymentHistoryRepository.findByCatalogIdOrderByExecutedAtDesc(catalogId);
        result.put("deploymentHistories", deploymentHistories);
        log.debug("Found {} deployment histories for catalog ID {}", deploymentHistories.size(), catalogId);
        
        // 배포 로그 조회 (각 배포 이력에 대한 로그)
        List<Map<String, Object>> deploymentLogsWithHistory = new ArrayList<>();
        for (DeploymentHistory history : deploymentHistories) {
            List<DeploymentLog> logs = deploymentLogRepository.findByDeploymentIdOrderByLoggedAtDesc(history.getId());
            Map<String, Object> historyWithLogs = new HashMap<>();
            historyWithLogs.put("deploymentHistory", history);
            historyWithLogs.put("logs", logs);
            deploymentLogsWithHistory.add(historyWithLogs);
        }
        result.put("deploymentLogsWithHistory", deploymentLogsWithHistory);
        log.debug("Found {} deployment logs with history for catalog ID {}", deploymentLogsWithHistory.size(), catalogId);
        
        // 운영 이력 조회 (애플리케이션 상태별)
        List<Map<String, Object>> operationHistoriesWithStatus = new ArrayList<>();
        for (ApplicationStatus status : applicationStatuses) {
            List<OperationHistory> operations = operationHistoryRepository.findByApplicationStatusId(status.getId());
            Map<String, Object> statusWithOperations = new HashMap<>();
            statusWithOperations.put("applicationStatus", status);
            statusWithOperations.put("operations", operations);
            operationHistoriesWithStatus.add(statusWithOperations);
        }
        result.put("operationHistoriesWithStatus", operationHistoriesWithStatus);
        log.debug("Found {} operation histories with status for catalog ID {}", operationHistoriesWithStatus.size(), catalogId);
        
        // 에러 로그 조회 (각 애플리케이션 상태별)
        List<Map<String, Object>> errorLogsWithStatus = new ArrayList<>();
        for (ApplicationStatus status : applicationStatuses) {
            Map<String, Object> statusWithErrors = new HashMap<>();
            statusWithErrors.put("applicationStatus", status);
            statusWithErrors.put("errorLogs", status.getErrorLogs());
            errorLogsWithStatus.add(statusWithErrors);
        }
        result.put("errorLogsWithStatus", errorLogsWithStatus);
        log.debug("Found {} error logs with status for catalog ID {}", errorLogsWithStatus.size(), catalogId);
        
        // INFO 로그 조회 (각 애플리케이션 상태별)
        List<Map<String, Object>> infoLogsWithStatus = new ArrayList<>();
        for (ApplicationStatus status : applicationStatuses) {
            Map<String, Object> statusWithInfo = new HashMap<>();
            statusWithInfo.put("applicationStatus", status);
            statusWithInfo.put("infoLogs", status.getInfoLogs());
            infoLogsWithStatus.add(statusWithInfo);
        }
        result.put("infoLogsWithStatus", infoLogsWithStatus);
        log.debug("Found {} info logs with status for catalog ID {}", infoLogsWithStatus.size(), catalogId);
        
        // DEBUG 로그 조회 (각 애플리케이션 상태별)
        List<Map<String, Object>> debugLogsWithStatus = new ArrayList<>();
        for (ApplicationStatus status : applicationStatuses) {
            Map<String, Object> statusWithDebug = new HashMap<>();
            statusWithDebug.put("applicationStatus", status);
            statusWithDebug.put("debugLogs", status.getDebugLogs());
            debugLogsWithStatus.add(statusWithDebug);
        }
        result.put("debugLogsWithStatus", debugLogsWithStatus);
        log.debug("Found {} debug logs with status for catalog ID {}", debugLogsWithStatus.size(), catalogId);
        
        // Pod 로그 조회 (각 애플리케이션 상태별)
        List<Map<String, Object>> podLogsWithStatus = new ArrayList<>();
        for (ApplicationStatus status : applicationStatuses) {
            Map<String, Object> statusWithPod = new HashMap<>();
            statusWithPod.put("applicationStatus", status);
            statusWithPod.put("podLogs", status.getPodLogs());
            podLogsWithStatus.add(statusWithPod);
        }
        result.put("podLogsWithStatus", podLogsWithStatus);
        log.debug("Found {} pod logs with status for catalog ID {}", podLogsWithStatus.size(), catalogId);
        
        log.info("Successfully retrieved integrated application info for catalog ID: {}", catalogId);
        return result;
    }
    
    /**
     * 특정 배포의 모든 상태/배포/로그 정보를 통합 조회합니다.
     */
    @Override
    public Map<String, Object> getIntegratedApplicationInfoByDeploymentId(Long deploymentId) {
        log.info("Getting integrated application info for deployment ID: {}", deploymentId);
        
        // 배포 이력 조회
        DeploymentHistory deploymentHistory = deploymentHistoryRepository.findById(deploymentId)
                .orElseThrow(() -> new RuntimeException("Deployment not found with ID: " + deploymentId));
        
        // 해당 배포의 카탈로그 ID로 애플리케이션 상태 조회
        Long catalogId = deploymentHistory.getCatalog().getId();
        List<ApplicationStatus> applicationStatuses = applicationStatusRepository.findByCatalogId(catalogId).map(List::of).orElse(List.of());
        
        // 해당 배포의 로그 조회
        List<DeploymentLog> logs = deploymentLogRepository.findByDeploymentIdOrderByLoggedAtDesc(deploymentId);
        
        // 운영 이력 조회
        List<OperationHistory> operations = new ArrayList<>();
        for (ApplicationStatus status : applicationStatuses) {
            operations.addAll(operationHistoryRepository.findByApplicationStatusId(status.getId()));
        }
        
        // 에러 로그 조회
        List<String> errorLogs = new ArrayList<>();
        for (ApplicationStatus status : applicationStatuses) {
            errorLogs.addAll(status.getErrorLogs());
        }
        
        // 간소화된 DTO로 변환
        IntegratedApplicationInfoDTO dto = convertToIntegratedDTO(deploymentHistory, applicationStatuses, logs, operations, errorLogs, 
                applicationStatuses.isEmpty() ? List.of() : applicationStatuses.get(0).getInfoLogs(),
                applicationStatuses.isEmpty() ? List.of() : applicationStatuses.get(0).getDebugLogs(),
                applicationStatuses.isEmpty() ? List.of() : applicationStatuses.get(0).getPodLogs());
        
        Map<String, Object> result = new HashMap<>();
        result.put("integratedInfo", dto);
        
        log.info("Successfully retrieved integrated application info for deployment ID: {}", deploymentId);
        return result;
    }

    @Override
    @Transactional
    public String updateIngressConfiguration(Long catalogId, Map<String, Object> ingressConfig) {
        log.info("Updating Ingress configuration for catalog ID: {}", catalogId);
        
        SoftwareCatalog catalog = catalogRepository.findById(catalogId)
                .orElseThrow(() -> new RuntimeException("Software catalog not found with ID: " + catalogId));
        
        // Ingress 설정 업데이트
        if (ingressConfig.containsKey("enabled")) {
            catalog.setIngressEnabled((Boolean) ingressConfig.get("enabled"));
        }
        if (ingressConfig.containsKey("host")) {
            catalog.setIngressHost((String) ingressConfig.get("host"));
        }
        if (ingressConfig.containsKey("path")) {
            catalog.setIngressPath((String) ingressConfig.get("path"));
        }
        if (ingressConfig.containsKey("class")) {
            catalog.setIngressClass((String) ingressConfig.get("class"));
        }
        if (ingressConfig.containsKey("tlsEnabled")) {
            catalog.setIngressTlsEnabled((Boolean) ingressConfig.get("tlsEnabled"));
        }
        if (ingressConfig.containsKey("tlsSecret")) {
            catalog.setIngressTlsSecret((String) ingressConfig.get("tlsSecret"));
        }
        
        catalog.setUpdatedAt(LocalDateTime.now());
        catalogRepository.save(catalog);
        
        log.info("Successfully updated Ingress configuration for catalog ID: {}", catalogId);
        return "Ingress configuration updated successfully";
    }
    
    /**
     * 엔티티들을 간소화된 DTO로 변환합니다.
     */
    private IntegratedApplicationInfoDTO convertToIntegratedDTO(
            DeploymentHistory deploymentHistory, 
            List<ApplicationStatus> applicationStatuses, 
            List<DeploymentLog> logs, 
            List<OperationHistory> operations, 
            List<String> errorLogs,
            List<String> infoLogs,
            List<String> debugLogs,
            List<String> podLogs) {
        
        // 기본 배포 정보 설정
        IntegratedApplicationInfoDTO.IntegratedApplicationInfoDTOBuilder builder = IntegratedApplicationInfoDTO.builder()
                .deploymentId(deploymentHistory.getId())
                .catalogId(deploymentHistory.getCatalog().getId())
                .catalogName(deploymentHistory.getCatalog().getName())
                .catalogDescription(deploymentHistory.getCatalog().getDescription())
                .catalogCategory(deploymentHistory.getCatalog().getCategory())
                .defaultPort(deploymentHistory.getCatalog().getDefaultPort())
                .logoUrlLarge(deploymentHistory.getCatalog().getLogoUrlLarge())
                .logoUrlSmall(deploymentHistory.getCatalog().getLogoUrlSmall())
                // Ingress 정보 설정
                .ingressEnabled(deploymentHistory.getCatalog().getIngressEnabled())
                .ingressHost(deploymentHistory.getCatalog().getIngressHost())
                .ingressPath(deploymentHistory.getCatalog().getIngressPath())
                .ingressClass(deploymentHistory.getCatalog().getIngressClass())
                .ingressTlsEnabled(deploymentHistory.getCatalog().getIngressTlsEnabled())
                .ingressTlsSecret(deploymentHistory.getCatalog().getIngressTlsSecret())
                .deploymentType(deploymentHistory.getDeploymentType())
                .namespace(deploymentHistory.getNamespace())
                .clusterName(deploymentHistory.getClusterName())
                .mciId(deploymentHistory.getMciId())
                .vmId(deploymentHistory.getVmId())
                .publicIp(deploymentHistory.getPublicIp())
                .actionType(deploymentHistory.getActionType())
                .status(deploymentHistory.getStatus())
                .executedAt(deploymentHistory.getExecutedAt())
                .executedBy(deploymentHistory.getExecutedBy() != null ? deploymentHistory.getExecutedBy().getUsername() : null)
                .cloudProvider(deploymentHistory.getCloudProvider())
                .cloudRegion(deploymentHistory.getCloudRegion())
                .servicePort(deploymentHistory.getServicePort())
                .podStatus(deploymentHistory.getPodStatus())
                .releaseName(deploymentHistory.getReleaseName())
                .errorLogs(errorLogs)
                .infoLogs(infoLogs)
                .debugLogs(debugLogs)
                .podLogs(podLogs);
        
        // 애플리케이션 상태 정보 설정 (가장 최근 상태 사용)
        if (!applicationStatuses.isEmpty()) {
            ApplicationStatus latestStatus = applicationStatuses.get(0);
            builder.applicationStatus(latestStatus.getStatus())
                   .cpuUsage(latestStatus.getCpuUsage())
                   .memoryUsage(latestStatus.getMemoryUsage())
                   .networkIn(latestStatus.getNetworkIn())
                   .networkOut(latestStatus.getNetworkOut())
                   .healthCheck(latestStatus.getIsHealthCheck())
                   .portAccessible(latestStatus.getIsPortAccessible())
                   .lastCheckedAt(latestStatus.getCheckedAt())
                   // 추가 상태 정보
                   .mciId(latestStatus.getMciId())
                   .vmId(latestStatus.getVmId())
                   .publicIp(latestStatus.getPublicIp())
                   .servicePort(latestStatus.getServicePort())
                   .podStatus(latestStatus.getPodStatus());
        }
        
        // 배포 로그 변환
        List<IntegratedApplicationInfoDTO.DeploymentLogSummaryDTO> deploymentLogSummaries = logs.stream()
                .map(log -> IntegratedApplicationInfoDTO.DeploymentLogSummaryDTO.builder()
                        .id(log.getId())
                        .logType(log.getLogType().toString())
                        .logMessage(log.getLogMessage())
                        .loggedAt(log.getLoggedAt())
                        .build())
                .collect(Collectors.toList());
        builder.deploymentLogs(deploymentLogSummaries);
        
        // 운영 이력 변환
        List<IntegratedApplicationInfoDTO.OperationHistorySummaryDTO> operationSummaries = operations.stream()
                .map(op -> IntegratedApplicationInfoDTO.OperationHistorySummaryDTO.builder()
                        .id(op.getId())
                        .operationType(op.getOperationType())
                        .reason(op.getReason())
                        .detailReason(op.getDetailReason())
                        .status("COMPLETED") // OperationHistory에는 status 필드가 없으므로 기본값 설정
                        .executedAt(op.getCreatedAt())
                        .executedBy(op.getExecutedBy() != null ? op.getExecutedBy().getUsername() : null)
                        .build())
                .collect(Collectors.toList());
        builder.operationHistories(operationSummaries);
        
        return builder.build();
    }
}
