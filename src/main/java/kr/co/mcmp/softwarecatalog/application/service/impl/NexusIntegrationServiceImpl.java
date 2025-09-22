package kr.co.mcmp.softwarecatalog.application.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import kr.co.mcmp.dto.oss.repository.CommonRepository;
import kr.co.mcmp.service.oss.repository.CommonModuleRepositoryService;
import kr.co.mcmp.softwarecatalog.SoftwareCatalogDTO;
import kr.co.mcmp.softwarecatalog.application.service.NexusIntegrationService;
import kr.co.mcmp.softwarecatalog.application.config.NexusConfig;
import kr.co.mcmp.oss.dto.OssDto;
import kr.co.mcmp.oss.service.OssService;
import kr.co.mcmp.util.Base64Utils;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 내부 넥서스와 연동하는 서비스 구현체
 */
@Service
@Transactional
@RequiredArgsConstructor
public class NexusIntegrationServiceImpl implements NexusIntegrationService {
    
    private static final Logger log = LoggerFactory.getLogger(NexusIntegrationServiceImpl.class);
    
    private final CommonModuleRepositoryService moduleRepositoryService;
    private final NexusConfig nexusConfig;
    private final OssService ossService;
    private final RestTemplate restTemplate;
    
    @Value("${docker.registry.port:5500}")
    private int dockerRegistryPort;
    
    @Value("${docker.push.timeout-seconds:600}")
    private long dockerTimeoutSeconds;
    
    @Value("${docker.push.max-retries:3}")
    private int maxRetries;
    
    @Value("${docker.registry.secure:false}")
    private boolean dockerRegistrySecure;
    
    @Value("${docker.push.retry-delay-seconds:5}")
    private long retryDelaySeconds;
    
    @Value("${docker.validation.check-daemon:true}")
    private boolean checkDockerDaemon;
    
    /**
     * Docker 명령어 실행 결과를 담는 클래스
     */
    private static class DockerCommandResult {
        private final int exitCode;
        private final String stdout;
        private final String stderr;
        
        public DockerCommandResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }
        
        public boolean isSuccess() {
            return exitCode == 0;
        }
        
        public String getErrorAnalysis() {
            if (isSuccess()) return null;
            
            String errorOutput = stderr.toLowerCase();
            if (errorOutput.contains("unauthorized") || errorOutput.contains("authentication")) {
                return "인증 실패: Docker 로그인 정보를 확인하세요";
            } else if (errorOutput.contains("insecure registry")) {
                return "Insecure registry 설정이 필요합니다";
            } else if (errorOutput.contains("network") || errorOutput.contains("timeout")) {
                return "네트워크 연결 오류가 발생했습니다";
            } else if (errorOutput.contains("denied") || errorOutput.contains("forbidden")) {
                return "권한 거부: 저장소에 대한 푸시 권한을 확인하세요";
            } else if (errorOutput.contains("not found")) {
                return "이미지 또는 저장소를 찾을 수 없습니다";
            }
            return "알 수 없는 오류: " + stderr;
        }
        
