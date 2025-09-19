package kr.co.mcmp.softwarecatalog.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "SelectBox option type")
public enum SelectBoxType {
	reason,
	restart_reason,
	stop_reason,
	category;
}
