package kr.co.mcmp.ape.cbtumblebug.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Region and Zone Information")
public class RegionZoneInfo {
    @Schema(description = "Assigned region")
    private String assignedRegion;

    @Schema(description = "Assigned zone")
    private String assignedZone;
}