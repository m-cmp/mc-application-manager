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

    @Schema(title = "포맷 유형", example = "raw, docker, helm")
    private String format;

    @Schema(title = "타입 유형", example = "hosted")
    private String type;
}
