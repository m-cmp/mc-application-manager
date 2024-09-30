package kr.co.mcmp.ape.cbtumblebug.dto;

import java.util.List;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "Detailed Region Information")
public class RegionDetail {
    @ApiModelProperty(value = "Region ID")
    private String regionId;

    @ApiModelProperty(value = "Region name")
    private String regionName;

    @ApiModelProperty(value = "Region description")
    private String description;

    @ApiModelProperty(value = "Region location")
    private Location location;

    @ApiModelProperty(value = "List of zones in the region")
    private List<String> zones;
}