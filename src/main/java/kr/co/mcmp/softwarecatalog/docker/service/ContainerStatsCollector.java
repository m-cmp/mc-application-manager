package kr.co.mcmp.softwarecatalog.docker.service;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.HealthState;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ContainerNetwork;
import com.github.dockerjava.api.model.CpuStatsConfig;
import com.github.dockerjava.api.model.CpuUsageConfig;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.Statistics;

import kr.co.mcmp.softwarecatalog.docker.model.ContainerHealthInfo;
import lombok.extern.slf4j.Slf4j;
@Slf4j
@Component
public class ContainerStatsCollector {

    public String getContainerId(DockerClient dockerClient, String containerName) {
        try {
            List<Container> containers = dockerClient.listContainersCmd().withShowAll(true).exec();
            log.info("Searching for container with name pattern: {}", containerName);
            log.info("Available containers: {}", 
                containers.stream()
                    .map(container -> Arrays.toString(container.getNames()))
                    .collect(java.util.stream.Collectors.toList()));
            
            return containers.stream()
                .filter(container -> {
                    String[] names = container.getNames();
                    if (names != null) {
                        for (String name : names) {
                            // 부분 문자열 매칭으로 변경 (정확한 이름 매칭에서)
                            if (name.contains(containerName)) {
                                log.debug("Found matching container: {}", name);
                                return true;
                            }
                        }
                    }
                    return false;
                })
                .findFirst()
                .map(Container::getId)
                .orElse(null);
        } catch (Exception e) {
            log.error("Error getting container ID for {}", containerName, e);
            return null;
        }
    }

    public ContainerHealthInfo collectContainerStats(DockerClient dockerClient, String containerId) {
        log.info("Collecting container stats for containerId: {}", containerId);
        try {
            InspectContainerResponse containerInfo = dockerClient.inspectContainerCmd(containerId).exec();
            log.debug("Container info retrieved successfully");
            
            Statistics stats = collectStatistics(dockerClient, containerId);
            log.debug("Statistics collected: {}", stats != null ? "SUCCESS" : "NULL");
            
            Integer servicePort = getServicePort(containerInfo);
            String ipAddress = getContainerIpAddress(containerInfo);
            Boolean isPortAccessible = servicePort != null && ipAddress != null && isPortAccessible(ipAddress, servicePort);
            Boolean isHealthCheck = isContainerHealthy(containerInfo);
            
            Double cpuUsage = calculateCpuUsage(stats);
            Double memoryUsage = calculateMemoryUsage(stats);
            Double networkIn = calculateNetworkIn(stats);
            Double networkOut = calculateNetworkOut(stats);
            
            log.info("Container stats - CPU: {}%, Memory: {}%, Network In: {}, Network Out: {}", 
                    cpuUsage, memoryUsage, networkIn, networkOut);
            
            return ContainerHealthInfo.builder()
                    .status(mapContainerStatus(containerInfo.getState().getStatus()))
                    .servicePorts(servicePort)
                    .cpuUsage(cpuUsage)
                    .memoryUsage(memoryUsage)
                    .isPortAccess(isPortAccessible)
                    .isHealthCheck(isHealthCheck)
                    .networkIn(networkIn)
                    .networkOut(networkOut)
                    .build();
        } catch (Exception e) {
            log.error("Error collecting container stats for containerId: {}", containerId, e);
            return ContainerHealthInfo.builder()
                    .status("ERROR")
                    .build();
        }
    }

    private String getContainerIpAddress(InspectContainerResponse containerInfo) {
        try {
            Map<String, ContainerNetwork> networks = containerInfo.getNetworkSettings().getNetworks();
            if (networks != null && !networks.isEmpty()) {
                return networks.values().iterator().next().getIpAddress();
            }
        } catch (Exception e) {
            log.error("Error getting container IP address", e);
        }
        return null;
    }

    private Statistics collectStatistics(DockerClient dockerClient, String containerId) {
        log.debug("Collecting statistics for containerId: {}", containerId);
        try (StatsCallback statsCallback = new StatsCallback()) {
            dockerClient.statsCmd(containerId).exec(statsCallback);
            boolean completed = statsCallback.awaitCompletion(5, TimeUnit.SECONDS);
            log.debug("Statistics collection completed: {}", completed);
            
            Statistics stats = statsCallback.getStats();
            if (stats == null) {
                log.warn("Statistics is null for containerId: {}", containerId);
            } else {
                log.debug("Statistics collected successfully - CPU: {}, Memory: {}", 
                        stats.getCpuStats() != null ? "Available" : "NULL",
                        stats.getMemoryStats() != null ? "Available" : "NULL");
            }
            return stats;
        } catch (Exception e) {
            log.error("Error collecting statistics for containerId: {}", containerId, e);
            return null;
        }
    }

    private boolean isContainerHealthy(InspectContainerResponse containerInfo) {
        try {
            InspectContainerResponse.ContainerState state = containerInfo.getState();
            if (state == null) {
                return false;
            }
            HealthState health = state.getHealth();
            if (health == null) {
                return "running".equalsIgnoreCase(state.getStatus());
            }
            return "healthy".equalsIgnoreCase(health.getStatus());
        } catch (Exception e) {
            log.error("Error checking container health", e);
            return false;
        }
    }

