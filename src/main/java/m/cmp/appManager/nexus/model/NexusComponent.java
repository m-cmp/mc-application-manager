package m.cmp.appManager.nexus.model;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Tag(name = "NexusComponent", description = "Nexus Components")
@JsonIgnoreProperties(ignoreUnknown=true)
public class NexusComponent implements Serializable {

	private static final long serialVersionUID = 6695416538557612577L;

    private String id;
    private String repository;
    private String format;
    private String name;
    private String version;
}
