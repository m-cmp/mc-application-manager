package kr.co.mcmp.softwarecatalog.application.service;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.persistence.EntityNotFoundException;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.github.dockerjava.api.DockerClient;

import kr.co.mcmp.ape.cbtumblebug.api.CbtumblebugRestApi;
import kr.co.mcmp.ape.cbtumblebug.dto.K8sClusterDto;
import kr.co.mcmp.ape.cbtumblebug.dto.K8sSpec;
import kr.co.mcmp.ape.cbtumblebug.dto.Spec;
import kr.co.mcmp.ape.cbtumblebug.dto.VmAccessInfo;
import kr.co.mcmp.softwarecatalog.CatalogService;
import kr.co.mcmp.softwarecatalog.SoftwareCatalogDTO;
import kr.co.mcmp.softwarecatalog.application.constants.ActionType;
import kr.co.mcmp.softwarecatalog.application.constants.DeploymentType;
import kr.co.mcmp.softwarecatalog.application.constants.LogType;
import kr.co.mcmp.softwarecatalog.application.dto.ApplicationStatusDto;
import kr.co.mcmp.softwarecatalog.application.dto.PackageInfoDTO;
import kr.co.mcmp.softwarecatalog.application.exception.ApplicationException;
import kr.co.mcmp.softwarecatalog.application.model.ApplicationStatus;
import kr.co.mcmp.softwarecatalog.application.model.DeploymentHistory;
import kr.co.mcmp.softwarecatalog.application.model.DeploymentLog;
import kr.co.mcmp.softwarecatalog.application.repository.ApplicationStatusRepository;
import kr.co.mcmp.softwarecatalog.application.repository.DeploymentHistoryRepository;
import kr.co.mcmp.softwarecatalog.application.repository.DeploymentLogRepository;
import kr.co.mcmp.softwarecatalog.docker.model.ContainerDeployResult;
import kr.co.mcmp.softwarecatalog.docker.service.ContainerStatsCollector;
import kr.co.mcmp.softwarecatalog.docker.service.DockerClientFactory;
import kr.co.mcmp.softwarecatalog.docker.service.DockerOperationService;
import kr.co.mcmp.softwarecatalog.docker.service.DockerSetupService;
import kr.co.mcmp.softwarecatalog.users.Entity.User;
import kr.co.mcmp.softwarecatalog.users.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class ApplicationService {

    private final CatalogService catalogService;
    private final ApplicationStatusRepository applicationStatusRepository;
    private final DeploymentHistoryRepository deploymentHistoryRepository;
    private final DeploymentLogRepository deploymentLogRepository;
    private final UserService userService;
    private final DockerSetupService dockerSetupService;
    private final DockerOperationService dockerOperationService;
    private final CbtumblebugRestApi cbtumblebugRestApi;
    private final DockerClientFactory dockerClientFactory;
    private final ContainerStatsCollector containerStatsCollector;

    public Map<String, Object> performDockerOperation(ActionType operation, Long applicationStatusId) throws Exception {
        ApplicationStatus applicationStatus = applicationStatusRepository.findById(applicationStatusId)
            .orElseThrow(() -> new EntityNotFoundException("ApplicationStatus not found with id: " + applicationStatusId));
    
        String host = applicationStatus.getPublicIp();
        DockerClient dockerClient = dockerClientFactory.getDockerClient(host);
        String containerName = applicationStatus.getCatalog().getTitle().toLowerCase().replaceAll("\\s+", "-");
        String containerId = containerStatsCollector.getContainerId(dockerClient, containerName);

        if (containerId == null) {
            throw new IllegalStateException("Container ID is not available for application status: " + applicationStatusId);
        }
    
        Map<String, Object> result = new HashMap<>();
        result.put("operation", operation);
        result.put("applicationStatusId", applicationStatusId);
    
        try {
            switch (operation.toString().toLowerCase()) {
                case "status":
                    String status = dockerOperationService.getDockerContainerStatus(host, containerId);
                    result.put("status", status);
                    break;
                case "stop":
                    String stopResult = dockerOperationService.stopDockerContainer(host, containerId);
                    result.put("result", stopResult);
                    break;
                case "uninstall":
                    String removeResult = dockerOperationService.removeDockerContainer(host, containerId);
                    result.put("result", removeResult);
                    break;
                case "restart":
                    String restartResult = dockerOperationService.restartDockerContainer(host, containerId);
                    result.put("result", restartResult);
                    break;
                case "isrunning":
                    boolean isRunning = dockerOperationService.isContainerRunning(host, containerId);
                    result.put("isRunning", isRunning);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown operation: " + operation);
            }
    
            // 작업 결과에 따라 ApplicationStatus 업데이트
            updateApplicationStatus(applicationStatus, operation, result);
    
        } catch (Exception e) {
            log.error("Error performing Docker operation: {} on application status: {}", operation, applicationStatusId, e);
            result.put("error", e.getMessage());
        }
    
        return result;
    }
    
    private void updateApplicationStatus(ApplicationStatus applicationStatus, ActionType operation, Map<String, Object> result) {
        switch (operation.toString().toLowerCase()) {
            case "status":
                applicationStatus.setStatus((String) result.get("status"));
                break;
            case "stop":
                applicationStatus.setStatus(ActionType.STOP.name());
                break;
            case "remove":
                applicationStatus.setStatus(ActionType.UNINSTALL.name());
                break;
            case "restart":
                applicationStatus.setStatus(ActionType.RESTART.name());
                break;
            case "isrunning":
                applicationStatus.setStatus((Boolean) result.get("isRunning") ? ActionType.RUN.name() : ActionType.STOP.name());
                break;
        }
        applicationStatus.setCheckedAt(LocalDateTime.now());
        applicationStatusRepository.save(applicationStatus);
    }

    @Transactional
    public DeploymentHistory deployApplication(String namespace, String mciId, String vmId, Long catalogId, Integer servicePort, String username) {
        SoftwareCatalogDTO catalog = catalogService.getCatalog(catalogId);
        User user = getUserOrNull(username);
        DeploymentHistory history = createDeploymentHistory(namespace, mciId, vmId, catalog, servicePort, user);

        try {
            // Docker 설치 확인 및 설치
            dockerSetupService.checkAndInstallDocker(namespace, mciId, vmId);

            // 애플리케이션 배포
            // String deployCommand = buildDeployCommand(catalog, servicePort);
            Map<String, String> deployParams = buildDeployParameters(catalog, servicePort);
            VmAccessInfo vmAccessInfo = cbtumblebugRestApi.getVmInfo(namespace, mciId, vmId);
            // ContainerDeployResult deployResult = dockerOperationService.runDockerContainer(namespace, mciId, vmId, null, deployCommand);
            // ContainerDeployResult deployResult = dockerOperationService.runDockerContainer(vmAccessInfo.getPublicIP(), deployCommand);
            ContainerDeployResult deployResult = dockerOperationService.runDockerContainer(vmAccessInfo.getPublicIP(), deployParams);
            
            String containerId = deployResult.getContainerId();
            String result = deployResult.getDeploymentResult();
            
            log.info("Deployment result: {}", result);
            log.info("Container ID: {}", containerId);

            if (containerId != null && !containerId.isEmpty()) {
                // boolean isRunning = dockerOperationService.isContainerRunning(namespace, mciId, vmId, null, containerId);
                boolean isRunning = dockerOperationService.isContainerRunning(vmAccessInfo.getPublicIP(), containerId);
                if (isRunning) {
                    log.info("Container is running");
                    history.setStatus("SUCCESS");
                    updateApplicationStatus(history, "RUNNING", user);
                    addDeploymentLog(history, LogType.INFO, "Deployment successful and container is running. Container ID: " + containerId);
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
            updateApplicationStatus(history, "FAILED", user);
            addDeploymentLog(history, LogType.ERROR, "Deployment failed: " + e.getMessage());
        }

        return deploymentHistoryRepository.save(history);
    }

    public Map<String, String> buildDeployParameters(SoftwareCatalogDTO catalog, Integer servicePort) {
        PackageInfoDTO packageInfo = catalog.getPackageInfo();
        if (packageInfo == null) {
            throw new IllegalArgumentException("PackageInfo is not available for deployment");
        }
        Integer containerPort = catalog.getDefaultPort() != null ? catalog.getDefaultPort() : servicePort;
        Map<String, String> params = new HashMap<>();
        params.put("name", catalog.getTitle().toLowerCase().replaceAll("\\s+", "-"));
        params.put("image", packageInfo.getPackageName().toLowerCase() + ":" + packageInfo.getPackageVersion().toLowerCase());
        params.put("portBindings", servicePort + ":" + containerPort);
    
        return params;
    }
    
    /* 
    public String buildDeployCommand(SoftwareCatalogDTO catalog, Integer servicePorts) {
        PackageInfoDTO packageInfo = catalog.getPackageInfo();
        if (packageInfo == null) {
            throw new IllegalArgumentException("PackageInfo is not available for deployment");
        }

        StringBuilder command = new StringBuilder("docker run -d");

        // 컨테이너 이름 설정
        String containerName = catalog.getTitle().toLowerCase().replaceAll("\\s+", "-");
        command.append(String.format(" --name %s", containerName));

        // 포트 매핑
        // List<Integer> defaultPorts = catalog.getDefaultPort();
        // for (int i = 0; i < Math.min(servicePorts.size(), defaultPorts.size()); i++) {
        //     command.append(String.format(" -p %d:%d", servicePorts.get(i), defaultPorts.get(i)));
        // }

        // 포트 매핑
        
        command.append(String.format(" -p %d:%d", servicePorts, servicePorts)); 

        // 리소스 제한 설정
        // if (catalog.getMinCpu() != null) {
        //     command.append(String.format(" --cpus=%f", catalog.getMinCpu()));
        // }
        // if (catalog.getMinMemory() != null) {
        //     command.append(String.format(" --memory=%dM", catalog.getMinMemory()));
        // }

        // 이미지 지정
        String imageName = packageInfo.getPackageName().toLowerCase();
        String imageTag = packageInfo.getPackageVersion().toLowerCase();
        command.append(String.format(" %s:%s", imageName, imageTag));

        log.info("Generated Docker command: {}", command);
        return command.toString();
    }
 
    private int getDefaultPort(String packageName) {
        switch (packageName.toLowerCase()) {
            case "nginx":
            case "httpd":
                return 80;
            case "tomcat":
                return 8080;
            case "redis":
                return 6379;
            case "mariadb":
            case "mysql":
                return 3306;
            case "nexus repository":
                return 8081;  // Nexus Repository의 기본 웹 인터페이스 포트
            case "grafana":
                return 3000;
            case "prometheus":
                return 9090;
            case "mongodb":
                return 27017;
            case "postgresql":
                return 5432;
            case "elasticsearch":
                return 9200;
            case "rabbitmq":
                return 5672;  // AMQP 포트, 관리 인터페이스는 15672
            case "jenkins":
                return 8080;
            case "zookeeper":
                return 2181;
            case "cassandra":
                return 9042;
            case "kafka":
                return 9092;
            case "memcached":
                return 11211;
            case "haproxy":
                return 80;  // 기본 HTTP 포트, 관리 인터페이스는 다를 수 있음
            default:
                return 0;  // 기본값
        }
    }

    private Map<String, String> getEnvironmentVariables(String packageName) {
        Map<String, String> envVars = new HashMap<>();
        switch (packageName.toLowerCase()) {
            case "mariadb":
            case "mysql":
                envVars.put("MYSQL_ROOT_PASSWORD", "changeme");
                break;
            case "redis":
                envVars.put("REDIS_PASSWORD", "changeme");
                break;
            // 다른 패키지에 대한 환경 변수 추가
        }
        return envVars;
    }

    private String getVolumeMount(String packageName) {
        switch (packageName.toLowerCase()) {
            case "mariadb":
            case "mysql":
                return " -v mysql_data:/var/lib/mysql";
            case "redis":
                return " -v redis_data:/data";
            // 다른 패키지에 대한 볼륨 마운트 추가
            default:
                return null;
        }
    }

    private String parseRegistryFromUrl(String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }
        if (url.contains("docker.io") || url.contains("hub.docker.com")) {
            return ""; // Docker Hub의 경우 레지스트리 주소 생략
        }
        // 다른 레지스트리의 경우, URL에서 레지스트리 주소 추출
        try {
            URL parsedUrl = new URL(url);
            return parsedUrl.getHost() + "/";
        } catch (MalformedURLException e) {
            return "";
        }
    }
*/
    public List<ApplicationStatusDto> getApplicationGroups() {
        List<Object[]> vmGroups = applicationStatusRepository.findDistinctVmGroups();
        
        return vmGroups.stream()
            .flatMap(group -> {
                String namespace = (String) group[0];
                String mciId = (String) group[1];
                String vmId = (String) group[2];
                List<ApplicationStatus> applications = applicationStatusRepository.findByNamespaceAndMciIdAndVmId(
                    namespace, mciId, vmId);
                
                return applications.stream()
                    .map(ApplicationStatusDto::fromEntity);
            })
            .collect(Collectors.toList());
    }
    

    /**
     * 배포 이력을 생성합니다.
     *
     * @param namespace 네임스페이스
     * @param mciId MCI ID
     * @param vmId VM ID
     * @param catalog 소프트웨어 카탈로그
     * @param user 사용자
     * @return 배포 이력
     */
    private DeploymentHistory createDeploymentHistory(String namespace, String mciId, String vmId, SoftwareCatalogDTO catalog, Integer servicePort, User user) {
        VmAccessInfo vmInfo = cbtumblebugRestApi.getVmInfo(namespace, mciId, vmId);
        String[] parts = vmInfo.getConnectionName().split("-");
        return DeploymentHistory.builder()
                .catalog(catalog.toEntity())
                .deploymentType(DeploymentType.VM)
                .cloudProvider(parts.length > 0 ? parts[0] : "") // 예: "aws-ap-northeast-2" -> "aws"
                .cloudRegion(vmInfo.getRegion().getRegion())
                .namespace(namespace)
                .mciId(mciId)
                .vmId(vmId)
                .publicIp(vmInfo.getPublicIP()) 
                .actionType(ActionType.INSTALL)
                .status("IN_PROGRESS")
                .servicePort(servicePort)
                .executedAt(LocalDateTime.now())
                .executedBy(user)
                .build();
    }

    /**
     * 애플리케이션 상태를 업데이트합니다.
     *
     * @param history 배포 이력
     * @param status 상태
     * @param user 사용자
     */
    private void updateApplicationStatus(DeploymentHistory history, String status, User user) {
        ApplicationStatus appStatus = applicationStatusRepository.findByCatalogId(history.getCatalog().getId())
                .orElse(new ApplicationStatus());
        
        appStatus.setCatalog(history.getCatalog());
        appStatus.setStatus(status);
        appStatus.setDeploymentType(history.getDeploymentType());
        appStatus.setCheckedAt(LocalDateTime.now());

        applicationStatusRepository.save(appStatus);
    }

    /**
     * 배포 로그를 추가합니다.
     *
     * @param history 배포 이력
     * @param logType 로그 타입
     * @param message 로그 메시지
     */
    private void addDeploymentLog(DeploymentHistory history, LogType logType, String message) {
        DeploymentLog log = DeploymentLog.builder()
                .deployment(history)
                .logType(logType)
                .logMessage(message)
                .loggedAt(LocalDateTime.now())
                .build();
        deploymentLogRepository.save(log);
    }

    /**
     * 사용자 이름으로 사용자를 조회합니다.
     *
     * @param username 사용자 이름
     * @return 사용자 또는 null
     */
    private User getUserOrNull(String username) {
        return StringUtils.isNotBlank(username) ? userService.findUserByUsername(username).orElse(null) : null;
    }

    /**
     * 배포 이력 목록을 조회합니다.
     *
     * @param catalogId 카탈로그 ID
     * @param username 사용자 이름
     * @return 배포 이력 목록
     */
    public List<DeploymentHistory> getDeploymentHistories(Long catalogId, String username) {
        if (StringUtils.isBlank(username)) {
            return deploymentHistoryRepository.findByCatalogIdOrderByExecutedAtDesc(catalogId);
        } else {
            User user = getUserOrNull(username);
            return user != null ? 
                deploymentHistoryRepository.findByCatalogIdAndExecutedByOrderByExecutedAtDesc(catalogId, user) :
                List.of();
        }
    }

    /**
     * 배포 로그 목록을 조회합니다.
     *
     * @param deploymentId 배포 ID
     * @param username 사용자 이름
     * @return 배포 로그 목록
     */
    public List<DeploymentLog> getDeploymentLogs(Long deploymentId, String username) {
        if (StringUtils.isBlank(username)) {
            return deploymentLogRepository.findByDeploymentIdOrderByLoggedAtDesc(deploymentId);
        } else {
            User user = getUserOrNull(username);
            return user != null ? 
                deploymentLogRepository.findByDeploymentIdAndDeployment_ExecutedByOrderByLoggedAtDesc(deploymentId, user) :
                List.of();
        }
    }

    /**
     * 애플리케이션 상태를 조회합니다.
     *
     * @param catalogId 카탈로그 ID
     * @param username 사용자 이름
     * @return 애플리케이션 상태
     */
    public ApplicationStatusDto getLatestApplicationStatus(String username) {
        if (StringUtils.isBlank(username)) {
            throw new IllegalArgumentException("Username cannot be blank");
        }
    
        User user = getUserOrNull(username);
        if (user == null) {
            throw new EntityNotFoundException("User not found with username: " + username);
        }
    
        ApplicationStatus status = applicationStatusRepository.findTopByExecutedByOrderByCheckedAtDesc(user)
                .orElseThrow(() -> new EntityNotFoundException("Application status not found for user: " + username));
    
        return ApplicationStatusDto.fromEntity(status);
    }

    /***
     * Vm의 스펙 정보와 현재 설치된 어플리케이션의 스펙 합을 비교 합니다.
     * @param namespace
     * @param mciId
     * @param vmId
     * @param catalogId
     * @return true : 여유, false : 부족
     */
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

    /***
     * K8s의 스펙 정보와 현재 설치된 어플리케이션의 스펙 합을 비교합니다.
     * @param namespace
     * @param clusterName
     * @param catalogId
     * @return true : 여유, false : 부족
     */
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

    /**
     * VM의 스펙 정보를 조회합니다.
     *
     * @param namespace 네임스페이스
     * @param mciId MCI ID
     * @param vmId VM ID
     * @return VM의 스펙 정보
     */
    public Spec getSpecForVm(String namespace, String mciId, String vmId) {
        log.info("Retrieving spec for VM: namespace={}, mciId={}, vmId={}", namespace, mciId, vmId);
        try {
            VmAccessInfo vmInfo = cbtumblebugRestApi.getVmInfo(namespace, mciId, vmId);
            if (vmInfo == null || StringUtils.isBlank(vmInfo.getSpecId())) {
                throw new ApplicationException("Failed to retrieve VM info or spec ID is blank");
            }
            Spec spec = cbtumblebugRestApi.getSpecBySpecId(namespace, vmInfo.getSpecId());
            log.info("Retrieved spec for VM: {}", spec);
            return spec;
        } catch (Exception e) {
            log.error("Error retrieving spec for VM: {}", e.getMessage());
            throw new ApplicationException("Failed to retrieve spec for VM : " + e.getMessage());
        }
    }

    /**
     * K8s 클러스터의 노드 스펙 정보를 조회합니다.
     *
     * @param namespace 네임스페이스
     * @param clusterName 클러스터 이름
     * @return K8s 노드의 스펙 정보
     */
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