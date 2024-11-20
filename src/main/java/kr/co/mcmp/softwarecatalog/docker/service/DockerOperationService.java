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

@Service
@Slf4j
@RequiredArgsConstructor
public class DockerOperationService {
    
    private final DockerClientFactory dockerClientFactory;

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

            // 컨테이너 생성
            CreateContainerResponse container = dockerClient.createContainerCmd(imageName)
            .withName(deployParams.get("name"))
            .withHostConfig(hostConfig)
            .withExposedPorts(exposedPort)
            .exec();

            String containerId = container.getId();
            log.info("Container created with ID: {}", containerId);

            // 컨테이너 시작
            dockerClient.startContainerCmd(containerId).exec();
            log.info("Container started: {}", containerId);

            boolean isRunning = waitForContainerToStart(dockerClient, containerId);
            log.info("Container running status: {}", isRunning);

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
            if (containerInfo.getState().getRunning()) {
                return true;
            }
            Thread.sleep(1000);
        }
        return false;
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
