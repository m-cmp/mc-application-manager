package kr.co.mcmp.softwarecatalog.application.service.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import kr.co.mcmp.oss.dto.OssDto;
import kr.co.mcmp.softwarecatalog.SoftwareCatalogDTO;
import kr.co.mcmp.softwarecatalog.application.dto.PackageInfoDTO;
import kr.co.mcmp.softwarecatalog.application.service.DockerHubIntegrationService;
import kr.co.mcmp.softwarecatalog.application.service.NexusIntegrationService;
import kr.co.mcmp.util.Base64Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Docker Hub 연동 서비스 구현체 (개선된 버전)
 */
@Service
@RequiredArgsConstructor
public class DockerHubIntegrationServiceImpl implements DockerHubIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(DockerHubIntegrationServiceImpl.class);

    @Autowired
    private RestTemplate restTemplate;
    private final NexusIntegrationService nexusIntegrationService;
    private final ObjectMapper objectMapper;

    // 설정 값들
    @Value("${docker.push.timeout-seconds:600}")
    private long dockerTimeoutSeconds;

    @Value("${docker.push.max-retries:3}")
    private int maxRetries;

    // Docker Registry 설정
    @Value("${docker.registry.port:5500}")
    private int dockerRegistryPort;

    @Value("${docker.registry.secure:false}")
    private boolean dockerRegistrySecure;

    @Value("${docker.push.retry-delay-seconds:5}")
    private long retryDelaySeconds;

    @Value("${docker.validation.check-daemon:true}")
    private boolean checkDockerDaemon;

    private static final String DOCKER_HUB_API_BASE = "https://hub.docker.com/v2";

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

    @Override
    public Map<String, Object> searchImages(String query, int page, int pageSize) {
        log.info("Searching Docker Hub images: query={}, page={}, pageSize={}", query, page, pageSize);

        Map<String, Object> result = new HashMap<>();

        try {
            String url;
            if (query.equals("nginx") || query.equals("ubuntu") || query.equals("redis")) {
                url = String.format("https://hub.docker.com/v2/repositories/library/%s/", query);
            } else {
                url = String.format("https://hub.docker.com/v2/search/repositories/?q=%s&page=%d&page_size=%d",
                    query, page, pageSize);
            }

            log.info("Docker Hub search URL: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            headers.set("Accept", "application/json");

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                result.put("success", true);
                result.put("data", response.getBody());
                result.put("message", "Docker Hub search completed successfully");
            } else {
                result.put("success", false);
                result.put("message", "Failed to search Docker Hub: " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Error searching Docker Hub images", e);
            result.put("success", false);
            result.put("message", "Error searching Docker Hub: " + e.getMessage());
        }

        return result;
    }

    @Override
    public Map<String, Object> getImageDetails(String path, String tag) {
        log.info("Getting Docker Hub image details: imageName={}, tag={}", path, tag);

        Map<String, Object> result = new HashMap<>();

        try {
            String url = String.format("%s/repositories/%s/tags/%s", DOCKER_HUB_API_BASE, path, tag);
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                Map<String, Object> responseBody = (Map<String, Object>) response.getBody();

                // 전체 응답 로깅
                log.info("=== Docker Hub API Response ===");
                log.info("URL: {}", url);
                log.info("Status Code: {}", response.getStatusCode());
                log.info("Response Headers: {}", response.getHeaders());
                // log.info("Response Body: {}", responseBody);
                log.info("=== End Docker Hub API Response ===");

                result.put("success", true);
                result.put("data", responseBody);
                result.put("message", "Docker Hub image details retrieved successfully");
            } else {
                log.error("Docker Hub API Error - URL: {}, Status: {}", url, response.getStatusCode());
                result.put("success", false);
                result.put("message", "Failed to get Docker Hub image details: " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Error getting Docker Hub image details", e);
            result.put("success", false);
            result.put("message", "Error getting Docker Hub image details: " + e.getMessage());
        }

        return result;
    }

    @Override
    public List<String> getImageTags(String imageName) {
        log.info("Getting Docker Hub image tags: imageName={}", imageName);

        List<String> tags = new ArrayList<>();

        try {
            String url = String.format("%s/repositories/%s/tags/", DOCKER_HUB_API_BASE, imageName);
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                Map<String, Object> responseBody = (Map<String, Object>) response.getBody();
                List<Map<String, Object>> results = (List<Map<String, Object>>) responseBody.get("results");

                if (results != null) {
                    for (Map<String, Object> tagInfo : results) {
                        String tagName = (String) tagInfo.get("name");
                        if (tagName != null) {
                            tags.add(tagName);
                        }
                    }
                }
            }

        } catch (Exception e) {
            log.error("Error getting Docker Hub image tags", e);
        }

        return tags;
    }

    @Override
    public Map<String, Object> pushImageToNexus(String imageName, String tag) {
        log.info("=== Starting enhanced Docker Hub to Nexus push ===");
        log.info("Image: {}:{}", imageName, tag);

        try {
            // 1. OSS 정보 가져오기
            OssDto ossInfo = nexusIntegrationService.getNexusInfoFromDB();
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
                result.put("success", true);
                result.put("message", "이미지가 성공적으로 Nexus에 푸시되었습니다.");
                result.put("registryUrl", registryInfo.getRegistryUrl());
                result.put("repository", nexusIntegrationService.getRepositoryNameByFormat("docker"));
                result.put("fullImageName", String.format("%s/%s/%s:%s",
                    registryInfo.getRegistryUrl(), nexusIntegrationService.getRepositoryNameByFormat("docker"), imageName, tag));
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
     * 개선된 Docker 푸시 실행 (NexusIntegrationServiceImpl의 pushImageAndRegisterCatalog 호출)
     */
    private boolean executeEnhancedDockerPush(String imageName, String tag, OssDto ossInfo,
                                            DockerRegistryInfo registryInfo) {
        log.info("=== Starting enhanced Docker push process using NexusIntegrationService ===");

        try {
            // SoftwareCatalogDTO 생성
            SoftwareCatalogDTO catalog = createSoftwareCatalogDTO(imageName, tag, ossInfo);
            
            // NexusIntegrationServiceImpl의 pushImageAndRegisterCatalog 메서드 호출
            Map<String, Object> result = nexusIntegrationService.pushImageAndRegisterCatalog(catalog);
            
            // 결과 확인
            Boolean success = (Boolean) result.get("success");
            return success != null && success;
        } catch (Exception e) {
            log.error("Error in enhanced Docker push process", e);
            return false;
        }
    }

    /**
     * SoftwareCatalogDTO 생성
     */
    private SoftwareCatalogDTO createSoftwareCatalogDTO(String imageName, String tag, OssDto ossInfo) {
        SoftwareCatalogDTO catalog = new SoftwareCatalogDTO();
        
        // 기본 정보 설정
        catalog.setName(imageName);
        catalog.setDescription("Docker Hub image: " + imageName + ":" + tag);
        catalog.setCategory("Docker");
        // catalog.setSourceType("DOCKER_HUB");
        
        // PackageInfo 설정
        PackageInfoDTO packageInfo = new PackageInfoDTO();
        packageInfo.setPackageName(imageName);
        packageInfo.setPackageVersion(tag);
        // packageInfo.setDescription("Docker Hub image: " + imageName + ":" + tag);
        catalog.setPackageInfo(packageInfo);
        
        // HelmChart 설정 (Docker 이미지이므로 null)
        catalog.setHelmChart(null);
        
        return catalog;
    }

    /**
     * Docker Hub에서 이미지 풀 (NexusIntegrationServiceImpl 호출)
     */
    private boolean pullImageFromDockerHub(String imageName, String tag) {
        log.info("Step 1/4: Pulling image from Docker Hub...");

        try {
            String sourceImage = getSourceImageName(imageName, tag);
            ProcessBuilder pullProcess = new ProcessBuilder("docker", "pull", sourceImage);
            pullProcess.environment().put("DOCKER_BUILDKIT", "0");
            return executeDockerCommandWithRetry(pullProcess, "Docker Pull", dockerTimeoutSeconds);
        } catch (Exception e) {
            log.error("Error pulling image from Docker Hub: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Nexus에 로그인 (NexusIntegrationServiceImpl의 executeEnhancedDockerPush 호출)
     */
    private boolean loginToNexus(String registryUrl, String username, String password) {
        log.info("Step 2/4: Logging into Nexus using NexusIntegrationService...");

        try {
            // executeEnhancedDockerPush에서 로그인을 포함하여 처리
            return true;
        } catch (Exception e) {
            log.error("Error logging into Nexus: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 이미지 태그 생성 (NexusIntegrationServiceImpl의 executeEnhancedDockerPush 호출)
     */
    private boolean tagImageForNexus(String sourceImage, String nexusImage) {
        log.info("Step 3/4: Tagging image for Nexus using NexusIntegrationService...");

        try {
            // executeEnhancedDockerPush에서 태그도 처리함
            return true;
        } catch (Exception e) {
            log.error("Error tagging image: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 이미지를 레지스트리에 푸시 (NexusIntegrationServiceImpl의 executeEnhancedDockerPush 호출)
     */
    private boolean pushImageToRegistry(String nexusImage) {
        log.info("Step 4/4: Pushing image to Nexus using NexusIntegrationService...");

        try {
            // executeEnhancedDockerPush에서 푸시도 처리함
            return true;
        } catch (Exception e) {
            log.error("Error pushing image to Nexus: {}", e.getMessage());
            return false;
        }
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
     * Insecure registry 설정 검증
     */
    private boolean validateInsecureRegistryConfiguration(String registryUrl) {
        log.info("Validating insecure registry configuration for: {}", registryUrl);

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
                boolean isConfigured = registries.contains(registryUrl) || registries.contains("127.0.0.0/8");

                if (isConfigured) {
                    log.info("Insecure registry configuration found for: {}", registryUrl);
                } else {
                    log.warn("Insecure registry not configured for: {}", registryUrl);
                    log.warn("Docker info output: {}", registries);
                }

                return isConfigured;
            } else {
                log.warn("Failed to get Docker info for insecure registry validation");
                return false;
            }
        } catch (Exception e) {
            log.error("Error validating insecure registry configuration: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 운영체제에 따른 Docker Socket 경로 반환
     */
    private String getDockerSocketPath() {
        String os = System.getProperty("os.name").toLowerCase();
        
        if (os.contains("windows")) {
            // Windows 환경 (Docker Desktop)
            return "//var/run/docker.sock:/var/run/docker.sock";
        } else {
            // Linux/Unix 환경
            return "/var/run/docker.sock:/var/run/docker.sock";
        }
    }

    /**
     * Docker config JSON 생성
     */
    private String createDockerConfig(String registryUrl, String username, String password) {
        String hostPort = registryUrl.replace("http://", "").replace("https://", "");
        String auth = java.util.Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
        
        return String.format(
            "{\n" +
            "  \"auths\": {\n" +
            "    \"%s\": {\n" +
            "      \"auth\": \"%s\"\n" +
            "    }\n" +
            "  }\n" +
            "}",
            hostPort, auth
        );
    }

    /**
     * 컨테이너 내부 Docker daemon 사용
     */
    private boolean startDinDContainer(String containerName, String registryUrl) {
        try {
            log.info("Using container internal Docker daemon: {}", containerName);
            
            // 컨테이너 내부 Docker daemon이 준비될 때까지 대기
            if (waitForContainerDockerDaemonReady()) {
                log.info("Container Docker daemon is ready");
                return true;
            } else {
                log.error("Container Docker daemon failed to become ready");
                return false;
            }
            
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
                    return true;
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
     * 직접 Docker 명령어로 레지스트리 로그인
     */
    private boolean loginDirectlyToRegistry(String registryUrl, String username, String password) {
        try {
            log.info("Attempting direct Docker login to registry: {}", registryUrl);
            
            // 직접 Docker 명령어 실행
            ProcessBuilder loginProcess = new ProcessBuilder(
                "docker", "login", "http://" + registryUrl, "-u", username, "--password-stdin"
            );
            
            // Docker 환경 설정
            loginProcess.environment().put("DOCKER_TLS_VERIFY", "0");
            loginProcess.environment().put("DOCKER_CONTENT_TRUST", "0");
            loginProcess.environment().put("DOCKER_BUILDKIT", "0");
            loginProcess.environment().put("DOCKER_INSECURE_REGISTRIES", registryUrl);
            
            return executeDockerCommandWithRetryAndStdin(loginProcess, "Direct Docker Login", 30, password);
            
        } catch (Exception e) {
            log.error("Error in direct Docker login: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 컨테이너 내부에서 직접 로그인
     */
    private boolean loginInDinDContainer(String containerName, String registryUrl, String username, String password) {
        try {
            log.info("Attempting login in container: {}", registryUrl);
            
            // 컨테이너 내부에서 직접 Docker 명령어 실행
            ProcessBuilder loginProcess = new ProcessBuilder(
                "docker", "login", "http://" + registryUrl, "-u", username, "--password-stdin"
            );
            
            // 컨테이너 내부 환경 설정
            loginProcess.environment().put("DOCKER_TLS_VERIFY", "0");
            loginProcess.environment().put("DOCKER_CONTENT_TRUST", "0");
            loginProcess.environment().put("DOCKER_BUILDKIT", "0");
            loginProcess.environment().put("DOCKER_INSECURE_REGISTRIES", registryUrl);
            
            return executeDockerCommandWithRetryAndStdin(loginProcess, "Container Docker Login", 30, password);
            
        } catch (Exception e) {
            log.error("Error logging in container: {}", e.getMessage());
            return false;
        }
    }

    /**
     * DinD 컨테이너 정리
     */
    private void cleanupDinDContainer(String containerName) {
        try {
            log.info("Cleaning up DinD container: {}", containerName);
            
            // 컨테이너 중지 및 제거
            ProcessBuilder stopProcess = new ProcessBuilder("docker", "stop", containerName);
            stopProcess.start().waitFor();
            
            ProcessBuilder removeProcess = new ProcessBuilder("docker", "rm", containerName);
            removeProcess.start().waitFor();
            
            log.info("DinD container '{}' cleaned up successfully", containerName);
            
        } catch (Exception e) {
            log.warn("Error cleaning up DinD container '{}': {}", containerName, e.getMessage());
        }
    }

    /**
     * DinD 컨테이너의 Docker daemon 준비 대기 (deprecated - use waitForContainerDockerDaemonReady)
     */
    private boolean waitForDinDDaemonReady(String containerName) {
        // 이 메서드는 더 이상 사용되지 않음 - waitForContainerDockerDaemonReady 사용
        return waitForContainerDockerDaemonReady();
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
            String registryUrl = host + ":" + dockerPort;

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
        return registryUrl + "/" + repositoryName + "/" + fullImageName + ":" + tag;
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

    @Override
    public Map<String, Object> searchDockerHubImages(String query, int page, int pageSize) {
        return searchImages(query, page, pageSize);
    }

    @Override
    public Map<String, Object> getDockerHubImageDetails(String imageName, String tag) {
        return getImageDetails(imageName, tag);
    }

    @Override
    public Map<String, Object> getImageInfo(String path, String tag) {
        return getImageDetails(path, tag);
    }

    /**
     * Docker 푸시 명령어를 생성 (참고용)
     */
    public Map<String, String> createDockerPushCommands(String imageName, String tag, OssDto ossInfo) {
        Map<String, String> commands = new HashMap<>();

        try {
            DockerRegistryInfo registryInfo = parseNexusUrl(ossInfo.getOssUrl());
            String username = ossInfo.getOssUsername();
            String password = Base64Utils.base64Decoding(ossInfo.getOssPassword());
            String repositoryName = nexusIntegrationService.getRepositoryNameByFormat("docker");

            String sourceImage = getSourceImageName(imageName, tag);
            String nexusImage = getNexusImageName(registryInfo.getRegistryUrl(), repositoryName, imageName, tag);

            // Docker pull은 anonymous로 가능 (Allow anonymous docker pull 체크 시)
            commands.put("pull_source", "docker pull " + sourceImage);
            // Docker push는 인증 필요
            commands.put("login", "docker login " + registryInfo.getRegistryUrl() + " -u " + username + " -p " + password);
            commands.put("tag_image", "docker tag " + sourceImage + " " + nexusImage);
            commands.put("push", "docker push " + nexusImage);

        } catch (Exception e) {
            log.error("Error creating Docker push commands: {}", e.getMessage());
            commands.put("error", "Failed to create commands: " + e.getMessage());
        }

        return commands;
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