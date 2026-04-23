package kr.co.mcmp.ape.cbtumblebug.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Detailed Region Information")
public class RegionDetail {
    @Schema(description = "Region ID")
    private String regionId;

    @Schema(description = "Region name")
    private String regionName;

    @Schema(description = "Region description")
    private String description;

    @Schema(description = "Region location")
    private Location location;

    @Schema(description = "List of zones in the region")
    private List<String> zones;
}