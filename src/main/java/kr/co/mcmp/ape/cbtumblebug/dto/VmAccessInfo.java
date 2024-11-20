package kr.co.mcmp.ape.cbtumblebug.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
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
@ApiModel(description = "VM (Virtual Machine) information")
public class VmAccessInfo {
    @ApiModelProperty(value = "Resource type", example = "vm")
    private String resourceType;

    @ApiModelProperty(value = "VM ID", example = "vm01-1")
    private String id;

    @ApiModelProperty(value = "VM UID", example = "cstcna3ebd5s73bq2o3g")
    private String uid;

    @ApiModelProperty(value = "CSP Resource Name")
    private String cspResourceName;

    @ApiModelProperty(value = "CSP Resource ID")
    private String cspResourceId;

    @ApiModelProperty(value = "VM name", example = "vm01-1")
    private String name;

    @ApiModelProperty(value = "Sub group ID")
    private String subGroupId;

    @ApiModelProperty(value = "Location information")
    private Location location;

    @ApiModelProperty(value = "VM status", example = "Running")
    private String status;

    @ApiModelProperty(value = "Target status", example = "None")
    private String targetStatus;

    @ApiModelProperty(value = "Target action", example = "None")
    private String targetAction;

    @ApiModelProperty(value = "Monitoring agent status")
    private String monAgentStatus;

    @ApiModelProperty(value = "Network agent status")
    private String networkAgentStatus;

    @ApiModelProperty(value = "System message")
    private String systemMessage;

    @ApiModelProperty(value = "Created time")
    private String createdTime;

    @ApiModelProperty(value = "Label")
    private Map<String, String> label;

    @ApiModelProperty(value = "Description", example = "")
    private String description;

    @ApiModelProperty(value = "Region information")
    private Region region;

    @ApiModelProperty(value = "Public IP")
    private String publicIP;

    @ApiModelProperty(value = "SSH port")
    private String sshPort;

    @ApiModelProperty(value = "Public DNS")
    private String publicDNS;

    @ApiModelProperty(value = "Private IP")
    private String privateIP;

    @ApiModelProperty(value = "Private DNS")
    private String privateDNS;

    @ApiModelProperty(value = "Root disk type")
    private String rootDiskType;

    @ApiModelProperty(value = "Root disk size")
    private String rootDiskSize;

    @ApiModelProperty(value = "Root device name")
    private String rootDeviceName;

    @ApiModelProperty(value = "Connection name")
    private String connectionName;

    @ApiModelProperty(value = "Connection configuration")
    private ConnectionConfig connectionConfig;

    @ApiModelProperty(value = "Spec ID")
    private String specId;

    @ApiModelProperty(value = "CSP spec name")
    private String cspSpecName;

    @ApiModelProperty(value = "Image ID")
    private String imageId;

    @ApiModelProperty(value = "CSP image name")
    private String cspImageName;

    @ApiModelProperty(value = "VNet ID")
    private String vNetId;

    @ApiModelProperty(value = "CSP VNet ID")
    private String cspVNetId;

    @ApiModelProperty(value = "Subnet ID")
    private String subnetId;

    @ApiModelProperty(value = "CSP subnet ID")
    private String cspSubnetId;

    @ApiModelProperty(value = "Network interface")
    private String networkInterface;

    @ApiModelProperty(value = "Security group IDs")
    private List<String> securityGroupIds;

    @ApiModelProperty(value = "Data disk IDs")
    private List<String> dataDiskIds;

    @ApiModelProperty(value = "SSH key ID")
    private String sshKeyId;

    @ApiModelProperty(value = "CSP SSH key ID")
    private String cspSshKeyId;

    @ApiModelProperty(value = "VM user name")
    private String vmUserName;

    @ApiModelProperty(value = "Additional details")
    private List<AdditionalDetail> additionalDetails;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Location {
        @ApiModelProperty(value = "Display name")
        private String display;

        @ApiModelProperty(value = "Latitude")
        private Double latitude;

        @ApiModelProperty(value = "Longitude")
        private Double longitude;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Region {
        @JsonProperty("Region")
        @ApiModelProperty(value = "Region")
        private String region;

        @JsonProperty("Zone")
        @ApiModelProperty(value = "Zone")
        private String zone;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConnectionConfig {
        @ApiModelProperty(value = "Config name")
        private String configName;

        @ApiModelProperty(value = "Provider name")
        private String providerName;

        @ApiModelProperty(value = "Driver name")
        private String driverName;

        @ApiModelProperty(value = "Credential name")
        private String credentialName;

        @ApiModelProperty(value = "Credential holder")
        private String credentialHolder;

        @ApiModelProperty(value = "Region zone info name")
        private String regionZoneInfoName;

        @ApiModelProperty(value = "Region zone info")
        private RegionZoneInfo regionZoneInfo;

        @ApiModelProperty(value = "Region detail")
        private RegionDetail regionDetail;

        @ApiModelProperty(value = "Region representative")
        private Boolean regionRepresentative;

        @ApiModelProperty(value = "Verified")
        private Boolean verified;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegionZoneInfo {
        @ApiModelProperty(value = "Assigned region")
        private String assignedRegion;

        @ApiModelProperty(value = "Assigned zone")
        private String assignedZone;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegionDetail {
        @ApiModelProperty(value = "Region ID")
        private String regionId;

        @ApiModelProperty(value = "Region name")
        private String regionName;

        @ApiModelProperty(value = "Description")
        private String description;

        @ApiModelProperty(value = "Location")
        private Location location;

        @ApiModelProperty(value = "Zones")
        private List<String> zones;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdditionalDetail {
        @ApiModelProperty(value = "Key")
        private String key;

        @ApiModelProperty(value = "Value")
        private String value;
    }
}