package kr.co.mcmp.ape.cbtumblebug.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Schema(description = "Region information")
public class Region {
    @Schema(description = "Region")
    private String region;

    @Schema(description = "Zone")
    private String zone;
}


