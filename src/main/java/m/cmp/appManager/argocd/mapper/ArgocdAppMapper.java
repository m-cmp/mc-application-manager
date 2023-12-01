package m.cmp.appManager.argocd.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import m.cmp.appManager.argocd.model.ArgocdApp;

@Mapper
public interface ArgocdAppMapper {


    int insertArgocdApp(ArgocdApp appInfo);

    ArgocdApp selectArgocdApp(@Param("deployId") int deployId);

    int deleteArgocdApp(int deployId);
    
    // 카탈로그 배포 > ArgoCd Application 정보 조회
    ArgocdApp selectCatalogArgocdApplication(int catalogDeployId);
	
	// 카탈로그 배포 > ArgoCd Application 정보 등록
	int insertCatalogArgocdApplication(ArgocdApp argocdApp);
	
	// 카탈로그 배포 > ArgoCd Application 정보 삭제
	int deleteCatalogArgocdApplication(int catalogDeployId);
}
