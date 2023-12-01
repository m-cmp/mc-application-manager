package m.cmp.appManager.common.model;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Tag(name = "Common", description = "Common")
public class CommonCode {
    
	private String commonGroupCd;
	private String commonCd;
	private String codeName;
	private String codeDesc;
	private int codeOrder;

	private String protectedYn;		// 삭제 가능 여부 ('Y' - 삭제 불가 / 'N' - 삭제 가능)

	private String regId;
	private String regName;
	private String regDate;
	private String modId;
	private String modName;
	private String modDate;
}
