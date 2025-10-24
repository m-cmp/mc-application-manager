package kr.co.mcmp.softwarecatalog.docker.service;

import java.time.Duration;

import org.springframework.stereotype.Component;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class DockerClientFactory {
    
    public DockerClient getDockerClient(String host) {
        String dockerHost = "tcp://" + host + ":2375";
        log.info("Creating Docker client for host: {}", dockerHost);
        
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(dockerHost)
                .build();
        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .maxConnections(100)
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofSeconds(45))
                .build();
        
        DockerClient client = DockerClientImpl.getInstance(config, httpClient);
        log.info("Docker client created successfully for host: {}", dockerHost);
        return client;
    }

}