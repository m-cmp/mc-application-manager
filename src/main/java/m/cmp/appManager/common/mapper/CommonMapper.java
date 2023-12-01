package m.cmp.appManager.common.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import m.cmp.appManager.common.model.CommonCode;


@Mapper
public interface CommonMapper {
	
	// 공통 코드 목록
	List<CommonCode> selectCommonCodeList(String commonGroupCd);

	// 공통 코드 상세 정보
	CommonCode selectCommonCode(@Param("commonGroupCd") String commonGroupCd, @Param("commonCd") String commonCd);
	
	// 공통 코드 등록
	int insertCommonCode(CommonCode code);
	
	// 공통 코드 삭제
	int deleteCommonCode(@Param("commonGroupCd") String commonGroupCd, @Param("commonCd") String commonCd);
}
