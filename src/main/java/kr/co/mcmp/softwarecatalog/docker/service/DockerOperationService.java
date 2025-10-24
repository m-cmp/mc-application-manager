package kr.co.mcmp.softwarecatalog.docker.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;

import kr.co.mcmp.softwarecatalog.docker.model.ContainerDeployResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import kr.co.mcmp.softwarecatalog.application.config.NexusConfig;

@Service
@Slf4j
@RequiredArgsConstructor
public class DockerOperationService {
    
    private final DockerClientFactory dockerClientFactory;
    private final NexusConfig nexusConfig;

    public ContainerDeployResult runDockerContainer(String host, Map<String, String> deployParams) {
        return runDockerContainer(host, deployParams, null, -1, null);
    }
    
    public ContainerDeployResult runDockerContainer(String host, Map<String, String> deployParams, 
                                                   List<String> vmPublicIps, int vmIndex, String vmId) {
        try (DockerClient dockerClient = dockerClientFactory.getDockerClient(host)) {
            log.info("Docker client created successfully");
            String imageName = deployParams.get("image");
            log.info("Image name: {}", imageName);

            // 이미지 존재 여부 확인 및 pull
            boolean imageExists = checkImageExists(dockerClient, imageName);
            log.info("Image exists: {}", imageExists);
            if (!imageExists) {
                log.info("Pulling image: {}", imageName);
                pullImage(dockerClient, imageName);
                log.info("Image pulled successfully");
            }

            // 포트 바인딩 설정 (복수 포트 지원)
            String portBindingsStr = deployParams.get("portBindings");
            Ports portBindings = parsePortBindings(portBindingsStr);

            // HostConfig 생성
            HostConfig hostConfig = HostConfig.newHostConfig().withPortBindings(portBindings);

            // 이미지 타입에 따른 적절한 명령어 설정
            String[] cmd = getCommandForImage(imageName);
            
            // 노출할 포트들 추출
            ExposedPort[] exposedPorts = portBindings.getBindings().keySet().toArray(new ExposedPort[0]);
            
            // 환경변수 설정 (애플리케이션별) - 클러스터링 지원
            Map<String, String> envVars = getEnvironmentVariables(imageName, vmPublicIps, vmIndex, vmId);
            
            // 컨테이너 생성
            CreateContainerCmd createCmd = dockerClient.createContainerCmd(imageName)
                .withName(deployParams.get("name"))
                .withHostConfig(hostConfig)
                .withExposedPorts(exposedPorts);
            
            // 환경변수 설정
            if (!envVars.isEmpty()) {
                createCmd.withEnv(envVars.entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .toArray(String[]::new));
            }
            
            // 명령어 설정 (null이 아닌 경우에만)
            if (cmd != null) {
                createCmd.withCmd(cmd);
            }
            
            CreateContainerResponse container = createCmd.exec();

            String containerId = container.getId();
            log.info("Container created with ID: {}", containerId);

            // 컨테이너 시작
            dockerClient.startContainerCmd(containerId).exec();
            log.info("Container started: {}", containerId);

            boolean isRunning = waitForContainerToStart(dockerClient, containerId);
            log.info("Container running status: {}", isRunning);
            
            // 컨테이너 상태를 더 자세히 로깅
            if (!isRunning) {
                InspectContainerResponse containerInfo = dockerClient.inspectContainerCmd(containerId).exec();
                log.warn("Container failed to start. State: {}, ExitCode: {}, Error: {}", 
                    containerInfo.getState().getStatus(), 
                    containerInfo.getState().getExitCode(),
                    containerInfo.getState().getError());
            }

            return new ContainerDeployResult(containerId, "Container started", isRunning);
        } catch (Exception e) {
            log.error("Error running Docker container", e);
            return new ContainerDeployResult(null, e.getMessage(), false);
        }
    }

    

