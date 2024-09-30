package kr.co.mcmp.ape.cbtumblebug.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "Region information")
public class Region {
    @ApiModelProperty(value = "Region")
    private String Region;

    @ApiModelProperty(value = "Zone")
    private String Zone;
}


