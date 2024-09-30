package kr.co.mcmp.ape.cbtumblebug.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "Connection configuration")
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConnectionConfig {
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

    @ApiModelProperty(value = "Is region representative")
    private boolean regionRepresentative;

    @ApiModelProperty(value = "Is verified")
    private boolean verified;
}