    private boolean checkImageExists(DockerClient dockerClient, String imageName) {
        try {
            dockerClient.inspectImageCmd(imageName).exec();
            return true; 
        } catch (NotFoundException e) {
            return false;
        }
    }

    private void pullImage(DockerClient dockerClient, String imageName) throws InterruptedException {
        try {
            // 항상 Docker Hub에서 직접 pull
            log.info("Pulling image from Docker Hub: {}", imageName);
            dockerClient.pullImageCmd(imageName)
                .exec(new PullImageResultCallback())
                .awaitCompletion(5, TimeUnit.MINUTES);
        } catch (NotFoundException e) {
            log.error("Image not found: {}", imageName);
            throw e;
        }
    }
    
    /**
     * 포트 바인딩 문자열을 파싱하여 Ports 객체를 생성합니다.
     * 지원 형식: "9200:9200" 또는 "9200:9200,9300:9300"
     */
    private Ports parsePortBindings(String portBindingsStr) {
        Ports portBindings = new Ports();
        
        if (portBindingsStr == null || portBindingsStr.trim().isEmpty()) {
            return portBindings;
        }
        
        try {
            // 쉼표로 분리하여 각 포트 바인딩 처리
            String[] portMappings = portBindingsStr.split(",");
            
            for (String portMapping : portMappings) {
                portMapping = portMapping.trim();
                if (portMapping.isEmpty()) {
                    continue;
                }
                
                // "hostPort:containerPort" 형식 파싱
                String[] parts = portMapping.split(":");
                if (parts.length != 2) {
                    log.warn("Invalid port mapping format: {}", portMapping);
                    continue;
                }
                
                int hostPort = Integer.parseInt(parts[0].trim());
                int containerPort = Integer.parseInt(parts[1].trim());
                
                ExposedPort exposedPort = ExposedPort.tcp(containerPort);
                portBindings.bind(exposedPort, Ports.Binding.bindPort(hostPort));
                
                log.debug("Added port binding: {}:{}", hostPort, containerPort);
            }
            
        } catch (NumberFormatException e) {
            log.error("Invalid port number in port bindings: {}", portBindingsStr, e);
            throw new IllegalArgumentException("Invalid port number in port bindings: " + portBindingsStr, e);
        } catch (Exception e) {
            log.error("Error parsing port bindings: {}", portBindingsStr, e);
            throw new IllegalArgumentException("Error parsing port bindings: " + portBindingsStr, e);
        }
        
        return portBindings;
    }
    private boolean waitForContainerToStart(DockerClient dockerClient, String containerId) throws InterruptedException {
        // Elasticsearch는 시작하는데 더 오래 걸릴 수 있으므로 60초로 증가
        for (int i = 0; i < 60; i++) {
            InspectContainerResponse containerInfo = dockerClient.inspectContainerCmd(containerId).exec();
            String status = containerInfo.getState().getStatus();
            Boolean running = containerInfo.getState().getRunning();
            
            log.info("Container status check {}/60: status={}, running={}", i + 1, status, running);
            
            if (running != null && running) {
                log.info("Container is now running successfully");
                return true;
            }
            
            // 컨테이너가 종료된 경우
            if ("exited".equals(status)) {
                log.warn("Container exited with code: {}", containerInfo.getState().getExitCode());
                // 컨테이너 로그 확인 (간단한 방법)
                try {
                    log.warn("Container exited. Check container logs manually with: docker logs {}", containerId);
                } catch (Exception e) {
                    log.warn("Could not retrieve container logs: {}", e.getMessage());
                }
                return false;
            }
            
            Thread.sleep(1000);
        }
        log.warn("Container failed to start within 60 seconds");
        return false;
    }
    
