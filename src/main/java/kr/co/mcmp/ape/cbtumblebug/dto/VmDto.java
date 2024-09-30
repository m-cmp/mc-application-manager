package kr.co.mcmp.ape.cbtumblebug.dto;

import java.util.List;
import java.util.Map;

import org.apache.http.config.ConnectionConfig;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
// @JsonIgnoreProperties(ignoreUnknown = true)
@ApiModel(description = "VM (Virtual Machine) information")
public class VmDto {
    @ApiModelProperty(value = "Resource type", example = "mci")
    private String resourceType;

    @ApiModelProperty(value = "VM ID", example = "g1-1-3")
    private String id;

    @ApiModelProperty(value = "VM UID", example = "crr3fq5n7lsc739fdq0g")
    private String uid;

    @ApiModelProperty(value = "VM name", example = "mci01")
    private String name;

    @ApiModelProperty(value = "Sub group ID")
    private String subGroupId;

    @ApiModelProperty(value = "Location information")
    private Location location;

    @ApiModelProperty(value = "VM status", example = "Creating")
    private String status;

    @ApiModelProperty(value = "Target status", example = "Running")
    private String targetStatus;

    @ApiModelProperty(value = "Target action", example = "Create")
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

    @ApiModelProperty(value = "Description", example = "Made in CB-TB")
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

    // @ApiModelProperty(value = "Connection configuration")
    // private ConnectionConfig connectionConfig;

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
}