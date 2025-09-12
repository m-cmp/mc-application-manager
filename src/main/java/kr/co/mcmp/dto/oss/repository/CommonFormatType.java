package kr.co.mcmp.dto.oss.repository;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CommonFormatType {

    @Schema(title = "Format type", example = "raw, docker, helm")
    private String format;

    @Schema(title = "Type", example = "hosted")
    private String type;
}
