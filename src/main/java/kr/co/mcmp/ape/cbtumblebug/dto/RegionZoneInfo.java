package kr.co.mcmp.ape.cbtumblebug.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "Region and Zone Information")
public class RegionZoneInfo {
    @ApiModelProperty(value = "Assigned region")
    private String assignedRegion;

    @ApiModelProperty(value = "Assigned zone")
    private String assignedZone;
}