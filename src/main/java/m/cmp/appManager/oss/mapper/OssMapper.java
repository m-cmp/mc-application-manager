package m.cmp.appManager.oss.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import m.cmp.appManager.oss.model.Oss;

@Mapper
public interface OssMapper {

	List<Oss> selectOssList(@Param("ossCd") String ossCd);
	
	Oss selectOss(@Param("ossId") int ossId);
	
	boolean isOssInfoDuplicated(Oss oss);
	
	int insertOss(Oss oss);
	
	void updateOss(Oss oss);
	
	void deleteOss(@Param("ossId") int ossId);
}