    private Boolean isPortAccessible(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 1000);
            return true;
        } catch (Exception e) {
            log.debug("Port {} is not accessible on host {}", port, host);
            return false;
        }
    }
    /* 
     private List<Integer> getServicePorts(InspectContainerResponse containerInfo) {
        Ports ports = containerInfo.getNetworkSettings().getPorts();
        if (ports == null) {
            return Collections.emptyList();
        }
        
        return ports.getBindings().entrySet().stream()
            .flatMap(entry -> Arrays.stream(entry.getValue()))
            .map(binding -> Integer.parseInt(binding.getHostPortSpec()))
            .collect(Collectors.toList());
    } */

    private Integer getServicePort(InspectContainerResponse containerInfo) {
        try {
            Ports ports = containerInfo.getNetworkSettings().getPorts();
            if (ports == null) {
                return null;
            }
            
            Map<ExposedPort, Ports.Binding[]> bindings = ports.getBindings();
            if (bindings == null || bindings.isEmpty()) {
                return null;
            }
            
            return bindings.entrySet().stream()
                .flatMap(entry -> {
                    // ExposedPort exposedPort = entry.getKey();
                    Ports.Binding[] bindingsArray = entry.getValue();
                    return bindingsArray != null ? Arrays.stream(bindingsArray) : Stream.empty();
                })
                .filter(Objects::nonNull)
                .map(Ports.Binding::getHostPortSpec)
                .filter(Objects::nonNull)
                .map(Integer::parseInt)
                .findFirst()
                .orElse(null);
        } catch (Exception e) {
            log.error("Error getting service port", e);
            return null;
        }
    } 

    private String mapContainerStatus(String containerStatus) {
        if (containerStatus == null) {
            return "UNKNOWN";
        }
        switch (containerStatus.toLowerCase()) {
            case "running":
                return "RUNNING";
            case "exited":
                return "STOPPED";
            case "restarting":
                return "RESTARTING";
            default:
                return "UNKNOWN";
        }
    }

    private Double calculateCpuUsage(Statistics stats) {
        if (stats == null || stats.getCpuStats() == null || stats.getPreCpuStats() == null) {
            return null;
        }
        try {
            CpuStatsConfig cpuStats = stats.getCpuStats();
            CpuStatsConfig preCpuStats = stats.getPreCpuStats();
            CpuUsageConfig cpuUsage = cpuStats.getCpuUsage();
            CpuUsageConfig preCpuUsage = preCpuStats.getCpuUsage();
            
            if (cpuUsage == null || preCpuUsage == null || 
                cpuUsage.getTotalUsage() == null || preCpuUsage.getTotalUsage() == null ||
                cpuStats.getSystemCpuUsage() == null || preCpuStats.getSystemCpuUsage() == null ||
                cpuStats.getOnlineCpus() == null) {
                return null;
            }

            Long cpuDelta = cpuUsage.getTotalUsage() - preCpuUsage.getTotalUsage();
            Long systemDelta = cpuStats.getSystemCpuUsage() - preCpuStats.getSystemCpuUsage();
            Long cpuCount = cpuStats.getOnlineCpus();
        
            if (systemDelta > 0 && cpuCount > 0) {
                double cpuUsagePercent = (cpuDelta.doubleValue() / systemDelta.doubleValue()) * cpuCount * 100.0;
                return Math.round(cpuUsagePercent * 100.0) / 100.0;
            }
        } catch (Exception e) {
            log.error("Error calculating CPU usage", e);
        }
        return null;
    }
    
    private Double calculateMemoryUsage(Statistics stats) {
        if (stats == null || stats.getMemoryStats() == null) {
            return null;
        }
        try {
            Long usedMemory = stats.getMemoryStats().getUsage();
            Long totalMemory = stats.getMemoryStats().getLimit();
            if (usedMemory != null && totalMemory != null && totalMemory > 0) {
                double memoryUsage = (usedMemory.doubleValue() / totalMemory.doubleValue()) * 100.0;
                return Math.round(memoryUsage * 100.0) / 100.0;
            }
        } catch (Exception e) {
            log.error("Error calculating memory usage", e);
        }
        return null;
    }

    private Double calculateNetworkIn(Statistics stats) {
        if (stats == null || stats.getNetworks() == null) {
            return null;
        }
        try {
            return stats.getNetworks().values().stream()
                    .mapToDouble(net -> net.getRxBytes() != null ? net.getRxBytes().doubleValue() : 0.0)
                    .sum();
        } catch (Exception e) {
            log.error("Error calculating network in", e);
            return null;
        }
    }

    private Double calculateNetworkOut(Statistics stats) {
        if (stats == null || stats.getNetworks() == null) {
            return null;
        }
        try {
            return stats.getNetworks().values().stream()
                    .mapToDouble(net -> net.getTxBytes() != null ? net.getTxBytes().doubleValue() : 0.0)
                    .sum();
        } catch (Exception e) {
            log.error("Error calculating network out", e);
            return null;
        }
    }

    private static class StatsCallback extends ResultCallback.Adapter<Statistics> {
        private Statistics stats;

        @Override
        public void onNext(Statistics stats) {
            this.stats = stats;
        }

        public Statistics getStats() {
            return stats;
        }
    }
}