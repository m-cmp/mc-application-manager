package m.cmp.appManager.oss.model;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Tag(name = "Oss", description = "Oss Config (GITLAB, JENKINS...)")
public class Oss implements Serializable {

	private static final long serialVersionUID = 8730409824632948159L;

	private Integer ossId;
	private String ossCd;
	private String ossName;
	private String ossDesc;
	private String ossUrl;
	private String ossUsername;
	private String ossPassword;
	private String ossToken;
	
	private String regId;
	private String regName;
	private String regDate;
	private String modId;
	private String modName;
	private String modDate;
	
}
