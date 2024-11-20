package kr.co.mcmp.ape.cbtumblebug.dto;

import java.util.List;
import java.util.Map;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "MCI (Multi-Cloud Infrastructure) information")
public class MciDto {
    
    @ApiModelProperty(value = "Resource type", example = "mci")
    private String resourceType;

    @ApiModelProperty(value = "MCI ID", example = "mci01")
    private String id;

    @ApiModelProperty(value = "MCI UID", example = "crr3fq5n7lsc739fdq0g")
    private String uid;

    @ApiModelProperty(value = "MCI name", example = "mci01")
    private String name;

    @ApiModelProperty(value = "MCI status", example = "Creating:3 (R:0/3)")
    private String status;

    @ApiModelProperty(value = "Status count")
    private StatusCount statusCount;

    @ApiModelProperty(value = "Target status", example = "Running")
    private String targetStatus;

    @ApiModelProperty(value = "Target action", example = "Create")
    private String targetAction;

    @ApiModelProperty(value = "Install monitoring agent", example = "no")
    private String installMonAgent;

    @ApiModelProperty(value = "Configure cloud adaptive network")
    private String configureCloudAdaptiveNetwork;

    @ApiModelProperty(value = "Label")
    private Map<String, String> label;

    @ApiModelProperty(value = "System label")
    private String systemLabel;

    @ApiModelProperty(value = "System message")
    private String systemMessage;

    @ApiModelProperty(value = "Description", example = "Made in CB-TB")
    private String description;

    @ApiModelProperty(value = "List of VMs")
    private List<VmAccessInfo> vm;

    @ApiModelProperty(value = "List of new VMs")
    private List<VmAccessInfo> newVmList;

    
}
