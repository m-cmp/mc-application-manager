package kr.co.mcmp.softwarecatalog.controller;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.co.mcmp.response.ResponseWrapper;
import kr.co.mcmp.softwarecatalog.dto.SelectBoxOptionDTO;
import kr.co.mcmp.softwarecatalog.enums.LabeledEnum;
import kr.co.mcmp.softwarecatalog.enums.RatingCategoryType;
import kr.co.mcmp.softwarecatalog.enums.ReasonType;
import kr.co.mcmp.softwarecatalog.enums.RestartReasonType;
import kr.co.mcmp.softwarecatalog.enums.SelectBoxType;
import kr.co.mcmp.softwarecatalog.enums.StopReasonType;

@Tag(name = "SelectBox Options", description = "SelectBox option retrieval API")
@RestController
@RequestMapping("/catalog/selectbox")
public class SelectBoxController {

	@Operation(summary = "Get SelectBox Options", description = "Retrieve SelectBox option list by type.")
	@GetMapping("/options")
	public ResponseEntity<ResponseWrapper<List<SelectBoxOptionDTO>>> getSelectBoxOptions(
			@Parameter(description = "SelectBox type", required = true)
			@RequestParam SelectBoxType type) {

		List<SelectBoxOptionDTO> options = new ArrayList<>();

		switch (type) {
			case UNINSTALL:
				options = mapEnumToOptions(ReasonType.values());
				break;
			case RESTART:
				options = mapEnumToOptions(RestartReasonType.values());
				break;
			case STOP:
				options = mapEnumToOptions(StopReasonType.values());
				break;
			case category:
				options = mapEnumToOptions(RatingCategoryType.values());
				break;
			default:
				options = new ArrayList<>();
		}

		return ResponseEntity.ok(new ResponseWrapper<>(options));
	}

	private List<SelectBoxOptionDTO> mapEnumToOptions(LabeledEnum[] labeledEnums) {
		List<SelectBoxOptionDTO> list = new ArrayList<>();
		Arrays.stream(labeledEnums).forEach(e -> {
			SelectBoxOptionDTO dto = new SelectBoxOptionDTO();
			dto.setValue(e.getValue());
			dto.setLabel(e.getLabel());
			dto.setDescription(e.getDescription());
			list.add(dto);
		});
		return list;
	}
}