package kr.co.mcmp.ape.cbtumblebug.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "네임스페이스 정보")
public class NamespaceDto {

    @Schema(description = "네임스페이스 ID", example = "default")
    private String id;

    @Schema(description = "네임스페이스 이름", example = "default")
    private String name;

    @Schema(description = "네임스페이스 설명", example = "Default Namespace")
    private String description;
    
}
