package m.cmp.appManager.catalog.model;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Tag(name = "Catalog", description = "카탈로그 정보")
public class Catalog implements Serializable {

	private static final long serialVersionUID = -3309532675890711400L;

	private Integer catalogId;
	private String	catalogName;
	private String	catalogVersion;
	private String 	catalogTypeCd;
    private String  regId;
    private String  regName;
    private String  regDate;
    private String  modId;
    private String  modName;
    private String  modDate;
}
