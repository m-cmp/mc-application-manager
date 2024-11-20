package kr.co.mcmp.softwarecatalog.application.dto;

import kr.co.mcmp.softwarecatalog.application.constants.ScriptFormat;
import kr.co.mcmp.softwarecatalog.application.constants.ScriptType;
import kr.co.mcmp.softwarecatalog.application.model.ScriptDefinition;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ScriptDefinitionDTO {
    private Long id;
    private Long catalogId;
    private ScriptType scriptType;
    private String scriptContent;
    private ScriptFormat scriptFormat;

    public ScriptDefinitionDTO(ScriptDefinition entity) {
        this.id = entity.getId();
        this.catalogId = entity.getCatalog().getId();
        this.scriptType = entity.getScriptType();
        this.scriptContent = entity.getScriptContent();
        this.scriptFormat = entity.getScriptFormat();
    }
}
