package kr.co.mcmp.softwarecatalog.docker.service;

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

            // 포트 바인딩 설정
            String[] portMapping = deployParams.get("portBindings").split(":");
            int hostPort = Integer.parseInt(portMapping[0]);
            int containerPort = Integer.parseInt(portMapping[1]);
            ExposedPort exposedPort = ExposedPort.tcp(containerPort);
            Ports portBindings = new Ports();
            portBindings.bind(exposedPort, Ports.Binding.bindPort(hostPort));

            // HostConfig 생성
            HostConfig hostConfig = HostConfig.newHostConfig().withPortBindings(portBindings);

            // 이미지 타입에 따른 적절한 명령어 설정
            String[] cmd = getCommandForImage(imageName);
            
            // 컨테이너 생성
            CreateContainerResponse container = dockerClient.createContainerCmd(imageName)
            .withName(deployParams.get("name"))
            .withHostConfig(hostConfig)
            .withExposedPorts(exposedPort)
            .withCmd(cmd)
            .exec();

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
    private boolean waitForContainerToStart(DockerClient dockerClient, String containerId) throws InterruptedException {
        for (int i = 0; i < 30; i++) {
            InspectContainerResponse containerInfo = dockerClient.inspectContainerCmd(containerId).exec();
            String status = containerInfo.getState().getStatus();
            Boolean running = containerInfo.getState().getRunning();
            
            log.debug("Container status check {}/30: status={}, running={}", i + 1, status, running);
            
            if (running != null && running) {
                log.info("Container is now running successfully");
                return true;
            }
            
            // 컨테이너가 종료된 경우
            if ("exited".equals(status)) {
                log.warn("Container exited with code: {}", containerInfo.getState().getExitCode());
                return false;
            }
            
            Thread.sleep(1000);
        }
        log.warn("Container failed to start within 30 seconds");
        return false;
    }
    
    /**
     * 이미지 타입에 따른 적절한 명령어를 반환합니다.
     */
    private String[] getCommandForImage(String imageName) {
        String lowerImageName = imageName.toLowerCase();
        
        if (lowerImageName.contains("ruby")) {
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
