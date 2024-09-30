package kr.co.mcmp.ape.cbtumblebug.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "Location information")
public class Location {
    @ApiModelProperty(value = "Display name")
    private String display;

    @ApiModelProperty(value = "Latitude")
    private double latitude;

    @ApiModelProperty(value = "Longitude")
    private double longitude;
}
