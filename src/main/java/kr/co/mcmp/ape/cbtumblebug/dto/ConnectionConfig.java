package kr.co.mcmp.ape.cbtumblebug.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Connection configuration")
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConnectionConfig {
    @Schema(description = "Config name")
    private String configName;

    @Schema(description = "Provider name")
    private String providerName;

    @Schema(description = "Driver name")
    private String driverName;

    @Schema(description = "Credential name")
    private String credentialName;

    @Schema(description = "Credential holder")
    private String credentialHolder;

    @Schema(description = "Region zone info name")
    private String regionZoneInfoName;

    @Schema(description = "Region zone info")
    private RegionZoneInfo regionZoneInfo;

    @Schema(description = "Region detail")
    private RegionDetail regionDetail;

    @Schema(description = "Is region representative")
    private boolean regionRepresentative;

    @Schema(description = "Is verified")
    private boolean verified;
}