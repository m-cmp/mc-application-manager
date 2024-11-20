package kr.co.mcmp.softwarecatalog.docker.model;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ContainerHealthInfo {
    private String status;
    private Integer servicePorts;
    private Boolean isPortAccess;
    private Double cpuUsage;
    private Double memoryUsage;
    private Double networkIn;
    private Double networkOut;
    private Boolean isHealthCheck;
}
