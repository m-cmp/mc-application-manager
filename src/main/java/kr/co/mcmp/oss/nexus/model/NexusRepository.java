package kr.co.mcmp.oss.nexus.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@Tag(name = "NexusRepository", description = "넥서스 Repository 객체")
@JsonIgnoreProperties
public class NexusRepository implements Serializable {

	private static final long serialVersionUID = 972782900597914206L;

    private String id;
    private String format;
    private String type;
    private String url;
}
