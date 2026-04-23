package kr.co.mcmp.ape.cbtumblebug.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Location information")
public class Location {
    @Schema(description = "Display name")
    private String display;

    @Schema(description = "Latitude")
    private double latitude;

    @Schema(description = "Longitude")
    private double longitude;
}
