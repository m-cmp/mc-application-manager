package kr.co.mcmp.ape.cbtumblebug.dto;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "MCI (Multi-Cloud Infrastructure) information")
public class MciDto {
    
    @Schema(description = "Resource type", example = "mci")
    private String resourceType;

    @Schema(description = "MCI ID", example = "mci01")
    private String id;

    @Schema(description = "MCI UID", example = "crr3fq5n7lsc739fdq0g")
    private String uid;

    @Schema(description = "MCI name", example = "mci01")
    private String name;

    @Schema(description = "MCI status", example = "Creating:3 (R:0/3)")
    private String status;

    @Schema(description = "Status count")
    private StatusCount statusCount;

    @Schema(description = "Target status", example = "Running")
    private String targetStatus;

    @Schema(description = "Target action", example = "Create")
    private String targetAction;

    @Schema(description = "Install monitoring agent", example = "no")
    private String installMonAgent;

    @Schema(description = "Configure cloud adaptive network")
    private String configureCloudAdaptiveNetwork;

    @Schema(description = "Label")
    private Map<String, String> label;

    @Schema(description = "System label")
    private String systemLabel;

    @Schema(description = "System message")
    private String systemMessage;

    @Schema(description = "Description", example = "Made in CB-TB")
    private String description;

    @Schema(description = "List of VMs")
    private List<VmAccessInfo> vm;

    @Schema(description = "List of new VMs")
    private List<VmAccessInfo> newVmList;

    
}
