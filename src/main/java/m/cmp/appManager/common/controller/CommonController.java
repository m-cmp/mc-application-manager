package m.cmp.appManager.common.controller;

import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import m.cmp.appManager.api.response.ResponseCode;
import m.cmp.appManager.api.response.ResponseWrapper;
import m.cmp.appManager.common.model.CommonCode;
import m.cmp.appManager.common.service.CommonService;


@Tag(name = "공통코드", description = "공통코드 조회")
@RestController
@RequestMapping("/common")
public class CommonController {
	
	@Autowired
	private CommonService commonService;
	
	@Operation(summary = "공통코드 목록 조회", description = "")
	@GetMapping("/group/{commonGroupCd}")
	public ResponseWrapper<List<CommonCode>> getCommonCodeList(@PathVariable String commonGroupCd) {
		List<CommonCode> data = commonService.getCommonCodeList(commonGroupCd);
		return new ResponseWrapper<>(data);
	}

	@Operation(summary = "공통코드 추가", description = "")
	@PostMapping("/group/{commonGroupCd}/code")
	public ResponseWrapper<String> createCommonCode(@PathVariable String commonGroupCd, @RequestBody CommonCode code) {
    	if ( StringUtils.isBlank(commonGroupCd) ) {
    		return new ResponseWrapper<>(ResponseCode.BAD_REQUEST, "commonGroupCd"); 
    	}
    	
		if ( StringUtils.isBlank(code.getCodeName()) ) {
    		return new ResponseWrapper<>(ResponseCode.BAD_REQUEST, "codeName"); 
		}
		else {
			if ( !Pattern.matches("[a-zA-Z0-9\' \']*", code.getCodeName()) ) {
	    		return new ResponseWrapper<>(ResponseCode.BAD_REQUEST, "codeName은 영문/숫자/공백만 가능합니다."); 
			}
		}
		
		if ( StringUtils.isBlank(code.getCommonGroupCd()) ) {
			code.setCommonGroupCd(commonGroupCd);
		}

    	code.setRegId("admin");
    	code.setRegName("admin");
		
		return new ResponseWrapper<>(commonService.createCommonCode(code));
	}
	
	@Operation(summary = "공통코드 삭제", description = "")
	@DeleteMapping("/group/{commonGroupCd}/code/{commonCd}")
	public ResponseWrapper<Integer> deleteCommonCode(@PathVariable String commonGroupCd, @PathVariable String commonCd) {
    	if ( StringUtils.isBlank(commonGroupCd) ) {
    		return new ResponseWrapper<>(ResponseCode.BAD_REQUEST, "commonGroupCd"); 
    	}

    	if ( StringUtils.isBlank(commonCd) ) {
    		return new ResponseWrapper<>(ResponseCode.BAD_REQUEST, "commonCd"); 
    	}
    	
		return new ResponseWrapper<>(commonService.deleteCommonCode(commonGroupCd, commonCd));
	}
}
