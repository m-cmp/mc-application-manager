package kr.co.mcmp.ape.cbtumblebug.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "MCI Response")
public class MciResponse {

    @Schema(description = "List of MCIs")
    private List<MciDto> infra;
}
