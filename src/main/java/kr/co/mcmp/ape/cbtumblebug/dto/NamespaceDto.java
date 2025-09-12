package kr.co.mcmp.ape.cbtumblebug.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Namespace information")
public class NamespaceDto {

    @Schema(description = "Namespace ID", example = "default")
    private String id;

    @Schema(description = "Namespace name", example = "default")
    private String name;

    @Schema(description = "Namespace description", example = "Default Namespace")
    private String description;
    
}
