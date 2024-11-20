package kr.co.mcmp.ape.cbtumblebug.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
@ApiModel(description = "Region information")
public class Region {
    @ApiModelProperty(value = "Region")
    private String region;

    @ApiModelProperty(value = "Zone")
    private String zone;
}


