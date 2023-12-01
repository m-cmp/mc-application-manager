package m.cmp.appManager.common.service;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import m.cmp.appManager.api.response.ResponseCode;
import m.cmp.appManager.common.mapper.CommonMapper;
import m.cmp.appManager.common.model.CommonCode;
import m.cmp.appManager.exception.McmpException;


@Service
public class CommonService {
	
	@Autowired
	private CommonMapper commonMapper;

	/**
	 * 공통 코드 목록
	 * @param commonGroupCd
	 * @return
	 */
	public List<CommonCode> getCommonCodeList(String commonGroupCd) {
		return commonMapper.selectCommonCodeList(commonGroupCd);
	}
	
	/**
	 * 공통 코드 조회
	 * @param commonGroupCd
	 * @param commonCd
	 * @return
	 */
	public CommonCode getCommonCode(String commonGroupCd, String commonCd) {
		return commonMapper.selectCommonCode(commonGroupCd, commonCd);
	}

	/**
	 * 공통 코드 등록
	 * @param code
	 * @return
	 */
	public String createCommonCode(CommonCode code) {
		if ( StringUtils.isBlank(code.getCommonCd()) ) {
			code.setCommonCd(code.getCodeName().toUpperCase().replaceAll(" ", "_"));
		}
		
		// 중복 체크
		CommonCode duplicatedCode = getCommonCode(code.getCommonGroupCd(), code.getCommonCd());
		if ( duplicatedCode != null ) {
			throw new McmpException(ResponseCode.COMMON_CODE_EXISTS);
		}
		
		code.setProtectedYn("N");
		commonMapper.insertCommonCode(code);
		
		return code.getCommonCd();
	}
	
	/**
	 * 공통 코드 삭제
	 * @param commonGroupCd
	 * @param commonCd
	 * @return
	 */
	public int deleteCommonCode(String commonGroupCd, String commonCd) {
		// protected_yn = 'Y'인 코드는 삭제할 수 없음
		CommonCode code = getCommonCode(commonGroupCd, commonCd);
		if ( code != null && StringUtils.equals("Y", code.getProtectedYn()) ) {
			throw new McmpException(ResponseCode.COMMON_CODE_DELETE_NOT_ALLOWED);
		}
		
		return commonMapper.deleteCommonCode(commonGroupCd, commonCd);
	}
}