    /**
     * 이미지 타입에 따른 적절한 명령어를 반환합니다.
     */
    private String[] getCommandForImage(String imageName) {
        String lowerImageName = imageName.toLowerCase();
        
        if (lowerImageName.contains("elasticsearch")) {
            // Elasticsearch는 기본 명령어 사용 (환경변수는 컨테이너 생성시 설정)
            return null; // 기본 명령어 사용
        } else if (lowerImageName.contains("redis")) {
            // Redis는 기본 명령어 사용
            return null;
        } else if (lowerImageName.contains("mariadb") || lowerImageName.contains("mysql")) {
            // MariaDB/MySQL은 기본 명령어 사용
            return null;
        } else if (lowerImageName.contains("ruby")) {
            // Ruby 컨테이너는 대화형 모드로 실행
            return new String[]{"ruby", "-e", "loop { sleep 1 }"};
        } else if (lowerImageName.contains("python")) {
            // Python 컨테이너는 대화형 모드로 실행
            return new String[]{"python", "-c", "import time; [time.sleep(1) for _ in iter(int, 1)]"};
        } else if (lowerImageName.contains("node")) {
            // Node.js 컨테이너는 대화형 모드로 실행
            return new String[]{"node", "-e", "setInterval(() => {}, 1000)"};
        } else if (lowerImageName.contains("nginx")) {
            // Nginx는 기본 명령어 사용
            return new String[]{"nginx", "-g", "daemon off;"};
        } else if (lowerImageName.contains("apache")) {
            // Apache는 기본 명령어 사용
            return new String[]{"httpd", "-D", "FOREGROUND"};
        } else {
            // 기본적으로 컨테이너가 계속 실행되도록 함
            return new String[]{"tail", "-f", "/dev/null"};
        }
    }
    
    /**
     * 이미지 타입에 따른 환경변수를 반환합니다.
     */
    private Map<String, String> getEnvironmentVariables(String imageName) {
        return getEnvironmentVariables(imageName, null, -1, null);
    }
    
    /**
     * 이미지 타입에 따른 환경변수를 반환합니다. (클러스터링 지원)
     */
    private Map<String, String> getEnvironmentVariables(String imageName, List<String> vmPublicIps, int vmIndex, String vmId) {
        Map<String, String> envVars = new HashMap<>();
        String lowerImageName = imageName.toLowerCase();
        
        if (lowerImageName.contains("elasticsearch")) {
            // Elasticsearch 클러스터링 환경 변수 설정
            envVars.put("cluster.name", "elasticsearch-cluster");
            envVars.put("network.host", "0.0.0.0");
            envVars.put("http.port", "9200");
            envVars.put("transport.port", "9300");
            envVars.put("xpack.security.enabled", "false");
            envVars.put("xpack.ml.enabled", "false");
            envVars.put("ES_JAVA_OPTS", "-Xms256m -Xmx256m");
            envVars.put("bootstrap.memory_lock", "false");
            
            // 클러스터링 설정이 있는 경우
            if (vmPublicIps != null && !vmPublicIps.isEmpty() && vmIndex >= 0) {
                // 노드 이름 설정 (es-01, es-02, es-03...)
                String nodeName = String.format("es-%02d", vmIndex + 1);
                envVars.put("node.name", nodeName);
                
                // discovery.seed_hosts를 공인 IP로 설정
                String seedHosts = String.join(",", vmPublicIps);
                envVars.put("discovery.seed_hosts", seedHosts);
                
                // cluster.initial_master_nodes를 노드 이름으로 설정
                List<String> nodeNames = new ArrayList<>();
                for (int i = 0; i < vmPublicIps.size(); i++) {
                    nodeNames.add(String.format("es-%02d", i + 1));
                }
                envVars.put("cluster.initial_master_nodes", String.join(",", nodeNames));
                
                // publish_host를 현재 노드의 공인 IP로 설정
                if (vmIndex < vmPublicIps.size()) {
                    envVars.put("network.publish_host", vmPublicIps.get(vmIndex));
                }
            } else {
                // 단일 노드 설정 (fallback)
                envVars.put("discovery.type", "single-node");
            }
        } else if (lowerImageName.contains("redis")) {
            // Redis 환경변수 설정
            envVars.put("REDIS_PASSWORD", "");
        } else if (lowerImageName.contains("mariadb") || lowerImageName.contains("mysql")) {
            // MariaDB/MySQL 환경변수 설정
            envVars.put("MYSQL_ROOT_PASSWORD", "password");
            envVars.put("MYSQL_DATABASE", "testdb");
            envVars.put("MYSQL_USER", "testuser");
            envVars.put("MYSQL_PASSWORD", "testpass");
        }
        
        return envVars;
    }
    
