package kr.co.mcmp.softwarecatalog.docker.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ContainerHealthInfo {
    private String status;
    private Integer servicePorts;
    private Boolean isPortAccess;
    /** CPU percent of the container's available capacity (metrics version 1). */
    private Double cpuUsage;
    /** Unmodified Docker percentage: one fully used CPU core is 100%. */
    private Double rawCpuUsage;
    private Double cpuCapacityCores;
    private Integer cpuMetricsVersion;
    private Double memoryUsage;
    private Long memoryBytes;
    private Long memoryLimitBytes;
    private Double networkIn;
    private Double networkOut;
    private Boolean isHealthCheck;
    private Boolean oomKilled;
    private Integer restartCount;
}