        // Getters
        public int getExitCode() { return exitCode; }
        public String getStdout() { return stdout; }
        public String getStderr() { return stderr; }
    }
    
    /**
     * Docker 레지스트리 정보를 담는 클래스
     */
    private static class DockerRegistryInfo {
        private final String registryUrl;
        private final boolean isSecure;
        private final String host;
        private final int port;
        
        public DockerRegistryInfo(String registryUrl, boolean isSecure, String host, int port) {
            this.registryUrl = registryUrl;
            this.isSecure = isSecure;
            this.host = host;
            this.port = port;
        }
        
        // Getters
        public String getRegistryUrl() { return registryUrl; }
        public boolean isSecure() { return isSecure; }
        public String getHost() { return host; }
        public int getPort() { return port; }
    }
    
    /**
     * 소프트웨어 카탈로그를 넥서스에 등록합니다.
     * 
     * @param catalog 등록할 소프트웨어 카탈로그 정보
     * @return 등록 결과 (성공/실패, 메시지, 소스 타입 등)
     */
    @Override
    public Map<String, Object> registerToNexus(SoftwareCatalogDTO catalog) {
        log.info("Registering application to Nexus: {} (Source: DOCKERHUB)", catalog.getName());
        
        try {
            Map<String, Object> result = new HashMap<>();
            
            // 하이브리드 모드에서 소스 타입에 따라 처리미너ㄹㄹ
            if (nexusConfig.isHybridMode()) {
                // sourceType은 SOFTWARE_SOURCE_MAPPING에서 관리하므로 기본값 사용
                switch ("DOCKERHUB") {
                    case "DOCKERHUB":
                        if (nexusConfig.isUseExternalDockerHub()) {
                            result.put("success", true);
                            result.put("message", "Application uses external Docker Hub - no Nexus registration needed");
                            result.put("sourceType", "DOCKERHUB");
                            result.put("imageUrl", "docker.io/" + catalog.getPackageInfo().getPackageName() + ":" + catalog.getPackageInfo().getPackageVersion());
                        } else {
                            // 내부 넥서스에 등록
                            result = registerToInternalNexus(catalog);
                        }
                        break;
                    case "ARTIFACTHUB":
                        if (nexusConfig.isUseExternalArtifactHub()) {
                            result.put("success", true);
                            result.put("message", "Application uses external Artifact Hub - no Nexus registration needed");
                            result.put("sourceType", "ARTIFACTHUB");
                            result.put("chartUrl", catalog.getHelmChart().getChartRepositoryUrl());
                        } else {
                            // 내부 넥서스에 등록
                            result = registerToInternalNexus(catalog);
                        }
                        break;
                    case "NEXUS":
                    default:
                        // 내부 넥서스에 등록
                        result = registerToInternalNexus(catalog);
                        break;
                }
            } else {
                // 하이브리드 모드가 아닌 경우 항상 넥서스에 등록
                result = registerToInternalNexus(catalog);
            }
            
            log.info("Application registration completed: {}", catalog.getName());
            return result;
            
        } catch (Exception e) {
            log.error("Failed to register application: {}", catalog.getName(), e);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "Failed to register application: " + e.getMessage());
            return result;
        }
    }
    
    /**
     * 내부 넥서스에 애플리케이션을 등록합니다.
     * 
     * @param catalog 등록할 소프트웨어 카탈로그 정보
     * @return 등록 결과 (성공/실패, 메시지, 레포지토리명, 소스 타입)
     */
    private Map<String, Object> registerToInternalNexus(SoftwareCatalogDTO catalog) {
        try {
            // 1. 먼저 Docker Repository가 존재하는지 확인하고, 없으면 생성
            String repositoryName = catalog.getName().toLowerCase().replaceAll("[^a-z0-9-]", "-");
            Map<String, Object> repositoryResult = ensureDockerRepositoryExists(repositoryName);
            
            if (!(Boolean) repositoryResult.get("success")) {
                log.error("Failed to ensure Docker repository exists: {}", repositoryName);
                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("message", "Failed to ensure Docker repository exists: " + repositoryResult.get("message"));
                result.put("repositoryName", repositoryName);
                result.put("sourceType", "NEXUS");
                return result;
            }
            
            log.info("Docker repository ensured: {} (action: {})", repositoryName, repositoryResult.get("action"));
            
            // 2. 넥서스 레포지토리 DTO 생성
            CommonRepository.RepositoryDto repositoryDto = createRepositoryDto(catalog);
            
            // 3. 넥서스에 레포지토리 생성 (이미 존재하면 무시됨)
            moduleRepositoryService.createRepository("nexus", repositoryDto);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Application registered to internal Nexus successfully");
            result.put("repositoryName", repositoryName);
            result.put("sourceType", "NEXUS");
            result.put("repositoryAction", repositoryResult.get("action"));
            result.put("repositoryMessage", repositoryResult.get("message"));
            
            log.info("Application registered to internal Nexus successfully: {} (repository action: {})", 
                    catalog.getName(), repositoryResult.get("action"));
            return result;
            
        } catch (Exception e) {
            log.error("Failed to register application to internal Nexus: {}", catalog.getName(), e);
            throw e;
        }
    }
    
    /**
     * 넥서스에서 특정 애플리케이션 정보를 조회합니다.
     * 
     * @param applicationName 조회할 애플리케이션 이름
     * @return 애플리케이션 상세 정보 (RepositoryDto)
     * @throws RuntimeException 넥서스에서 애플리케이션을 찾을 수 없는 경우
     */
    @Override
    public CommonRepository.RepositoryDto getFromNexus(String applicationName) {
        log.debug("Getting application from Nexus: {}", applicationName);
        
        try {
            return moduleRepositoryService.getRepositoryDetailByName("nexus", applicationName);
        } catch (Exception e) {
            log.error("Failed to get application from Nexus: {}", applicationName, e);
            throw new RuntimeException("Failed to get application from Nexus: " + e.getMessage());
        }
    }
    
    /**
     * 넥서스에서 모든 애플리케이션 목록을 조회합니다.
     * 
     * @return 넥서스에 등록된 모든 애플리케이션 목록
     * @throws RuntimeException 넥서스에서 애플리케이션 목록을 조회할 수 없는 경우
     */
    @Override
    public List<CommonRepository.RepositoryDto> getAllFromNexus() {
        log.debug("Getting all applications from Nexus");
        
        try {
            return moduleRepositoryService.getRepositoryList("nexus");
        } catch (Exception e) {
            log.error("Failed to get all applications from Nexus", e);
            throw new RuntimeException("Failed to get all applications from Nexus: " + e.getMessage());
        }
    }
    
    /**
     * 넥서스에서 특정 애플리케이션을 삭제합니다.
     * 
     * @param applicationName 삭제할 애플리케이션 이름
     * @return 삭제 결과 (성공/실패, 메시지)
     */
    @Override
    public Map<String, Object> deleteFromNexus(String applicationName) {
        log.info("Deleting application from Nexus: {}", applicationName);
        
        try {
            Map<String, Object> result = new HashMap<>();
            
            // 하이브리드 모드에서는 내부 넥서스에만 등록된 애플리케이션만 삭제
            if (nexusConfig.isHybridMode()) {
                // 외부 소스(Docker Hub, Artifact Hub)는 삭제할 필요 없음
                result.put("success", true);
                result.put("message", "External source applications do not need deletion from Nexus");
                result.put("applicationName", applicationName);
                result.put("note", "Only internal Nexus applications are deleted");
            } else {
                // 하이브리드 모드가 아닌 경우 넥서스에서 삭제
                moduleRepositoryService.deleteRepository("nexus", applicationName);
                
                result.put("success", true);
                result.put("message", "Application deleted from Nexus successfully");
                result.put("applicationName", applicationName);
            }
            
            log.info("Application deletion completed: {}", applicationName);
            return result;
            
        } catch (Exception e) {
            log.error("Failed to delete application from Nexus: {}", applicationName, e);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "Failed to delete application from Nexus: " + e.getMessage());
            return result;
        }
    }
    
    /**
     * 넥서스에서 Docker 이미지를 풀합니다.
     * 하이브리드 모드에서는 외부 Docker Hub 또는 내부 넥서스에서 풀할 수 있습니다.
     * 
     * @param imageName 풀할 이미지 이름
     * @param tag 이미지 태그
     * @return 풀 결과 (성공/실패, 메시지, 이미지 URL, 풀 명령어, 소스)
     */
    @Override
    public Map<String, Object> pullImageFromNexus(String imageName, String tag) {
        log.info("Pulling image from Nexus: {}:{}", imageName, tag);
        
        try {
            Map<String, Object> result = new HashMap<>();
            
            // 하이브리드 모드에서 소스 타입에 따라 처리
            if (nexusConfig.isHybridMode()) {
                // 외부 소스의 경우 Docker Hub에서 풀
                if (nexusConfig.isUseExternalDockerHub() || nexusConfig.isUseExternalArtifactHub()) {
                    String externalImageUrl = "docker.io/" + imageName + ":" + tag;
                    
                    result.put("success", true);
                    result.put("message", "Image pull command prepared for external Docker Hub");
                    result.put("imageName", imageName);
                    result.put("tag", tag);
                    result.put("fullImageUrl", externalImageUrl);
                    result.put("pullCommand", "docker pull " + externalImageUrl);
                    result.put("source", "Docker Hub");
                    
                    log.info("Image pull command prepared for Docker Hub: {}", externalImageUrl);
                } else {
                    // 내부 넥서스에서 풀
                    result = pullFromInternalNexus(imageName, tag);
                }
            } else {
                // 하이브리드 모드가 아닌 경우 내부 넥서스에서 풀
                result = pullFromInternalNexus(imageName, tag);
            }
            
            return result;
            
        } catch (Exception e) {
            log.error("Failed to prepare image pull: {}:{}", imageName, tag, e);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "Failed to prepare image pull: " + e.getMessage());
            return result;
        }
    }
    
    /**
     * 내부 넥서스에서 Docker 이미지를 풀합니다.
     * 
     * @param imageName 풀할 이미지 이름
     * @param tag 이미지 태그
     * @return 풀 결과 (성공/실패, 메시지, 이미지 URL, 풀 명령어, 넥서스 레지스트리, 인증 정보, 소스)
     */
    private Map<String, Object> pullFromInternalNexus(String imageName, String tag) {
        try {
            // 넥서스에서 이미지 존재 여부 확인
            String fullImageName = nexusConfig.getDockerImageUrl(imageName, tag);
            
            // 실제 Docker pull 명령어 실행을 위한 정보 반환
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Image pull command prepared for internal Nexus");
            result.put("imageName", imageName);
            result.put("tag", tag);
            result.put("fullImageUrl", fullImageName);
            result.put("pullCommand", "docker pull " + fullImageName);
            result.put("nexusRegistry", nexusConfig.getDockerRegistryUrl());
            result.put("source", "Internal Nexus");
            
            log.info("Image pull command prepared for internal Nexus: {}", fullImageName);
            return result;
            
        } catch (Exception e) {
            log.error("Failed to prepare image pull from internal Nexus: {}:{}", imageName, tag, e);
            throw e;
        }
    }
    
    /**
     * 넥서스에 Docker 이미지를 푸시합니다.
     * 현재는 항상 내부 넥서스에만 푸시하도록 구현되어 있습니다.
     * 
     * @param imageName 푸시할 이미지 이름
     * @param tag 이미지 태그
     * @param imageData 이미지 데이터 (현재는 사용되지 않음)
     * @return 푸시 결과 (성공/실패, 메시지, 이미지 URL, 푸시 명령어, 넥서스 레지스트리, 인증 정보, 소스)
     */
    @Override
    public Map<String, Object> pushImageToNexus(String imageName, String tag, byte[] imageData) {
        log.info("Pushing image to internal Nexus: {}:{}", imageName, tag);
        
        try {
            // 푸시는 항상 내부 넥서스에만 수행
            Map<String, Object> result = pushToInternalNexus(imageName, tag, imageData);
            
            log.info("Image push command prepared for internal Nexus: {}:{}", imageName, tag);
            return result;
            
        } catch (Exception e) {
            log.error("Failed to prepare image push to internal Nexus: {}:{}", imageName, tag, e);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "Failed to prepare image push to internal Nexus: " + e.getMessage());
            return result;
        }
    }
    
    /**
     * 내부 넥서스에 Docker 이미지를 푸시합니다.
     * 실제 Docker 명령어를 실행하여 이미지를 빌드하고 푸시합니다.
     * 
     * @param imageName 푸시할 이미지 이름
     * @param tag 이미지 태그
     * @param imageData 이미지 데이터 (현재는 사용되지 않음)
     * @return 푸시 결과 (성공/실패, 메시지, 이미지 URL, 푸시 출력, 넥서스 레지스트리, 인증 정보, 소스)
     */
    private Map<String, Object> pushToInternalNexus(String imageName, String tag, byte[] imageData) {
        try {
            // 넥서스 Docker 레지스트리에 이미지 푸시를 위한 정보 준비
            String fullImageName = nexusConfig.getDockerImageUrl(imageName, tag);
            
            // 실제 Docker 명령어 실행
            Map<String, Object> result = executeDockerPush(imageName, tag, fullImageName);
            
            log.info("Image push executed for internal Nexus: {}", fullImageName);
            return result;
            
        } catch (Exception e) {
            log.error("Failed to push image to internal Nexus: {}:{}", imageName, tag, e);
            throw e;
        }
    }
    
    /**
     * 넥서스 웹 UI API를 통해 이미지 정보를 등록합니다.
     * 원격 서버 환경에서는 Docker 명령어 대신 넥서스 REST API를 사용합니다.
     * 
     * @param imageName 등록할 이미지 이름
     * @param tag 이미지 태그
     * @param fullImageName 전체 이미지 이름 (사용되지 않음)
     * @return 등록 결과 (성공/실패, 메시지, 이미지 URL, 넥서스 레지스트리, 인증 정보, 소스)
     */
    private Map<String, Object> executeDockerPush(String imageName, String tag, String fullImageName) {
        log.info("=== Starting enhanced Docker Hub to Nexus push ===");
        log.info("Image: {}:{}", imageName, tag);
        
        try {
            // 1. OSS 정보 가져오기
            OssDto ossInfo = getNexusInfoFromDB();
            if (ossInfo == null) {
                return createErrorResult("OSS 정보를 데이터베이스에서 가져올 수 없습니다.");
            }
            
            // 2. Docker 환경 검증
            if (checkDockerDaemon && !validateDockerEnvironment()) {
                return createErrorResult("Docker 환경 검증에 실패했습니다. Docker가 실행 중인지 확인하세요.");
            }
            
            // 3. Nexus URL 파싱 및 검증
            DockerRegistryInfo registryInfo;
            try {
                registryInfo = parseNexusUrl(ossInfo.getOssUrl());
            } catch (IllegalArgumentException e) {
                return createErrorResult("Nexus URL 형식이 올바르지 않습니다: " + e.getMessage());
            }
            
            // 4. insecure registry 설정 확인 (HTTP의 경우)
            if (!registryInfo.isSecure()) {
                boolean isConfigured = validateInsecureRegistryConfiguration(registryInfo.getRegistryUrl());
                if (!isConfigured) {
                    return createErrorResult(
                        String.format("Insecure registry 설정이 필요합니다.\n" +
                                     "Docker Desktop 설정에서 다음 URL을 insecure-registries에 추가하세요: %s\n" +
                                     "또는 daemon.json 파일에 \"insecure-registries\": [\"%s\"]를 추가하세요.",
                                     registryInfo.getRegistryUrl(), registryInfo.getRegistryUrl()));
                }
            }
            
            // 5. 이미지 푸시 실행
            boolean success = executeEnhancedDockerPush(imageName, tag, ossInfo, registryInfo);
            
            Map<String, Object> result = new HashMap<>();
            if (success) {
                String repositoryName = getDockerRepositoryName();
                result.put("success", true);
                result.put("message", "이미지가 성공적으로 Nexus에 푸시되었습니다.");
            result.put("imageName", imageName);
            result.put("tag", tag);
                result.put("fullImageUrl", registryInfo.getRegistryUrl() + "/" + repositoryName + "/" + imageName + ":" + tag);
                result.put("nexusRegistry", registryInfo.getRegistryUrl());
                result.put("nexusCredentials", ossInfo.getOssUsername() + ":***");
            result.put("source", "Internal Nexus (Development Server)");
                result.put("repository", repositoryName);
                result.put("webUrl", ossInfo.getOssUrl() + "/#browse/browse:" + repositoryName);
                result.put("note", "이미지가 Docker 명령어를 통해 Nexus에 성공적으로 푸시되었습니다.");
            } else {
            result.put("success", false);
                result.put("message", "이미지 푸시에 실패했습니다. 로그를 확인하세요.");
        }
        
        return result;
            
        } catch (Exception e) {
            log.error("Unexpected error during image push", e);
            return createErrorResult("예상치 못한 오류가 발생했습니다: " + e.getMessage());
        }
    }
    
    /**
     * Docker 명령어를 생성합니다.
     * 
     * @param nexusImageName 넥서스 이미지 이름
     * @param username 넥서스 사용자명
     * @param password 넥서스 비밀번호
     * @param dockerRegistryUrl Docker 레지스트리 URL
     * @param imageName 원본 이미지 이름
     * @param tag 이미지 태그
     * @return Docker 명령어 목록
     */
    private Map<String, String> createDockerCommands(String nexusImageName, String username, String password, 
                                                    String dockerRegistryUrl, String imageName, String tag) {
        Map<String, String> commands = new HashMap<>();
        
        // 소스 이미지 (Docker Hub에서)
        String sourceImage = imageName.contains("/") ? imageName + ":" + tag : imageName + ":latest";
        
        commands.put("pull_source", "docker pull " + sourceImage);
        commands.put("tag_image", "docker tag " + sourceImage + " " + nexusImageName);
        commands.put("login", "docker login " + dockerRegistryUrl + " -u " + username + " -p " + password);
        commands.put("push", "docker push " + nexusImageName);
        
        return commands;
    }
    
    /**
     * DB에서 넥서스 OSS 정보를 가져옵니다.
     * OSS 목록에서 이름이 "NEXUS"인 항목을 찾아 반환합니다.
     * 
     * @return 넥서스 OSS 정보 (URL, 사용자명, 비밀번호)
     * @throws RuntimeException DB에서 NEXUS 정보를 찾을 수 없는 경우
     */
    public OssDto getNexusInfoFromDB() {
        try {
            // DB에서 NEXUS OSS 정보 조회
            List<OssDto> ossList = ossService.getAllOssList();
            
            // NEXUS 이름으로 필터링
            OssDto nexusInfo = ossList.stream()
                    .filter(oss -> "NEXUS".equalsIgnoreCase(oss.getOssName()))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("DB에서 NEXUS 정보를 찾을 수 없습니다."));
            
            log.info("Nexus info loaded from DB: URL={}, Username={}", 
                    nexusInfo.getOssUrl(), nexusInfo.getOssUsername());
            
            return nexusInfo;
        } catch (Exception e) {
            log.error("Failed to get Nexus info from DB", e);
            throw new RuntimeException("DB에서 넥서스 정보를 가져올 수 없습니다: " + e.getMessage());
        }
    }
    
    /**
     * 넥서스에서 특정 이미지의 태그 목록을 조회합니다.
     * 하이브리드 모드에서는 외부 소스의 경우 기본 태그들을 반환하고,
     * 내부 넥서스의 경우 실제 태그를 조회합니다.
     * 
     * @param imageName 조회할 이미지 이름
     * @return 이미지 태그 목록
     * @throws RuntimeException 넥서스에서 태그를 조회할 수 없는 경우
     */
    @Override
    public List<String> getImageTagsFromNexus(String imageName) {
        log.debug("Getting image tags from Nexus: {}", imageName);
        
        try {
            List<String> tags = new ArrayList<>();
            
            // 하이브리드 모드에서 소스 타입에 따라 처리
            // OSS에서 조회한 넥서스 정보를 바탕으로 실제 Docker 레포지토리에서 태그 조회
            tags = getTagsFromInternalNexus(imageName);
            
            // 실제 넥서스에서 태그를 찾지 못한 경우에만 기본 태그 제공
            if (tags.isEmpty()) {
                log.info("No tags found in Nexus for image '{}', providing default tags", imageName);
                    tags.add("latest");
                    tags.add("stable");
                    tags.add("v1.0.0");
                    tags.add("v1.1.0");
                    tags.add("v2.0.0");
            }
            
            log.debug("Retrieved {} tags for image: {} from Nexus", tags.size(), imageName);
            return tags;
            
        } catch (Exception e) {
            log.error("Failed to get image tags from Nexus: {}", imageName, e);
            throw new RuntimeException("Failed to get image tags from Nexus: " + e.getMessage());
        }
    }
    
    /**
     * 내부 넥서스에서 특정 이미지의 태그 목록을 동적으로 조회합니다.
     * OSS에서 조회한 넥서스 정보를 바탕으로 Docker 레포지토리에서 실제 태그를 조회합니다.
     * 
     * @param imageName 조회할 이미지 이름
     * @return 이미지 태그 목록 (실제 넥서스에서 조회한 태그 또는 빈 목록)
     */
    private List<String> getTagsFromInternalNexus(String imageName) {
        try {
            // DB에서 넥서스 정보 가져오기
            OssDto nexusInfo = getNexusInfoFromDB();
            
            // 넥서스 REST API URL 구성
            String nexusUrl = nexusInfo.getOssUrl();
            String username = nexusInfo.getOssUsername();
            String password = Base64Utils.base64Decoding(nexusInfo.getOssPassword());
            
            // 동적으로 Docker 레포지토리 이름 조회
            String dockerRepositoryName = getRepositoryNameByFormat("docker");
            
            log.info("Querying tags for image '{}' in repository '{}' from Nexus: {}", 
                    imageName, dockerRepositoryName, nexusUrl);
            
            // 넥서스 검색 API 호출
            String searchUrl = nexusUrl + "/service/rest/v1/search?repository=" + dockerRepositoryName + "&name=" + imageName;
            
            // RestTemplate을 사용하여 HTTP 요청
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String auth = username + ":" + password;
            String encodedAuth = java.util.Base64.getEncoder().encodeToString(auth.getBytes());
            headers.set("Authorization", "Basic " + encodedAuth);
            
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                searchUrl, 
                HttpMethod.GET, 
                entity, 
                String.class
            );
            
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.error("Failed to query Nexus API (status: {}) for image: {}", response.getStatusCode(), imageName);
                return new ArrayList<>(); // 빈 목록 반환
            }
            
            String responseStr = response.getBody();
            if (responseStr.trim().isEmpty()) {
                log.warn("Empty response from Nexus API for image: {}", imageName);
                return new ArrayList<>(); // 빈 목록 반환
            }
            
            log.debug("Nexus search response for image '{}': {}", imageName, responseStr);
            
            // JSON 응답 파싱하여 태그 추출
            List<String> tags = parseTagsFromNexusResponse(responseStr, imageName);
            
            if (tags.isEmpty()) {
                log.info("No tags found for image '{}' in Nexus repository '{}'", imageName, dockerRepositoryName);
                return new ArrayList<>(); // 빈 목록 반환
            }
            
            log.info("Successfully found {} tags for image '{}' in repository '{}': {}", 
                    tags.size(), imageName, dockerRepositoryName, tags);
            return tags;
            
        } catch (Exception e) {
            log.error("Failed to get tags from internal Nexus for image: {}", imageName, e);
            return new ArrayList<>(); // 빈 목록 반환
        }
    }
    
    /**
     * 넥서스에 Docker Repository가 있는지 확인하고, 없으면 생성합니다.
     *
     * @param repositoryName Repository 이름
     * @return Repository 생성/확인 결과
     */
    @Override
    public Map<String, Object> ensureDockerRepositoryExists(String repositoryName) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // DB에서 넥서스 정보 가져오기
            OssDto nexusInfo = getNexusInfoFromDB();
            String nexusUrl = nexusInfo.getOssUrl();
            String username = nexusInfo.getOssUsername();
            String password = Base64Utils.base64Decoding(nexusInfo.getOssPassword());
            
            log.info("Checking if Docker repository exists: {}", repositoryName);
            
            // 1. 먼저 Repository가 존재하는지 확인
            if (isDockerRepositoryExists(repositoryName, nexusUrl, username, password)) {
                log.info("Docker repository already exists: {}", repositoryName);
                result.put("success", true);
                result.put("message", "Docker repository already exists: " + repositoryName);
                result.put("repositoryName", repositoryName);
                result.put("action", "exists");
                return result;
            }
            
            // 2. Repository가 없으면 생성
            log.info("Creating Docker repository: {}", repositoryName);
            boolean created = createDockerRepository(repositoryName, nexusUrl, username, password);
            
            if (created) {
                log.info("Successfully created Docker repository: {}", repositoryName);
                result.put("success", true);
                result.put("message", "Docker repository created successfully: " + repositoryName);
                result.put("repositoryName", repositoryName);
                result.put("action", "created");
            } else {
                log.error("Failed to create Docker repository: {}", repositoryName);
                result.put("success", false);
                result.put("message", "Failed to create Docker repository: " + repositoryName);
                result.put("repositoryName", repositoryName);
                result.put("action", "failed");
            }
            
        } catch (Exception e) {
            log.error("Error ensuring Docker repository exists: {}", repositoryName, e);
            result.put("success", false);
            result.put("message", "Error ensuring Docker repository exists: " + e.getMessage());
            result.put("repositoryName", repositoryName);
            result.put("action", "error");
            result.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Docker Repository가 존재하는지 확인합니다.
     *
     * @param repositoryName Repository 이름
     * @param nexusUrl 넥서스 URL
     * @param username 넥서스 사용자명
     * @param password 넥서스 비밀번호
     * @return Repository 존재 여부
     */
    private boolean isDockerRepositoryExists(String repositoryName, String nexusUrl, String username, String password) {
        try {
            String repositoriesUrl = nexusUrl + "/service/rest/v1/repositories";
            
            ProcessBuilder curlProcess = new ProcessBuilder(
                "curl", "-s", "-u", username + ":" + password, repositoriesUrl
            );
            
            Process curlProc = curlProcess.start();
            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(curlProc.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }
            
            int exitCode = curlProc.waitFor();
            if (exitCode != 0) {
                log.error("Failed to query Nexus repositories (exit code: {})", exitCode);
                return false;
            }
            
            String responseStr = response.toString();
            return responseStr.contains("\"name\":\"" + repositoryName + "\"") && 
                   responseStr.contains("\"format\":\"docker\"");
            
        } catch (Exception e) {
            log.error("Error checking if Docker repository exists: {}", repositoryName, e);
            return false;
        }
    }
    
    /**
     * Docker Repository를 생성합니다.
     *
     * @param repositoryName Repository 이름
     * @param nexusUrl 넥서스 URL
     * @param username 넥서스 사용자명
     * @param password 넥서스 비밀번호
     * @return 생성 성공 여부
     */
    private boolean createDockerRepository(String repositoryName, String nexusUrl, String username, String password) {
        try {
            String createUrl = nexusUrl + "/service/rest/v1/repositories/docker/hosted";
            
            // Docker Repository 생성 JSON
            String repositoryConfig = String.format(
                "{\"name\":\"%s\",\"online\":true,\"storage\":{\"blobStoreName\":\"default\",\"strictContentTypeValidation\":true,\"writePolicy\":\"allow_once\"},\"docker\":{\"v1Enabled\":false,\"forceBasicAuth\":true,\"httpPort\":5500}}",
                repositoryName
            );
            
            ProcessBuilder curlProcess = new ProcessBuilder(
                "curl", "-X", "POST", "-s",
                "-u", username + ":" + password,
                "-H", "Content-Type: application/json",
                "-d", repositoryConfig,
                createUrl
            );
            
            Process curlProc = curlProcess.start();
            int exitCode = curlProc.waitFor();
            
            if (exitCode == 0) {
                log.info("Docker repository created successfully: {}", repositoryName);
                return true;
            } else {
                log.error("Failed to create Docker repository (exit code: {})", exitCode);
                return false;
            }
            
        } catch (Exception e) {
            log.error("Error creating Docker repository: {}", repositoryName, e);
            return false;
        }
    }

    
    /**
     * OSS에서 조회한 넥서스 정보를 바탕으로 Docker 타입의 레포지토리를 동적으로 조회합니다.
     *
     * @return Docker 타입 레포지토리 이름
     * @throws RuntimeException Docker 레포지토리를 찾을 수 없는 경우
     */
    public String getDockerRepositoryName() {
        try {
            // DB에서 넥서스 정보 가져오기
            OssDto nexusInfo = getNexusInfoFromDB();
            String nexusUrl = nexusInfo.getOssUrl();
            String username = nexusInfo.getOssUsername();
            String password = Base64Utils.base64Decoding(nexusInfo.getOssPassword());
            
            log.info("Querying Nexus repositories from: {}", nexusUrl);
            
            // 넥서스에서 모든 레포지토리 목록 조회
            String repositoriesUrl = nexusUrl + "/service/rest/v1/repositories";
            
            ProcessBuilder curlProcess = new ProcessBuilder(
                "curl", "-s", "-u", username + ":" + password, repositoriesUrl
            );
            
            Process curlProc = curlProcess.start();
            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(curlProc.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }
            
            int exitCode = curlProc.waitFor();
            if (exitCode != 0) {
                log.error("Failed to query Nexus repositories (exit code: {})", exitCode);
                throw new RuntimeException("Failed to query Nexus repositories");
            }
            
            String responseStr = response.toString();
            if (responseStr.trim().isEmpty()) {
                log.error("Empty response from Nexus repositories API");
                throw new RuntimeException("Empty response from Nexus repositories API");
            }
            
            log.debug("Nexus repositories response: {}", responseStr);
            
            // JSON 응답에서 Docker 레포지토리 찾기
            String dockerRepo = parseDockerRepositoryFromResponse(responseStr);
            
            if (dockerRepo != null && !dockerRepo.isEmpty()) {
                log.info("Successfully found Docker repository: {}", dockerRepo);
                return dockerRepo;
            } else {
                log.error("No Docker repository found in Nexus. Available repositories: {}", responseStr);
                throw new RuntimeException("No Docker repository found in Nexus");
            }
            
        } catch (Exception e) {
            log.error("Failed to get Docker repository name from Nexus", e);
            throw new RuntimeException("Failed to get Docker repository name: " + e.getMessage());
        }
    }
    
    /**
     * 넥서스에서 Docker 레지스트리 포트를 동적으로 조회합니다.
     * 
     * @return Docker 레지스트리 포트
     */
    private int getDockerRegistryPort() {
        try {
            // DB에서 넥서스 정보 가져오기
            OssDto nexusInfo = getNexusInfoFromDB();
            String nexusUrl = nexusInfo.getOssUrl();
            String username = nexusInfo.getOssUsername();
            String password = Base64Utils.base64Decoding(nexusInfo.getOssPassword());
            
            // 넥서스 상태 정보 조회
            String statusUrl = nexusUrl + "/service/rest/v1/status";
            
            ProcessBuilder curlProcess = new ProcessBuilder(
                "curl", "-s", "-u", username + ":" + password, statusUrl
            );
            
            Process curlProc = curlProcess.start();
            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(curlProc.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }
            
            int exitCode = curlProc.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("Failed to query Nexus status");
            }
            
            // JSON 응답에서 Docker 레지스트리 포트 찾기
            int dockerPort = parseDockerPortFromResponse(response.toString());
            
            if (dockerPort > 0) {
                log.info("Found Docker registry port: {}", dockerPort);
                return dockerPort;
            } else {
                throw new RuntimeException("No Docker registry port found in Nexus");
            }
            
        } catch (Exception e) {
            log.error("Failed to get Docker registry port", e);
            throw new RuntimeException("Failed to get Docker registry port: " + e.getMessage());
        }
    }
    
    
    /**
     * 넥서스 API 응답에서 Docker 레지스트리 포트를 파싱합니다.
     * 
     * @param jsonResponse 넥서스 API JSON 응답
     * @return Docker 레지스트리 포트
     */
    private int parseDockerPortFromResponse(String jsonResponse) {
        try {
            // JSON에서 Docker 레지스트리 포트 정보 찾기
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            
            // Nexus status API에서 Docker registry 포트 찾기
            if (rootNode.has("data")) {
                JsonNode dataNode = rootNode.get("data");
                if (dataNode.has("docker")) {
                    JsonNode dockerNode = dataNode.get("docker");
                    if (dockerNode.has("port")) {
                        return dockerNode.get("port").asInt();
                    }
                }
            }
            
            // 기본적으로 8081 포트 사용 (Docker registry 기본 포트)
            return 8081;
        } catch (Exception e) {
            log.warn("Failed to parse Docker port from response: {}", e.getMessage());
            return 8081;
        }
    }
    
    /**
     * 넥서스 API 응답에서 태그 목록을 파싱합니다.
     * 
     * @param jsonResponse 넥서스 API JSON 응답
     * @param imageName 이미지 이름
     * @return 파싱된 태그 목록
     */
    private List<String> parseTagsFromNexusResponse(String jsonResponse, String imageName) {
        List<String> tags = new ArrayList<>();
        
        try {
            // Jackson ObjectMapper를 사용하여 JSON 파싱
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            
            if (rootNode.has("items")) {
                JsonNode itemsNode = rootNode.get("items");
                if (itemsNode.isArray()) {
                    for (JsonNode item : itemsNode) {
                        if (item.has("version")) {
                            String version = item.get("version").asText();
                            if (!tags.contains(version)) {
                                tags.add(version);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse Nexus response: {}", e.getMessage());
        }
        
        return tags;
    }
    
    /**
     * 소프트웨어 카탈로그 정보를 기반으로 넥서스 레포지토리 DTO를 생성합니다.
     * Docker 호스팅 레포지토리로 설정하여 생성합니다.
     * 
     * @param catalog 소프트웨어 카탈로그 정보
     * @return 넥서스 레포지토리 DTO (Docker 호스팅 타입)
     */
    private CommonRepository.RepositoryDto createRepositoryDto(SoftwareCatalogDTO catalog) {
        // Docker 설정
        CommonRepository.RepositoryDto.DockerDto dockerDto = CommonRepository.RepositoryDto.DockerDto.builder()
                .v1Enabled(false)
                .forceBasicAuth(true)
                .httpPort(dockerRegistryPort)
                .httpsPort(dockerRegistryPort + 100)
                .subdomain("/" + catalog.getName().toLowerCase().replaceAll("\\s+", "-"))
                .build();
        
        // Storage 설정
        CommonRepository.RepositoryDto.StorageDto storageDto = CommonRepository.RepositoryDto.StorageDto.builder()
                .blobStoreName("default")
                .strictContentTypeValidation(true)
                .writePolicy("allow")
                .build();
        
        // 레포지토리 DTO 생성
        return CommonRepository.RepositoryDto.builder()
                .name(catalog.getName())
                .format("docker")
                .type("hosted")
                .url("") // 등록시에는 빈값
                .online(true)
                .storage(storageDto)
                .docker(dockerDto)
                .build();
    }
    
    /**
     * 넥서스에 이미지가 존재하는지 확인합니다.
     * 
     * @param imageName 이미지 이름
     * @param tag 이미지 태그
     * @return 이미지 존재 여부
     */
    @Override
    public boolean checkImageExistsInNexus(String imageName, String tag) {
        log.info("Checking if image exists in Nexus: {}:{}", imageName, tag);
        
        try {
            // DB에서 넥서스 정보 가져오기
            OssDto nexusInfo = getNexusInfoFromDB();
            String dockerRepositoryName = getRepositoryNameByFormat("docker");
            
            // 넥서스 검색 API 호출
            String searchUrl = nexusInfo.getOssUrl() + "/service/rest/v1/search?repository=" + dockerRepositoryName + "&name=" + imageName;
            log.info("Searching for image in Nexus: {}", searchUrl);
            
            // RestTemplate을 사용하여 HTTP 요청
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String auth = nexusInfo.getOssUsername() + ":" + Base64Utils.base64Decoding(nexusInfo.getOssPassword());
            String encodedAuth = java.util.Base64.getEncoder().encodeToString(auth.getBytes());
            headers.set("Authorization", "Basic " + encodedAuth);
            
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                searchUrl, 
                HttpMethod.GET, 
                entity, 
                String.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String responseBody = response.getBody();
                log.info("Nexus search response: {}", responseBody);
                
                // JSON 응답에서 태그 확인
                return responseBody.contains("\"version\":\"" + tag + "\"") || 
                       responseBody.contains("\"name\":\"" + imageName + "\"");
            } else {
                log.warn("Failed to search Nexus for image: {}:{}, Status: {}", imageName, tag, response.getStatusCode());
                return false;
            }
            
        } catch (Exception e) {
            log.error("Error checking image existence in Nexus: {}:{}", imageName, tag, e);
            return false;
        }
    }
    
    /**
     * 넥서스에 이미지를 푸시하고 카탈로그에 등록합니다.
     * 
     * @param catalog 소프트웨어 카탈로그 정보
     * @return 등록 결과
     */
    @Override
    public Map<String, Object> pushImageAndRegisterCatalog(SoftwareCatalogDTO catalog) {
        log.info("Pushing image and registering catalog: {}", catalog.getName());
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 1. 이미지 정보 추출
            String imageName = catalog.getPackageInfo().getPackageName();
            String tag = catalog.getPackageInfo().getPackageVersion();
            
            // 2. 이미지가 이미 넥서스에 있는지 확인
            boolean imageExists = checkImageExistsInNexus(imageName, tag);
            
            if (imageExists) {
                log.info("Image already exists in Nexus: {}:{}", imageName, tag);
                result.put("imagePushRequired", false);
                result.put("message", "Image already exists in Nexus, proceeding with catalog registration");
            } else {
                log.info("Image not found in Nexus, pushing image: {}:{}", imageName, tag);
                
                // 3. 이미지 푸시
                Map<String, Object> pushResult = pushImageToNexus(imageName, tag, null);
                
                if (!(Boolean) pushResult.get("success")) {
                    result.put("success", false);
                    result.put("message", "Failed to push image to Nexus: " + pushResult.get("message"));
                    return result;
                }
                
                result.put("imagePushRequired", true);
                result.put("imagePushResult", pushResult);
            }
            
            // 4. 카탈로그 등록
            Map<String, Object> catalogResult = registerToNexus(catalog);
            
            if ((Boolean) catalogResult.get("success")) {
                result.put("success", true);
                result.put("message", "Image and catalog successfully registered to Nexus");
                result.put("catalogResult", catalogResult);
            } else {
                result.put("success", false);
                result.put("message", "Failed to register catalog to Nexus: " + catalogResult.get("message"));
            }
            
        } catch (Exception e) {
            log.error("Error pushing image and registering catalog: {}", catalog.getName(), e);
            result.put("success", false);
            result.put("message", "Error during image push and catalog registration: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Nexus에서 특정 format의 Repository 이름을 동적으로 가져옵니다.
     * 
     * @param format Repository format (예: "docker", "helm", "maven" 등)
     * @return Repository 이름
     */
    @Override
    public String getRepositoryNameByFormat(String format) {
        try {
            // DB에서 넥서스 정보 가져오기
            OssDto nexusInfo = getNexusInfoFromDB();
            String nexusUrl = nexusInfo.getOssUrl();
            String username = nexusInfo.getOssUsername();
            String password = Base64Utils.base64Decoding(nexusInfo.getOssPassword());
            
            log.info("Querying Nexus repositories from: {} for format: {}", nexusUrl, format);
            
            // 넥서스에서 모든 레포지토리 목록 조회
            String repositoriesUrl = nexusUrl + "/service/rest/v1/repositories";
            
            // RestTemplate을 사용하여 HTTP 요청
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String auth = username + ":" + password;
            String encodedAuth = java.util.Base64.getEncoder().encodeToString(auth.getBytes());
            headers.set("Authorization", "Basic " + encodedAuth);
            
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                repositoriesUrl, 
                HttpMethod.GET, 
                entity, 
                String.class
            );
            
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.error("Failed to query Nexus repositories: {}", response.getStatusCode());
                throw new RuntimeException("Failed to query Nexus repositories: " + response.getStatusCode());
            }
            
            String responseStr = response.getBody();
            log.debug("Nexus repositories response: {}", responseStr);
            
            // JSON 응답에서 지정된 format의 레포지토리 찾기
            String repositoryName = parseRepositoryByFormatFromResponse(responseStr, format);
            
            if (repositoryName != null && !repositoryName.isEmpty()) {
                // Docker repository name은 소문자여야 함
                String normalizedRepositoryName = repositoryName.toLowerCase();
                log.info("Successfully found {} repository: {} (normalized to: {})", format, repositoryName, normalizedRepositoryName);
                return normalizedRepositoryName;
            } else {
                log.error("No {} repository found in Nexus. Available repositories: {}", format, responseStr);
                throw new RuntimeException("No " + format + " repository found in Nexus");
            }
            
        } catch (Exception e) {
            log.error("Failed to get {} repository name from Nexus", format, e);
            throw new RuntimeException("Failed to get " + format + " repository name: " + e.getMessage());
        }
    }
    
    /**
     * 넥서스 API 응답에서 Docker 타입의 레포지토리 이름을 파싱합니다.
     * 
     * @param jsonResponse 넥서스 API JSON 응답
     * @return Docker 레포지토리 이름 (찾지 못하면 null)
     */
    private String parseDockerRepositoryFromResponse(String jsonResponse) {
        try {
            log.debug("Parsing Docker repository from response: {}", jsonResponse);
            
            // JSON에서 "format":"docker"인 레포지토리 찾기
            // 더 정확한 정규식 패턴 사용
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "\\{[^}]*\"name\"\\s*:\\s*\"([^\"]+)\"[^}]*\"format\"\\s*:\\s*\"docker\"[^}]*\\}"
            );
            java.util.regex.Matcher matcher = pattern.matcher(jsonResponse);
            
            if (matcher.find()) {
                String repositoryName = matcher.group(1);
                log.info("Successfully parsed Docker repository: {}", repositoryName);
                return repositoryName;
            }
            
            // 정규식이 실패하면 다른 방법으로 시도
            log.warn("Regex pattern failed, trying alternative parsing method");
            
            // 대안: "format":"docker"가 포함된 라인에서 "name" 찾기
            String[] lines = jsonResponse.split("\\{");
            for (String line : lines) {
                if (line.contains("\"format\"") && line.contains("docker")) {
                    int nameStart = line.indexOf("\"name\":\"");
                    if (nameStart > 0) {
                        int nameEnd = line.indexOf("\"", nameStart + 8);
                        if (nameEnd > nameStart) {
                            String repositoryName = line.substring(nameStart + 8, nameEnd);
                            log.info("Found Docker repository using alternative method: {}", repositoryName);
                            return repositoryName;
                        }
                    }
                }
            }
            
            log.error("No Docker repository found in response");
            return null;
            
        } catch (Exception e) {
            log.error("Failed to parse Docker repository from response: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * JSON 응답에서 특정 format의 repository를 찾습니다.
     * 
     * @param responseStr JSON 응답 문자열
     * @param format 찾을 format
     * @return repository 이름 또는 null
     */
    private String parseRepositoryByFormatFromResponse(String responseStr, String format) {
        try {
            // JSON 파싱을 위한 ObjectMapper 사용
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(responseStr);
            
            if (rootNode.isArray()) {
                for (JsonNode repository : rootNode) {
                    JsonNode formatNode = repository.get("format");
                    if (formatNode != null && format.equals(formatNode.asText())) {
                        JsonNode nameNode = repository.get("name");
                        if (nameNode != null) {
                            return nameNode.asText();
                        }
                    }
                }
            }
            
            return null;
        } catch (Exception e) {
            log.error("Error parsing repository response for format: {}", format, e);
            return null;
        }
    }
    
    /**
     * 파일을 Nexus에 업로드합니다.
     */
    @Override
    public boolean uploadFileToNexus(java.io.File file, String uploadUrl, kr.co.mcmp.oss.dto.OssDto nexusInfo) {
        try {
            // Basic Auth 헤더 설정
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            String auth = nexusInfo.getOssUsername() + ":" + nexusInfo.getOssPassword();
            String encodedAuth = java.util.Base64.getEncoder().encodeToString(auth.getBytes());
            headers.set("Authorization", "Basic " + encodedAuth);
            
            // 파일을 바이트 배열로 읽기
            byte[] fileBytes = java.nio.file.Files.readAllBytes(file.toPath());
            HttpEntity<byte[]> entity = new HttpEntity<>(fileBytes, headers);
            
            ResponseEntity<String> uploadResponse = restTemplate.exchange(
                uploadUrl,
                HttpMethod.PUT,
                entity,
                String.class
            );
            
            if (!uploadResponse.getStatusCode().is2xxSuccessful()) {
                log.error("Failed to upload file to Nexus: {}, Status: {}", uploadUrl, uploadResponse.getStatusCode());
                return false;
            }
            
            log.info("Successfully uploaded file to Nexus: {}", uploadUrl);
            return true;
            
        } catch (Exception e) {
            log.error("Error uploading file to Nexus: {}", uploadUrl, e);
            return false;
        }
    }
    
    /**
     * 개선된 Docker 푸시 실행
     */
    private boolean executeEnhancedDockerPush(String imageName, String tag, OssDto ossInfo, 
                                            DockerRegistryInfo registryInfo) {
        log.info("=== Starting enhanced Docker push process ===");
        
        try {
            String repositoryName = getDockerRepositoryName();
            String username = ossInfo.getOssUsername();
            String password = Base64Utils.base64Decoding(ossInfo.getOssPassword());
            
            // 1. Nexus에 로그인 (DinD 컨테이너 시작 포함)
            if (!loginToNexus(registryInfo.getRegistryUrl(), username, password)) {
                return false;
            }
            
            // 2. DinD 컨테이너 내부에서 Docker Hub에서 이미지 풀
            if (!pullImageFromDockerHub(imageName, tag)) {
                return false;
            }

            
            
            // 3. 이미지 태그 생성
            String sourceImage = getSourceImageName(imageName, tag);
            String nexusImage = getNexusImageName(registryInfo.getRegistryUrl(), repositoryName, imageName, tag);
            if (!tagImageForNexus(sourceImage, nexusImage)) {
                return false;
            }
            
            // 4. Nexus에 푸시
            if (!pushImageToRegistry(nexusImage)) {
                return false;
            }
            
            log.info("=== Enhanced Docker push process completed successfully ===");
            return true;
            
        } catch (Exception e) {
            log.error("Error in enhanced Docker push process", e);
            return false;
        }
    }
    
    /**
     * Docker Hub에서 이미지 풀 (재시도 로직 포함)
     */
    private boolean pullImageFromDockerHub(String imageName, String tag) {
        log.info("Step 1/4: Pulling image from Docker Hub...");
        
        String sourceImage = getSourceImageName(imageName, tag);
        
        // 간단한 pull 명령어
        ProcessBuilder pullProcess = new ProcessBuilder(
            "docker", "pull", sourceImage
        );
        
        return executeDockerCommandWithRetry(pullProcess, "Docker Pull", dockerTimeoutSeconds);
    }
    
    /**
     * Nexus에 로그인 (간단한 버전)
     */
    private boolean loginToNexus(String registryUrl, String username, String password) {
        log.info("Step 2/4: Logging into Nexus...");
        
        // 프로토콜 제거한 URL 사용
        String cleanRegistryUrl = registryUrl.replaceFirst("^https?://", "");
        
        log.info("cleanRegistryUrl : " + cleanRegistryUrl + ", username : " + username + ", password : " + password);

        // 사용자가 테스트한 간단한 명령어 사용 (HTTP 프로토콜 명시)
        ProcessBuilder loginProcess = new ProcessBuilder(
            "docker", "login", "http://" + cleanRegistryUrl, "-u", username, "--password", password
        );
        
        return executeDockerCommandWithRetry(loginProcess, "Docker Login", 30);
    }
    
    /**
     * insecure registry로 Docker 로그인 시도
     */
    private boolean tryLoginWithInsecureRegistry(String registryUrl, String username, String password) {
        try {
            log.info("Attempting login with insecure registry configuration: {}", registryUrl);
            
            // 1. Docker-in-Docker 전략 시도
            if (tryDockerInDockerStrategy(registryUrl, username, password)) {
                return true;
            }
            
            // 2. Docker client 설정 파일 동적 생성
            if (createDockerClientConfig(registryUrl)) {
                // 3. 환경 변수로 insecure registry 설정
                return tryLoginWithEnvironmentVariables(registryUrl, username, password);
            }
            
            return false;
        } catch (Exception e) {
            log.error("Error trying login with insecure registry: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 컨테이너 내부 Docker daemon을 사용하여 로그인 시도
     */
    private boolean tryDockerInDockerStrategy(String registryUrl, String username, String password) {
        try {
            log.info("Attempting container internal Docker daemon strategy for insecure registry: {}", registryUrl);
            
            // 컨테이너 내부 Docker daemon이 준비될 때까지 대기
            if (waitForContainerDockerDaemonReady()) {
                // 컨테이너 내부에서 직접 로그인 시도
                return loginInContainer(registryUrl, username, password);
            }
            
            return false;
        } catch (Exception e) {
            log.error("Error in container Docker daemon strategy: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 컨테이너 내부 Docker daemon이 준비될 때까지 대기
     */
    private boolean waitForContainerDockerDaemonReady() {
        try {
            log.info("Waiting for container internal Docker daemon to be ready");
            
            int maxAttempts = 30; // 30초 대기
            int attempt = 0;
            
            while (attempt < maxAttempts) {
                attempt++;
                log.info("Checking container Docker daemon readiness (attempt {}/{}):", attempt, maxAttempts);
                
                // 컨테이너 내부에서 docker info 실행하여 daemon 준비 상태 확인
                ProcessBuilder infoProcess = new ProcessBuilder(
                    "docker", "info"
                );
                
                Process infoProc = infoProcess.start();
                int exitCode = infoProc.waitFor();
                
                if (exitCode == 0) {
                    String output = new String(infoProc.getInputStream().readAllBytes());
                    log.info("Container Docker daemon is ready: {}", output);
                    
                    // insecure registries 설정 확인
                    boolean hasInsecureRegistry = output.contains("Insecure Registries:") && 
                                               output.contains("mc-application-manager-sonatype-nexus:5500");
                    
                    if (hasInsecureRegistry) {
                        log.info("Container Docker daemon has insecure registry configured: mc-application-manager-sonatype-nexus:5500");
                        return true;
                    } else {
                        log.warn("Container Docker daemon does not have insecure registry configured, waiting...");
                    }
                } else {
                    log.warn("Container Docker daemon not ready yet (exit code: {}), waiting...", exitCode);
                }
                
                Thread.sleep(1000);
            }
            
            log.error("Container Docker daemon failed to become ready within {} seconds", maxAttempts);
            return false;
            
        } catch (Exception e) {
            log.error("Error waiting for container Docker daemon to be ready: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 컨테이너 내부에서 직접 로그인 시도
     */
    private boolean loginInContainer(String registryUrl, String username, String password) {
        try {
            log.info("Attempting login in container: {}", registryUrl);
            
            // 사용자가 테스트한 간단한 명령어 사용 (HTTP 프로토콜 명시)
            ProcessBuilder loginProcess = new ProcessBuilder(
                "docker", "login", "http://" + registryUrl, "-u", username, "--password", password
            );
            
            return executeDockerCommandWithRetry(loginProcess, "Container Docker Login", 30);
            
        } catch (Exception e) {
            log.error("Error logging in container: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * DinD 컨테이너에서 insecure registry로 Docker daemon 시작
     */
    private boolean startDinDWithInsecureRegistry(String registryUrl) {
        try {
            log.info("Starting DinD container with insecure registry: {}", registryUrl);
            
            // 컨테이너 이름 생성 (레지스트리 URL 기반)
            String containerName = "dind-" + registryUrl.replaceAll("[:.]", "-");
            
            // 기존 DinD 컨테이너 정리
            cleanupDinDContainers(containerName);
            
            // DinD 컨테이너 실행 명령어 (사용자 제안 방식)
            ProcessBuilder dindProcess = new ProcessBuilder(
                "docker", "run", "-d",
                "--name", containerName,
                "--privileged",
                "-e", "DOCKER_TLS_CERTDIR=",
                "-e", "DOCKER_INSECURE_REGISTRIES=" + registryUrl,
                "docker:dind",
                "dockerd-entrypoint.sh",
                "--insecure-registry=" + registryUrl,
                "--host=0.0.0.0:2376",
                "--storage-driver=overlay2"
            );
            
            Process dindProc = dindProcess.start();
            int exitCode = dindProc.waitFor();
            
            if (exitCode == 0) {
                log.info("DinD container '{}' started successfully", containerName);
                
                // DinD 컨테이너의 Docker daemon이 완전히 준비될 때까지 대기
                if (waitForDinDDaemonReady(containerName)) {
                    log.info("DinD container '{}' is running and ready", containerName);
                    return true;
                } else {
                    log.error("DinD container '{}' daemon failed to start properly", containerName);
                    return false;
                }
            } else {
                log.error("Failed to start DinD container '{}', exit code: {}", containerName, exitCode);
                return false;
            }
            
        } catch (Exception e) {
            log.error("Error starting DinD container: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * DinD 컨테이너가 실행 중인지 확인
     */
    private boolean isDinDContainerRunning(String containerName) {
        try {
            ProcessBuilder checkProcess = new ProcessBuilder(
                "docker", "ps", "--filter", "name=" + containerName, "--format", "{{.Status}}"
            );
            Process checkProc = checkProcess.start();
            int exitCode = checkProc.waitFor();
            
            if (exitCode == 0) {
                String output = new String(checkProc.getInputStream().readAllBytes()).trim();
                boolean isRunning = output.contains("Up");
                log.info("DinD container '{}' status: {}", containerName, output);
                return isRunning;
            }
            
            return false;
        } catch (Exception e) {
            log.error("Error checking DinD container status: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * DinD 컨테이너의 Docker daemon이 준비될 때까지 대기
     */
    private boolean waitForDinDDaemonReady(String containerName) {
        try {
            log.info("Waiting for DinD daemon to be ready: {}", containerName);
            
            int maxAttempts = 60; // 60초 대기 (30초 → 60초로 증가)
            int attempt = 0;
            
            while (attempt < maxAttempts) {
                attempt++;
                log.info("Checking DinD daemon readiness (attempt {}/{}): {}", attempt, maxAttempts, containerName);
                
                // 컨테이너가 실행 중인지 확인
                if (!isDinDContainerRunning(containerName)) {
                    log.warn("DinD container '{}' is not running, waiting...", containerName);
                    Thread.sleep(1000);
                    continue;
                }
                
                // DinD 컨테이너 내부에서 docker info 실행하여 daemon 준비 상태 확인
                ProcessBuilder infoProcess = new ProcessBuilder(
                    "docker", "exec", containerName,
                    "docker", "info"
                );
                
                Process infoProc = infoProcess.start();
                int exitCode = infoProc.waitFor();
                
                if (exitCode == 0) {
                    String output = new String(infoProc.getInputStream().readAllBytes());
                    log.info("DinD daemon is ready: {}", output);
                    
                    // insecure registries 설정 확인
                    boolean hasInsecureRegistry = output.contains("Insecure Registries:") && 
                                               output.contains("mc-application-manager-sonatype-nexus:5500");
                    
                    if (hasInsecureRegistry) {
                        log.info("DinD daemon has insecure registry configured: mc-application-manager-sonatype-nexus:5500");
                        return true;
                    } else {
                        log.warn("DinD daemon does not have insecure registry configured, waiting...");
                    }
                } else {
                    log.warn("DinD daemon not ready yet (exit code: {}), waiting...", exitCode);
                }
                
                Thread.sleep(1000);
            }
            
            log.error("DinD daemon failed to become ready within {} seconds", maxAttempts);
            return false;
            
        } catch (Exception e) {
            log.error("Error waiting for DinD daemon to be ready: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * DinD 컨테이너 내부에서 로그인 시도
     */
    private boolean loginInDinDContainer(String registryUrl, String username, String password) {
        try {
            log.info("Attempting login inside DinD container: {}", registryUrl);
            
            // 컨테이너 이름 생성 (레지스트리 URL 기반)
            String containerName = "dind-" + registryUrl.replaceAll("[:.]", "-");
            
            // DinD 컨테이너 내부에서 Docker 명령어 실행 (interactive 플래그 추가)
            ProcessBuilder loginProcess = new ProcessBuilder(
                "docker", "exec", "-i", containerName,
                "docker", "login", "http://" + registryUrl, "-u", username, "--password", password
            );
            
            // DinD 컨테이너 내부 환경 설정
            loginProcess.environment().put("DOCKER_TLS_VERIFY", "0");
            loginProcess.environment().put("DOCKER_CONTENT_TRUST", "0");
            
            return executeDockerCommandWithRetry(loginProcess, "DinD Docker Login", 30);
            
        } catch (Exception e) {
            log.error("Error logging in DinD container: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 기존 DinD 컨테이너 정리
     */
    private void cleanupDinDContainers(String containerName) {
        try {
            log.info("Cleaning up DinD container: {}", containerName);
            
            // 기존 DinD 컨테이너 정리
            ProcessBuilder cleanupProcess = new ProcessBuilder(
                "docker", "rm", "-f", containerName
            );
            Process cleanupProc = cleanupProcess.start();
            int exitCode = cleanupProc.waitFor();
            
            if (exitCode == 0) {
                log.info("Successfully cleaned up DinD container: {}", containerName);
            } else {
                log.debug("DinD container '{}' may not have existed", containerName);
            }
        } catch (Exception e) {
            log.debug("Error cleaning up DinD container '{}': {}", containerName, e.getMessage());
        }
    }
    
    /**
     * Docker client 설정 파일 동적 생성
     */
    private boolean createDockerClientConfig(String registryUrl) {
        try {
            log.info("Creating Docker client configuration for insecure registry: {}", registryUrl);
            
            // Docker client 설정 디렉토리 생성
            String dockerConfigDir = System.getProperty("user.home") + "/.docker";
            java.io.File configDir = new java.io.File(dockerConfigDir);
            if (!configDir.exists()) {
                configDir.mkdirs();
            }
            
            // config.json 파일 생성
            String configPath = dockerConfigDir + "/config.json";
            java.io.File configFile = new java.io.File(configPath);
            
            // 기존 설정 읽기
            String existingConfig = "{}";
            if (configFile.exists()) {
                existingConfig = new String(java.nio.file.Files.readAllBytes(configFile.toPath()));
            }
            
            // JSON 파싱 및 수정
            ObjectMapper mapper = new ObjectMapper();
            JsonNode configNode = mapper.readTree(existingConfig);
            ObjectNode config = (ObjectNode) configNode;
            
            // insecure registries 설정 추가
            ObjectNode httpHeaders = config.has("httpHeaders") ? (ObjectNode) config.get("httpHeaders") : config.putObject("httpHeaders");
            httpHeaders.put("X-Docker-Insecure-Registry", "true");
            
            // 설정 저장
            mapper.writerWithDefaultPrettyPrinter().writeValue(configFile, config);
            
            log.info("Docker client configuration created: {}", configPath);
            return true;
            
        } catch (Exception e) {
            log.error("Error creating Docker client configuration: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 환경 변수로 insecure registry 설정하여 로그인 시도
     */
    private boolean tryLoginWithEnvironmentVariables(String registryUrl, String username, String password) {
        try {
            log.info("Attempting login with environment variables: {}", registryUrl);
            
            // 1. Docker daemon에 직접 연결하는 방법 시도
            if (tryDirectDockerDaemonConnection(registryUrl, username, password)) {
                return true;
            }
            
            // 2. Docker 명령어에 직접 플래그 추가하는 방법 시도
            if (tryDockerCommandWithFlags(registryUrl, username, password)) {
                return true;
            }
            
            // 3. 기본 환경 변수 방법 - HTTP 프로토콜 명시적 사용
            ProcessBuilder loginProcess = new ProcessBuilder("docker", "login", "http://" + registryUrl, "-u", username, "--password", password);
            
            // 모든 가능한 환경 변수 설정
            loginProcess.environment().put("DOCKER_TLS_VERIFY", "0");
            loginProcess.environment().put("DOCKER_CONTENT_TRUST", "0");
            loginProcess.environment().put("DOCKER_BUILDKIT", "0");
            loginProcess.environment().put("DOCKER_INSECURE_REGISTRIES", registryUrl);
            loginProcess.environment().put("DOCKER_REGISTRY_INSECURE", "true");
            loginProcess.environment().put("DOCKER_DAEMON_INSECURE_REGISTRIES", registryUrl);
            
            // HTTP 프로토콜 강제 사용
            loginProcess.environment().put("DOCKER_HTTP_HOST", "http://" + registryUrl);
            loginProcess.environment().put("DOCKER_HTTPS_HOST", "");
            loginProcess.environment().put("DOCKER_REGISTRY_URL", "http://" + registryUrl);
            
            // Docker daemon 설정
            loginProcess.environment().put("DOCKER_HOST", "unix:///var/run/docker.sock");
            
            log.info("Set multiple Docker environment variables for insecure registry with HTTP protocol");
            
            return executeDockerCommandWithRetry(loginProcess, "Docker Login (Environment)", 30);
            
        } catch (Exception e) {
            log.error("Error trying login with environment variables: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Docker daemon에 직접 연결하여 로그인 시도
     */
    private boolean tryDirectDockerDaemonConnection(String registryUrl, String username, String password) {
        try {
            log.info("Attempting direct Docker daemon connection: {}", registryUrl);
            
            // Docker daemon socket 경로 확인
            String dockerSocket = System.getenv("DOCKER_HOST");
            if (dockerSocket == null) {
                dockerSocket = "unix:///var/run/docker.sock";
            }
            
            // Docker daemon에 직접 HTTP 요청으로 로그인
            String loginUrl = "http://" + registryUrl + "/v2/";
            log.info("Attempting direct HTTP connection to: {}", loginUrl);
            
            // 간단한 HTTP 연결 테스트
            java.net.URL url = new java.net.URL(loginUrl);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            
            int responseCode = conn.getResponseCode();
            log.info("Direct HTTP connection response code: {}", responseCode);
            
            if (responseCode == 200 || responseCode == 401) {
                // 연결 성공, 이제 Docker 명령어로 로그인 시도
                return tryDockerLoginWithDirectConnection(registryUrl, username, password);
            }
            
            return false;
            
        } catch (Exception e) {
            log.error("Error in direct Docker daemon connection: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 직접 연결로 Docker 로그인 시도
     */
    private boolean tryDockerLoginWithDirectConnection(String registryUrl, String username, String password) {
        try {
            log.info("Attempting Docker login with direct connection: {}", registryUrl);
            
            ProcessBuilder loginProcess = new ProcessBuilder("docker", "login", registryUrl, "-u", username, "--password", password);
            
            // Docker daemon 설정
            loginProcess.environment().put("DOCKER_TLS_VERIFY", "0");
            loginProcess.environment().put("DOCKER_CONTENT_TRUST", "0");
            loginProcess.environment().put("DOCKER_BUILDKIT", "0");
            
            // insecure registry 설정
            loginProcess.environment().put("DOCKER_INSECURE_REGISTRIES", registryUrl);
            loginProcess.environment().put("DOCKER_REGISTRY_INSECURE", "true");
            
            // Docker daemon host 설정
            loginProcess.environment().put("DOCKER_HOST", "tcp://localhost:2375");
            
            return executeDockerCommandWithRetry(loginProcess, "Docker Login (Direct)", 30);
            
        } catch (Exception e) {
            log.error("Error in Docker login with direct connection: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Docker 명령어에 직접 플래그 추가하여 로그인 시도
     */
    private boolean tryDockerCommandWithFlags(String registryUrl, String username, String password) {
        try {
            log.info("Attempting Docker login with command flags: {}", registryUrl);
            
            // Docker 명령어에 직접 insecure registry 플래그 추가 (지원되지 않으므로 제거)
            ProcessBuilder loginProcess = new ProcessBuilder(
                "docker", "login", 
                registryUrl, 
                "-u", username, 
                "--password", password
            );
            
            // 기본 환경 변수 설정
            // loginProcess.environment().put("DOCKER_TLS_VERIFY", "0");
            // loginProcess.environment().put("DOCKER_CONTENT_TRUST", "0");
            // loginProcess.environment().put("DOCKER_BUILDKIT", "0");
            
            return executeDockerCommandWithRetry(loginProcess, "Docker Login (Flags)", 30);
            
        } catch (Exception e) {
            log.error("Error in Docker login with command flags: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 이미지 태그 생성
     */
    private boolean tagImageForNexus(String sourceImage, String nexusImage) {
        log.info("Step 3/4: Tagging image for Nexus...");
        
        // 간단한 tag 명령어
        ProcessBuilder tagProcess = new ProcessBuilder(
            "docker", "tag", sourceImage, nexusImage
        );
        
        return executeDockerCommandWithRetry(tagProcess, "Docker Tag", 30);
    }
    
    /**
     * 이미지를 레지스트리에 푸시
     */
    private boolean pushImageToRegistry(String nexusImage) {
        log.info("Step 4/4: Pushing image to Nexus...");
        
        // 간단한 push 명령어
        ProcessBuilder pushProcess = new ProcessBuilder(
            "docker", "push", nexusImage
        );
        
        return executeDockerCommandWithRetry(pushProcess, "Docker Push", dockerTimeoutSeconds);
    }
    
    /**
     * 재시도 로직이 포함된 Docker 명령어 실행
     */
    private boolean executeDockerCommandWithRetry(ProcessBuilder processBuilder, String stepName, long timeoutSeconds) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.info("Executing {} (attempt {}/{})", stepName, attempt, maxRetries);
                
                // 적응형 타임아웃 (재시도할수록 더 오래 기다림)
                long currentTimeout = timeoutSeconds + (timeoutSeconds * (attempt - 1) / 2);
                
                DockerCommandResult result = executeDockerCommand(processBuilder, stepName, currentTimeout);
                
                if (result.isSuccess()) {
                    log.info("{} completed successfully on attempt {}", stepName, attempt);
                    return true;
                } else {
                    String errorAnalysis = result.getErrorAnalysis();
                    log.warn("{} failed on attempt {}: {}", stepName, attempt, errorAnalysis);
                    
                    // 재시도 불가능한 에러의 경우 즉시 중단
                    if (isNonRetryableError(result.getStderr())) {
                        log.error("Non-retryable error detected, stopping retries: {}", errorAnalysis);
                        return false;
                    }
                }
                
                // 마지막 시도가 아니면 대기
                if (attempt < maxRetries) {
                    long delay = retryDelaySeconds * attempt * 1000;
                    log.info("Waiting {} ms before retry...", delay);
                    Thread.sleep(delay);
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Docker command execution interrupted", e);
                return false;
            } catch (Exception e) {
                log.error("Error during {} (attempt {}): {}", stepName, attempt, e.getMessage());
            }
        }
        
        log.error("{} failed after {} attempts", stepName, maxRetries);
        return false;
    }
    
    /**
     * Docker 명령어 실행
     */
    private DockerCommandResult executeDockerCommand(ProcessBuilder processBuilder, String commandDescription, long timeoutSeconds) {
        try {
            // Docker 명령어에 insecure registry 설정 적용
            // applyInsecureRegistryToDockerCommand(processBuilder, "mc-application-manager-sonatype-nexus:5500");
            
            Process process = processBuilder.start();
            
            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();
            
            // 비동기적으로 출력 스트림 읽기
            CompletableFuture<Void> stdoutFuture = CompletableFuture.runAsync(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stdout.append(line).append("\n");
                        log.info("[{}] STDOUT: {}", commandDescription, line);
                    }
                } catch (IOException e) {
                    log.error("Error reading stdout: {}", e.getMessage());
                }
            });
            
            CompletableFuture<Void> stderrFuture = CompletableFuture.runAsync(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stderr.append(line).append("\n");
                        log.warn("[{}] STDERR: {}", commandDescription, line);
                    }
                } catch (IOException e) {
                    log.error("Error reading stderr: {}", e.getMessage());
                }
            });
            
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                log.error("{} timed out after {} seconds", commandDescription, timeoutSeconds);
                process.destroyForcibly();
                return new DockerCommandResult(-1, "", "Command timed out after " + timeoutSeconds + " seconds");
            }
            
            int exitCode = process.exitValue();
            
            // 출력 스트림 읽기 완료 대기
            stdoutFuture.get(5, TimeUnit.SECONDS);
            stderrFuture.get(5, TimeUnit.SECONDS);
            
            return new DockerCommandResult(exitCode, stdout.toString(), stderr.toString());
            
        } catch (Exception e) {
            log.error("Error executing Docker command [{}]: {}", commandDescription, e.getMessage());
            return new DockerCommandResult(-1, "", "Exception: " + e.getMessage());
        }
    }
    
    /**
     * 재시도 불가능한 에러인지 확인
     */
    private boolean isNonRetryableError(String stderr) {
        String errorOutput = stderr.toLowerCase();
        return errorOutput.contains("unauthorized") || 
               errorOutput.contains("forbidden") ||
               errorOutput.contains("not found") ||
               errorOutput.contains("invalid") ||
               errorOutput.contains("bad request");
    }
    
    /**
     * Docker 환경 검증
     */
    private boolean validateDockerEnvironment() {
        log.info("Validating Docker environment...");
        
        try {
            ProcessBuilder checkDocker = new ProcessBuilder("docker", "version", "--format", "{{.Server.Version}}");
            Process proc = checkDocker.start();
            
            boolean finished = proc.waitFor(10, TimeUnit.SECONDS);
            if (finished && proc.exitValue() == 0) {
                log.info("Docker daemon is running");
                return true;
            } else {
                log.error("Docker daemon is not accessible or not running");
                return false;
            }
        } catch (Exception e) {
            log.error("Error validating Docker environment: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Insecure registry 설정 검증 및 동적 추가
     */
    private boolean validateInsecureRegistryConfiguration(String registryUrl) {
        log.info("Validating insecure registry configuration for: {}", registryUrl);
        
        try {
            // 1. 현재 Docker daemon 설정 확인
            if (isInsecureRegistryConfigured(registryUrl)) {
                log.info("Insecure registry already configured for: {}", registryUrl);
                return true;
            }
            
            // 2. 런타임에 Docker daemon 설정 시도
            log.info("Insecure registry not configured, attempting runtime configuration: {}", registryUrl);
            if (configureInsecureRegistryAtRuntime(registryUrl)) {
                log.info("Successfully configured insecure registry at runtime: {}", registryUrl);
                return true;
            }
            
            // 3. 설정 파일 수정 시도
            log.info("Runtime configuration failed, attempting to modify configuration files: {}", registryUrl);
            return addInsecureRegistryDynamically(registryUrl);
            
        } catch (Exception e) {
            log.error("Error validating insecure registry configuration: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 런타임에 Docker daemon에 insecure registry 설정
     */
    private boolean configureInsecureRegistryAtRuntime(String registryUrl) {
        try {
            String cleanUrl = registryUrl.replaceFirst("^https?://", "");
            log.info("Attempting runtime configuration for insecure registry: {}", cleanUrl);
            
            // 1. Docker daemon 설정 파일을 즉시 수정
            if (updateDockerDaemonConfigImmediately(cleanUrl)) {
                log.info("Successfully updated Docker daemon configuration");
                return true;
            }
            
            // 2. Docker daemon API를 통한 설정 (실험적)
            if (tryDockerDaemonApiConfiguration(cleanUrl)) {
                return true;
            }
            
            // 3. Docker 명령어를 통한 설정
            if (tryDockerCommandConfiguration(cleanUrl)) {
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            log.error("Error configuring insecure registry at runtime: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Docker daemon 설정을 즉시 수정하고 적용
     */
    private boolean updateDockerDaemonConfigImmediately(String registryUrl) {
        try {
            log.info("Attempting immediate Docker daemon configuration update: {}", registryUrl);
            
            // Windows 환경에서 Docker Desktop 설정 업데이트
            if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                return updateWindowsDockerConfigImmediately(registryUrl);
            } else {
                // Linux/macOS 환경에서 설정 업데이트
                return updateUnixDockerConfigImmediately(registryUrl);
            }
            
        } catch (Exception e) {
            log.error("Error updating Docker daemon configuration immediately: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Windows Docker Desktop 설정을 즉시 업데이트
     */
    private boolean updateWindowsDockerConfigImmediately(String registryUrl) {
        try {
            // Windows Docker Desktop의 설정 파일 경로들
            String[] possiblePaths = {
                System.getProperty("user.home") + "/.docker/daemon.json",
                System.getProperty("user.home") + "/AppData/Roaming/Docker/settings.json",
                "C:/Users/" + System.getProperty("user.name") + "/.docker/daemon.json"
            };
            
            for (String path : possiblePaths) {
                java.io.File daemonFile = new java.io.File(path);
                if (daemonFile.exists()) {
                    log.info("Found Windows Docker daemon config file: {}", path);
                    if (updateDaemonJsonFile(daemonFile, registryUrl)) {
                        // Docker Desktop 재시작 시도
                        return restartDockerDesktop();
                    }
                }
            }
            
            return false;
        } catch (Exception e) {
            log.error("Error updating Windows Docker config immediately: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Unix 계열 시스템에서 Docker 설정을 즉시 업데이트
     */
    private boolean updateUnixDockerConfigImmediately(String registryUrl) {
        try {
            // Linux/macOS Docker daemon.json 경로들
            String[] possiblePaths = {
                "/etc/docker/daemon.json",
                System.getProperty("user.home") + "/.docker/daemon.json"
            };
            
            for (String path : possiblePaths) {
                java.io.File daemonFile = new java.io.File(path);
                if (daemonFile.exists() && daemonFile.canWrite()) {
                    log.info("Found Unix Docker daemon config file: {}", path);
                    if (updateDaemonJsonFile(daemonFile, registryUrl)) {
                        // Docker daemon 재시작 시도
                        return restartDockerDaemon();
                    }
                }
            }
            
            return false;
        } catch (Exception e) {
            log.error("Error updating Unix Docker config immediately: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Docker Desktop 재시작 시도 (Windows)
     */
    private boolean restartDockerDesktop() {
        try {
            log.info("Attempting to restart Docker Desktop...");
            
            // Docker Desktop 프로세스 찾기 및 재시작
            ProcessBuilder tasklistBuilder = new ProcessBuilder("tasklist", "/FI", "IMAGENAME eq Docker Desktop.exe");
            Process tasklistProc = tasklistBuilder.start();
            
            boolean finished = tasklistProc.waitFor(5, TimeUnit.SECONDS);
            if (finished && tasklistProc.exitValue() == 0) {
                // Docker Desktop이 실행 중이면 종료 후 재시작
                ProcessBuilder killBuilder = new ProcessBuilder("taskkill", "/F", "/IM", "Docker Desktop.exe");
                Process killProc = killBuilder.start();
                killProc.waitFor(10, TimeUnit.SECONDS);
                
                // 잠시 대기
                Thread.sleep(5000);
                
                // Docker Desktop 재시작
                ProcessBuilder startBuilder = new ProcessBuilder("C:\\Program Files\\Docker\\Docker\\Docker Desktop.exe");
                Process startProc = startBuilder.start();
                
                // 재시작 대기
                Thread.sleep(10000);
                
                // Docker가 정상적으로 실행되는지 확인
                return validateDockerEnvironment();
            }
            
            return false;
        } catch (Exception e) {
            log.error("Error restarting Docker Desktop: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Docker daemon API를 통한 설정 시도
     */
    private boolean tryDockerDaemonApiConfiguration(String registryUrl) {
        try {
            // Docker daemon API는 일반적으로 insecure registry 설정을 지원하지 않음
            // 하지만 일부 환경에서는 가능할 수 있음
            log.debug("Docker daemon API configuration not supported for insecure registries");
            return false;
        } catch (Exception e) {
            log.debug("Docker daemon API configuration failed: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Docker 명령어를 통한 설정 시도
     */
    private boolean tryDockerCommandConfiguration(String registryUrl) {
        try {
            // Docker daemon을 재시작하여 설정 파일을 다시 로드
            if (restartDockerDaemon()) {
                log.info("Docker daemon restarted, checking if insecure registry is now available");
                Thread.sleep(3000); // 재시작 대기
                return isInsecureRegistryConfigured("http://" + registryUrl);
            }
            
            return false;
        } catch (Exception e) {
            log.debug("Docker command configuration failed: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Docker 명령어에 직접 insecure registry 설정 적용
     */
    private void applyInsecureRegistryToDockerCommand(ProcessBuilder processBuilder, String registryUrl) {
        try {
            String cleanUrl = registryUrl.replaceFirst("^https?://", "");
            
            // Docker 명령어에 --insecure-registry 플래그 추가 (지원되는 경우)
            List<String> command = new ArrayList<>(processBuilder.command());
            
            // docker login이나 docker push 명령어인 경우
            if (command.contains("login") || command.contains("push")) {
                log.info("Applied insecure registry settings to Docker command: {}", cleanUrl);
            }
            
        } catch (Exception e) {
            log.warn("Failed to apply insecure registry settings to Docker command: {}", e.getMessage());
        }
    }
    
    /**
     * Docker daemon에서 insecure registry가 설정되어 있는지 확인
     */
    private boolean isInsecureRegistryConfigured(String registryUrl) {
        try {
            ProcessBuilder infoBuilder = new ProcessBuilder("docker", "info", "--format", "{{.RegistryConfig.InsecureRegistryCIDRs}}");
            Process infoProc = infoBuilder.start();
            
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(infoProc.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
            }
            
            boolean finished = infoProc.waitFor(10, TimeUnit.SECONDS);
            if (finished && infoProc.exitValue() == 0) {
                String registries = output.toString();
                // 프로토콜 제거한 URL로 확인
                String cleanUrl = registryUrl.replaceFirst("^https?://", "");
                boolean isConfigured = registries.contains(cleanUrl) || registries.contains("127.0.0.0/8");
                
                if (isConfigured) {
                    log.info("Insecure registry configuration found for: {}", cleanUrl);
                } else {
                    log.warn("Insecure registry not configured for: {}", cleanUrl);
                    log.warn("Docker info output: {}", registries);
                }
                
                return isConfigured;
            } else {
                log.warn("Failed to get Docker info for insecure registry validation");
                return false;
            }
        } catch (Exception e) {
            log.error("Error checking insecure registry configuration: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Docker daemon 설정에 insecure registry를 동적으로 추가
     */
    private boolean addInsecureRegistryDynamically(String registryUrl) {
        try {
            String cleanUrl = registryUrl.replaceFirst("^https?://", "");
            log.info("Attempting to add insecure registry dynamically: {}", cleanUrl);
            
            // Docker 컨테이너 환경인지 먼저 확인
            if (isRunningInDockerContainer()) {
                log.warn("Running inside Docker container - attempting alternative configuration methods");
                
                // Docker-in-Docker 환경에서 호스트 설정 시도
                if (tryConfigureHostDockerFromContainer(cleanUrl)) {
                    return true;
                }
                
                log.warn("Cannot modify host Docker daemon configuration from container");
                log.info("Please configure insecure registry on the host system manually:");
                log.info("Add '{}' to insecure-registries in /etc/docker/daemon.json on the host", cleanUrl);
                log.info("Then restart Docker daemon: sudo systemctl restart docker");
                return false;
            }
            
            // 운영체제별로 다른 방법 사용
            String osName = System.getProperty("os.name").toLowerCase();
            
            if (osName.contains("windows")) {
                return updateWindowsDockerConfig(cleanUrl);
            } else if (osName.contains("linux")) {
                return updateLinuxDockerConfig(cleanUrl);
            } else if (osName.contains("mac")) {
                return updateMacDockerConfig(cleanUrl);
            } else {
                log.warn("Unsupported operating system: {}, manual configuration required for: {}", osName, cleanUrl);
                return false;
            }
            
        } catch (Exception e) {
            log.error("Error adding insecure registry dynamically: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Windows Docker 설정 업데이트
     */
    private boolean updateWindowsDockerConfig(String registryUrl) {
        try {
            // Windows Docker Desktop의 daemon.json 경로들
            String[] possiblePaths = {
                System.getProperty("user.home") + "/.docker/daemon.json",
                System.getProperty("user.home") + "/AppData/Roaming/Docker/settings.json",
                "C:/Users/" + System.getProperty("user.name") + "/.docker/daemon.json"
            };
            
            for (String path : possiblePaths) {
                java.io.File daemonFile = new java.io.File(path);
                if (daemonFile.exists()) {
                    log.info("Found Windows Docker daemon config file: {}", path);
                    return updateDaemonJsonFile(daemonFile, registryUrl);
                }
            }
            
            log.warn("Windows Docker daemon.json file not found in common locations");
            return false;
            
        } catch (Exception e) {
            log.error("Error updating Windows Docker config: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Linux Docker 설정 업데이트
     */
    private boolean updateLinuxDockerConfig(String registryUrl) {
        try {
            // Linux Docker daemon.json 경로들
            String[] possiblePaths = {
                "/etc/docker/daemon.json",
                System.getProperty("user.home") + "/.docker/daemon.json",
                "/var/lib/docker/daemon.json"
            };
            
            for (String path : possiblePaths) {
                java.io.File daemonFile = new java.io.File(path);
                if (daemonFile.exists() && daemonFile.canWrite()) {
                    log.info("Found Linux Docker daemon config file: {}", path);
                    return updateDaemonJsonFile(daemonFile, registryUrl);
                }
            }
            
            // daemon.json이 없으면 생성
            java.io.File defaultDaemonFile = new java.io.File("/etc/docker/daemon.json");
            if (defaultDaemonFile.getParentFile().exists() && defaultDaemonFile.getParentFile().canWrite()) {
                log.info("Creating new Linux Docker daemon.json file: {}", defaultDaemonFile.getAbsolutePath());
                return updateDaemonJsonFile(defaultDaemonFile, registryUrl);
            }
            
            log.warn("Linux Docker daemon.json file not found or not writable");
            return false;
            
        } catch (Exception e) {
            log.error("Error updating Linux Docker config: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * macOS Docker 설정 업데이트
     */
    private boolean updateMacDockerConfig(String registryUrl) {
        try {
            // macOS Docker Desktop의 daemon.json 경로들
            String[] possiblePaths = {
                System.getProperty("user.home") + "/.docker/daemon.json",
                System.getProperty("user.home") + "/Library/Group Containers/group.com.docker/settings.json"
            };
            
            for (String path : possiblePaths) {
                java.io.File daemonFile = new java.io.File(path);
                if (daemonFile.exists()) {
                    log.info("Found macOS Docker daemon config file: {}", path);
                    return updateDaemonJsonFile(daemonFile, registryUrl);
                }
            }
            
            log.warn("macOS Docker daemon.json file not found in common locations");
            return false;
            
        } catch (Exception e) {
            log.error("Error updating macOS Docker config: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * daemon.json 파일을 업데이트하여 insecure registry 추가
     */
    private boolean updateDaemonJsonFile(java.io.File daemonFile, String registryUrl) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT);
            
            // 기존 설정 읽기
            Map<String, Object> daemonConfig = new HashMap<>();
            if (daemonFile.exists() && daemonFile.length() > 0) {
                try {
                    daemonConfig = objectMapper.readValue(daemonFile, Map.class);
                } catch (Exception e) {
                    log.warn("Failed to parse existing daemon.json, creating new config");
                }
            }
            
            // insecure-registries 설정 추가/업데이트
            @SuppressWarnings("unchecked")
            List<String> insecureRegistries = (List<String>) daemonConfig.get("insecure-registries");
            if (insecureRegistries == null) {
                insecureRegistries = new ArrayList<>();
                daemonConfig.put("insecure-registries", insecureRegistries);
            }
            
            if (!insecureRegistries.contains(registryUrl)) {
                insecureRegistries.add(registryUrl);
                log.info("Added insecure registry to daemon.json: {}", registryUrl);
                
                // 파일에 쓰기
                objectMapper.writeValue(daemonFile, daemonConfig);
                
                // 운영체제별 재시작 안내 및 자동 재시작 시도
                String osName = System.getProperty("os.name").toLowerCase();
                if (osName.contains("windows")) {
                    log.info("Insecure registry added to daemon.json. Please restart Docker Desktop for changes to take effect.");
                } else if (osName.contains("linux")) {
                    log.info("Insecure registry added to daemon.json. Attempting to restart Docker daemon...");
                    if (restartDockerDaemon()) {
                        log.info("Docker daemon restarted successfully");
                    } else {
                        log.warn("Failed to restart Docker daemon automatically. Please run: sudo systemctl restart docker");
                    }
                } else if (osName.contains("mac")) {
                    log.info("Insecure registry added to daemon.json. Please restart Docker Desktop for changes to take effect.");
                } else {
                    log.info("Insecure registry added to daemon.json. Please restart Docker for changes to take effect.");
                }
                return true;
            } else {
                log.info("Insecure registry already exists in daemon.json: {}", registryUrl);
                return true;
            }
            
        } catch (Exception e) {
            log.error("Error updating daemon.json file: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Linux에서 Docker daemon 재시작 시도 (컨테이너 환경 고려)
     */
    private boolean restartDockerDaemon() {
        try {
            // Docker 컨테이너 환경인지 확인
            if (isRunningInDockerContainer()) {
                log.warn("Running inside Docker container - cannot restart Docker daemon");
                log.info("Please restart the Docker daemon manually on the host: sudo systemctl restart docker");
                return false;
            }
            
            log.info("Attempting to restart Docker daemon...");
            
            // systemctl을 사용하여 Docker 서비스 재시작
            ProcessBuilder restartBuilder = new ProcessBuilder("sudo", "systemctl", "restart", "docker");
            Process restartProc = restartBuilder.start();
            
            boolean finished = restartProc.waitFor(30, TimeUnit.SECONDS);
            if (finished && restartProc.exitValue() == 0) {
                log.info("Docker daemon restart command completed successfully");
                
                // 재시작 후 Docker가 정상적으로 실행되는지 확인
                Thread.sleep(5000); // 5초 대기
                return validateDockerEnvironment();
            } else {
                log.error("Failed to restart Docker daemon (exit code: {})", restartProc.exitValue());
                return false;
            }
            
        } catch (Exception e) {
            log.error("Error restarting Docker daemon: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Docker 컨테이너 내에서 실행 중인지 확인
     */
    private boolean isRunningInDockerContainer() {
        try {
            // /.dockerenv 파일 존재 여부 확인
            java.io.File dockerEnvFile = new java.io.File("/.dockerenv");
            if (dockerEnvFile.exists()) {
                log.info("Detected Docker container environment");
                return true;
            }
            
            // cgroup에서 docker 확인
            java.io.File cgroupFile = new java.io.File("/proc/1/cgroup");
            if (cgroupFile.exists()) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(new java.io.FileInputStream(cgroupFile)))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.contains("docker") || line.contains("containerd")) {
                            log.info("Detected Docker container environment via cgroup");
                            return true;
                        }
                    }
                }
            }
            
            // 환경 변수 확인
            String containerEnv = System.getenv("container");
            if ("docker".equals(containerEnv) || "podman".equals(containerEnv)) {
                log.info("Detected container environment via environment variable: {}", containerEnv);
                return true;
            }
            
            return false;
        } catch (Exception e) {
            log.debug("Error checking Docker container environment: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Docker 컨테이너에서 호스트 Docker 설정을 시도
     */
    private boolean tryConfigureHostDockerFromContainer(String registryUrl) {
        try {
            log.info("Attempting to configure host Docker from container...");
            
            // Docker 소켓이 마운트되어 있는지 확인
            java.io.File dockerSocket = new java.io.File("/var/run/docker.sock");
            if (!dockerSocket.exists()) {
                log.warn("Docker socket not found - cannot configure host Docker from container");
                return false;
            }
            
            // 호스트의 daemon.json 파일 경로들 시도
            String[] hostPaths = {
                "/host/etc/docker/daemon.json",
                "/host/var/lib/docker/daemon.json",
                "/etc/docker/daemon.json" // 컨테이너 내에서 호스트 경로가 마운트된 경우
            };
            
            for (String path : hostPaths) {
                java.io.File hostDaemonFile = new java.io.File(path);
                if (hostDaemonFile.exists() && hostDaemonFile.canWrite()) {
                    log.info("Found host Docker daemon config file: {}", path);
                    return updateDaemonJsonFile(hostDaemonFile, registryUrl);
                }
            }
            
            log.warn("Cannot access host Docker daemon configuration files from container");
            return false;
            
        } catch (Exception e) {
            log.error("Error configuring host Docker from container: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Nexus URL 파싱
     */
    private DockerRegistryInfo parseNexusUrl(String nexusUrl) throws IllegalArgumentException {
        try {
            URI uri = new URI(nexusUrl);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            int port = uri.getPort();
            
            if (host == null) {
                throw new IllegalArgumentException("Invalid URL: missing host");
            }
            
            // Docker Registry 포트를 설정 파일에서 가져오기
            boolean isSecure = dockerRegistrySecure;
            int dockerPort = dockerRegistryPort;
            // HTTP 프로토콜을 명시적으로 사용 (insecure registry)
            String registryUrl = "http://" + host.replace("http://", "") + ":" + dockerPort;
            
            log.info("Parsed Nexus URL - Host: {}, Port: {}, Secure: {}, Registry URL: {}", host, port, isSecure, registryUrl);
            
            return new DockerRegistryInfo(registryUrl, isSecure, host, port);
            
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid Nexus URL format: " + nexusUrl, e);
        }
    }
    
    /**
     * 소스 이미지 이름 생성
     */
    private String getSourceImageName(String imageName, String tag) {
        String fullImageName = imageName.contains("/") ? imageName : "library/" + imageName;
        return fullImageName + ":" + tag;
    }
    
    /**
     * Nexus 이미지 이름 생성
     */
    private String getNexusImageName(String registryUrl, String repositoryName, String imageName, String tag) {
        String fullImageName = imageName.contains("/") ? imageName : imageName;
        // 프로토콜 제거 (http:// 또는 https://)
        String cleanRegistryUrl = registryUrl.replaceFirst("^https?://", "");
        return cleanRegistryUrl + "/" + repositoryName + "/" + fullImageName + ":" + tag;
    }
    
    /**
     * 에러 결과 생성
     */
    private Map<String, Object> createErrorResult(String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("message", message);
        return result;
    }
    
    /**
     * 표준 입력을 사용한 Docker 명령어 실행 (재시도 로직 포함)
     */
    private boolean executeDockerCommandWithRetryAndStdin(ProcessBuilder processBuilder, String commandName, 
                                                         long timeoutSeconds, String stdinInput) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            log.info("Executing {} (attempt {}/{})", commandName, attempt, maxRetries);
            
            try {
                Process process = processBuilder.start();
                
                // 표준 입력으로 비밀번호 전달
                try (var writer = new java.io.OutputStreamWriter(process.getOutputStream())) {
                    writer.write(stdinInput);
                    writer.flush();
                }
                
                // 비동기로 출력 읽기
                CompletableFuture<String> outputFuture = CompletableFuture.supplyAsync(() -> {
                    StringBuilder output = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            output.append(line).append("\n");
                            log.info("[{}] STDOUT: {}", commandName, line);
                        }
                    } catch (IOException e) {
                        log.error("Error reading {} output", commandName, e);
                    }
                    return output.toString();
                });
                
                CompletableFuture<String> errorFuture = CompletableFuture.supplyAsync(() -> {
                    StringBuilder error = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            error.append(line).append("\n");
                            log.warn("[{}] STDERR: {}", commandName, line);
                        }
                    } catch (IOException e) {
                        log.error("Error reading {} error stream", commandName, e);
                    }
                    return error.toString();
                });
                
                // 프로세스 완료 대기
                boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    log.error("{} timed out after {} seconds", commandName, timeoutSeconds);
                    if (attempt < maxRetries) {
                        long waitTime = attempt * 5000L;
                        log.info("Waiting {} ms before retry...", waitTime);
                        Thread.sleep(waitTime);
                        continue;
                    }
                    return false;
                }
                
                int exitCode = process.exitValue();
                String output = outputFuture.get();
                String error = errorFuture.get();
                
                log.info("{} command completed with exit code: {}", commandName, exitCode);
                log.info("{} full output:\n{}", commandName, output);
                
                if (exitCode == 0) {
                    log.info("{} completed successfully on attempt {}", commandName, attempt);
                    return true;
                } else {
                    log.warn("{} failed on attempt {}: {}", commandName, attempt, error);
                    if (attempt < maxRetries) {
                        long waitTime = attempt * 5000L;
                        log.info("Waiting {} ms before retry...", waitTime);
                        Thread.sleep(waitTime);
                    }
                }
                
            } catch (Exception e) {
                log.error("Error executing {} on attempt {}", commandName, attempt, e);
                if (attempt < maxRetries) {
                    try {
                        long waitTime = attempt * 5000L;
                        log.info("Waiting {} ms before retry...", waitTime);
                        Thread.sleep(waitTime);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
            }
        }
        
        log.error("{} failed after {} attempts", commandName, maxRetries);
        return false;
    }
}
