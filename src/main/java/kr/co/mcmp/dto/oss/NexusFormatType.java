package kr.co.mcmp.dto.oss;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NexusFormatType {

    @Schema(title = "레포지토리 포맷 유형")
    private String format;

    @Schema(title = "레포지토리 타입 유형")
    private String type;
}
