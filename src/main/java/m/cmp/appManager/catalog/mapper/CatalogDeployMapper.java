package m.cmp.appManager.catalog.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import m.cmp.appManager.catalog.model.CatalogDeploy;
import m.cmp.appManager.catalog.model.CatalogDeployHistory;

@Mapper
public interface CatalogDeployMapper {

	// 카탈로그 배포 목록 조회
	List<CatalogDeploy> selectCatalogDeployList(CatalogDeploy catalogDeploy);
	
	// 카탈로그 배포 정보 조회
	CatalogDeploy selectCatalogDeploy(int catalogDeployId);
	
	// 카탈로그 배포 > 배포명 중복 체크
	boolean isCatalogDeployNameDuplicated(@Param("k8sId") int k8sId, @Param("deployName") String deployName);
	
	// 카탈로그 명 별 등록 정보 조회
	List<CatalogDeploy> selectCatalogDeployByCatalogName(CatalogDeploy catalogDeploy);
	
	// 카탈로그 배포 등록
	int insertCatalogDeploy(CatalogDeploy catalogDeploy);
	
	// 카탈로그 배포 수정
	int updateCatalogDeploy(CatalogDeploy catalogDeploy);
	
	// 카탈로그 배포 삭제
	int deleteCatalogDeploy(int catalogDeployId);
	
	// 카탈로그 배포 이력 조회 
	List<CatalogDeployHistory> selectCatalogDeployHistoryList(int catalogDeployId);
	
	// 카탈로그 배포 이력 등록
	int insertCatalogDeployHistory(CatalogDeployHistory catalogDeployHistory);
	
	// 카탈로그 배포 이력 결과 등록 
	int updateCatalogDeployHistory(CatalogDeployHistory catalogDeployHistory);
	
	// 카탈로그 배포 이력 삭제 
	int deleteCatalogDeployHistory(int catalogDeployId);
}