    /**
     * Docker Hub 이미지명을 Nexus 이미지명으로 변환합니다.
     * 현재는 Docker Hub에서 직접 pull하므로 사용하지 않음.
     */
    @Deprecated
    private String convertToNexusImageName(String dockerHubImageName) {
        // 이미 Nexus URL이 포함된 경우 그대로 반환
        if (dockerHubImageName.contains(nexusConfig.getDockerRegistryUrl())) {
            log.info("Image already contains Nexus URL, using as-is: {}", dockerHubImageName);
            return dockerHubImageName;
        }
        
        // docker.io/ 제거
        String cleanImageName = dockerHubImageName.replaceFirst("^docker\\.io/", "");
        
        // Nexus 레지스트리 URL과 Docker 레포지토리명을 사용하여 변환
        String nexusImageName = nexusConfig.getDockerRegistryUrl() + "/" + 
                               nexusConfig.getDockerRepository() + "/" + 
                               cleanImageName;
        
        log.info("Converted image name: {} -> {}", dockerHubImageName, nexusImageName);
        return nexusImageName;
    }

    public String getDockerContainerStatus(String host, String containerId) {
        try (DockerClient dockerClient = dockerClientFactory.getDockerClient(host)) {
            return dockerClient.inspectContainerCmd(containerId)
                    .exec()
                    .getState()
                    .getStatus();
        } catch (Exception e) {
            log.error("Error getting Docker container status", e);
            return "ERROR";
        }
    }

    public String stopDockerContainer(String host, String containerId) {
        try (DockerClient dockerClient = dockerClientFactory.getDockerClient(host)) {
            dockerClient.stopContainerCmd(containerId).exec();
            return "Container stopped successfully";
        } catch (Exception e) {
            log.error("Error stopping Docker container", e);
            return "Error: " + e.getMessage();
        }
    }

    public String removeDockerContainer(String host, String containerId) {
        try (DockerClient dockerClient = dockerClientFactory.getDockerClient(host)) {
            dockerClient.removeContainerCmd(containerId)
                .withForce(true)
                .withRemoveVolumes(true)
                .exec();
            return "Container removed successfully";
        } catch (Exception e) {
            log.error("Error removing Docker container", e);
            return "Error: " + e.getMessage();
        }
    }

    public String restartDockerContainer(String host, String containerId) {
        try (DockerClient dockerClient = dockerClientFactory.getDockerClient(host)) {
            dockerClient.restartContainerCmd(containerId).exec();
            return "Container restarted successfully";
        } catch (Exception e) {
            log.error("Error restarting Docker container", e);
            return "Error: " + e.getMessage();
        }
    }

    public boolean isContainerRunning(String host, String containerId) {
        try (DockerClient dockerClient = dockerClientFactory.getDockerClient(host)) {
            return isContainerRunning(dockerClient, containerId);
        } catch (Exception e) {
            log.error("Error checking if container is running", e);
            return false;
        }
    }

    private boolean isContainerRunning(DockerClient dockerClient, String containerId) {
        List<Container> containers = dockerClient.listContainersCmd()
                .withShowAll(true)
                .exec();
        return containers.stream()
                .anyMatch(container -> container.getId().equals(containerId) && "running".equalsIgnoreCase(container.getState()));
    }
}
