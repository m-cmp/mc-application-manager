package kr.co.mcmp.ape.cbtumblebug.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Schema(description = "VM (Virtual Machine) information")
public class VmAccessInfo {
    @Schema(description = "Resource type", example = "vm")
    private String resourceType;

    @Schema(description = "VM ID", example = "vm01-1")
    private String id;

    @Schema(description = "VM UID", example = "cstcna3ebd5s73bq2o3g")
    private String uid;

    @Schema(description = "CSP Resource Name")
    private String cspResourceName;

    @Schema(description = "CSP Resource ID")
    private String cspResourceId;

    @Schema(description = "VM name", example = "vm01-1")
    private String name;

    @Schema(description = "Sub group ID")
    private String subGroupId;

    @Schema(description = "Location information")
    private Location location;

    @Schema(description = "VM status", example = "Running")
    private String status;

    @Schema(description = "Target status", example = "None")
    private String targetStatus;

    @Schema(description = "Target action", example = "None")
    private String targetAction;

    @Schema(description = "Monitoring agent status")
    private String monAgentStatus;

    @Schema(description = "Network agent status")
    private String networkAgentStatus;

    @Schema(description = "System message")
    private String systemMessage;

    @Schema(description = "Created time")
    private String createdTime;

    @Schema(description = "Label")
    private Map<String, String> label;

    @Schema(description = "Description", example = "")
    private String description;

    @Schema(description = "Region information")
    private Region region;

    @Schema(description = "Public IP")
    private String publicIP;

    @Schema(description = "SSH port")
    private String sshPort;

    @Schema(description = "Public DNS")
    private String publicDNS;

    @Schema(description = "Private IP")
    private String privateIP;

    @Schema(description = "Private DNS")
    private String privateDNS;

    @Schema(description = "Root disk type")
    private String rootDiskType;

    @Schema(description = "Root disk size")
    private String rootDiskSize;

    @Schema(description = "Root device name")
    private String rootDeviceName;

    @Schema(description = "Connection name")
    private String connectionName;

    @Schema(description = "Connection configuration")
    private ConnectionConfig connectionConfig;

    @Schema(description = "Spec ID")
    private String specId;

    @Schema(description = "CSP spec name")
    private String cspSpecName;

    @Schema(description = "Image ID")
    private String imageId;

    @Schema(description = "CSP image name")
    private String cspImageName;

    @Schema(description = "VNet ID")
    private String vNetId;

    @Schema(description = "CSP VNet ID")
    private String cspVNetId;

    @Schema(description = "Subnet ID")
    private String subnetId;

    @Schema(description = "CSP subnet ID")
    private String cspSubnetId;

    @Schema(description = "Network interface")
    private String networkInterface;

    @Schema(description = "Security group IDs")
    private List<String> securityGroupIds;

    @Schema(description = "Data disk IDs")
    private List<String> dataDiskIds;

    @Schema(description = "SSH key ID")
    private String sshKeyId;

    @Schema(description = "CSP SSH key ID")
    private String cspSshKeyId;

    @Schema(description = "VM user name")
    private String vmUserName;

    @Schema(description = "Additional details")
    private List<AdditionalDetail> additionalDetails;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Location {
        @Schema(description = "Display name")
        private String display;

        @Schema(description = "Latitude")
        private Double latitude;

        @Schema(description = "Longitude")
        private Double longitude;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Region {
        @JsonProperty("Region")
        @Schema(description = "Region")
        private String region;

        @JsonProperty("Zone")
        @Schema(description = "Zone")
        private String zone;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConnectionConfig {
        @Schema(description = "Config name")
        private String configName;

        @Schema(description = "Provider name")
        private String providerName;

        @Schema(description = "Driver name")
        private String driverName;

        @Schema(description = "Credential name")
        private String credentialName;

        @Schema(description = "Credential holder")
        private String credentialHolder;

        @Schema(description = "Region zone info name")
        private String regionZoneInfoName;

        @Schema(description = "Region zone info")
        private RegionZoneInfo regionZoneInfo;

        @Schema(description = "Region detail")
        private RegionDetail regionDetail;

        @Schema(description = "Region representative")
        private Boolean regionRepresentative;

        @Schema(description = "Verified")
        private Boolean verified;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegionZoneInfo {
        @Schema(description = "Assigned region")
        private String assignedRegion;

        @Schema(description = "Assigned zone")
        private String assignedZone;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegionDetail {
        @Schema(description = "Region ID")
        private String regionId;

        @Schema(description = "Region name")
        private String regionName;

        @Schema(description = "Description")
        private String description;

        @Schema(description = "Location")
        private Location location;

        @Schema(description = "Zones")
        private List<String> zones;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdditionalDetail {
        @Schema(description = "Key")
        private String key;

        @Schema(description = "Value")
        private String value;
    }
}