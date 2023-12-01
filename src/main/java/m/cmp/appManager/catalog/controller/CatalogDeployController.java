package m.cmp.appManager.catalog.controller;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import m.cmp.appManager.api.response.ResponseCode;
import m.cmp.appManager.api.response.ResponseWrapper;
import m.cmp.appManager.catalog.model.CatalogDeploy;
import m.cmp.appManager.catalog.service.CatalogDeployService;

@Tag(name = "CatalogDeploy", description = "카탈로그 배포 관리")
@RestController
public class CatalogDeployController {

	@Autowired
	private CatalogDeployService catalogDeployService;

    @Operation(summary="카탈로그 배포 목록 조회")
    @PostMapping("/catalog/deploy/list")
	public ResponseWrapper<List<CatalogDeploy>> getCatalogDeployList(@RequestBody CatalogDeploy catalogDeploy) {
		return new ResponseWrapper<>(catalogDeployService.getCatalogDeployList(catalogDeploy));
	}

    @Operation(summary="카탈로그 배포 상세 조회")
    @GetMapping("/catalog/deploy/{catalogDeployId}")
    public ResponseWrapper<CatalogDeploy> getCatalogDeploy(@PathVariable int catalogDeployId) {
    	return new ResponseWrapper<>(catalogDeployService.getCatalogDeploy(catalogDeployId));
    }
	
    @Operation(summary="카탈로그 배포 설정 조회 > values.yaml 파일 조회") 
    @PostMapping("/catalog/deploy/values")
    public ResponseWrapper<String> getValuesYaml(@RequestBody CatalogDeploy catalogDeploy) {
    	if ( catalogDeploy.getK8sId() == null || catalogDeploy.getK8sId() == 0 ) {
    		return new ResponseWrapper<>(ResponseCode.BAD_REQUEST, "k8sId"); 
    	}
    	else if ( StringUtils.isBlank(catalogDeploy.getDeployName()) ) {
    		return new ResponseWrapper<>(ResponseCode.BAD_REQUEST, "deployName"); 
    	}
    	else if ( StringUtils.isBlank(catalogDeploy.getCatalogName()) ) {
    		return new ResponseWrapper<>(ResponseCode.BAD_REQUEST, "catalogName"); 
    	}
    	else if ( StringUtils.isBlank(catalogDeploy.getCatalogVersion()) ) {
    		return new ResponseWrapper<>(ResponseCode.BAD_REQUEST, "catalogVersion"); 
    	}

    	return new ResponseWrapper<>(catalogDeployService.getValuesYaml(catalogDeploy));
    }
	
    @Operation(summary="카탈로그 배포명 중복 체크", description="true : 중복 / false : 중복 아님")
    @GetMapping("/catalog/deploy/name/duplicate")
    public ResponseWrapper<Boolean> isCatalogDeployNameDuplicated(@RequestParam int k8sId, @RequestParam String deployName) {
    	if ( k8sId == 0 || StringUtils.isBlank(deployName) ) {
    		return new ResponseWrapper<>(ResponseCode.BAD_REQUEST); 
    	}
    	
    	return new ResponseWrapper<>(catalogDeployService.isCatalogDeployNameDuplicated(k8sId, deployName));
    }
	
    @Operation(summary="카탈로그 배포 정보 복사")
    @PostMapping("/catalog/deploy/copy")
    public ResponseWrapper<List<CatalogDeploy>> getCatalogDeployByCatalogName(@RequestBody CatalogDeploy catalogDeploy) {
    	if ( StringUtils.isBlank(catalogDeploy.getCatalogName()) ) {
    		return new ResponseWrapper<>(ResponseCode.BAD_REQUEST); 
    	}
    	
    	return new ResponseWrapper<>(catalogDeployService.getCatalogDeployByCatalogName(catalogDeploy));
    }
	
    @Operation(summary="카탈로그 배포 등록")
    @PostMapping("/catalog/deploy")
    public ResponseWrapper<Integer> createCatalogDeploy(@RequestBody CatalogDeploy catalogDeploy) {
    	if ( catalogDeploy.getK8sId() == null || catalogDeploy.getK8sId() == 0 ) {
    		return new ResponseWrapper<>(ResponseCode.BAD_REQUEST, "k8sId"); 
    	}
    	else if ( StringUtils.isBlank(catalogDeploy.getCatalogName())) {
    		return new ResponseWrapper<>(ResponseCode.BAD_REQUEST, "catalogName"); 
    	}
    	else if ( StringUtils.isBlank(catalogDeploy.getCatalogVersion()) ) {
    		return new ResponseWrapper<>(ResponseCode.BAD_REQUEST, "catalogVersion"); 
    	}
    	else if ( StringUtils.isBlank(catalogDeploy.getDeployName()) ) {
    		return new ResponseWrapper<>(ResponseCode.BAD_REQUEST, "deployName"); 
    	}
    	
    	catalogDeploy.setRegId("admin");
    	catalogDeploy.setRegName("admin");
    	
    	return new ResponseWrapper<>(catalogDeployService.createCatalogDeploy(catalogDeploy));
    }
	
    @Operation(summary="카탈로그 배포 수정")
    @PutMapping("/catalog/deploy/{catalogDeployId}")
    public ResponseWrapper<Integer> upodateCatalogDeploy(@PathVariable int catalogDeployId, @RequestBody CatalogDeploy catalogDeploy) {
    	if ( StringUtils.isBlank(catalogDeploy.getCatalogDeployYaml()) ) {
    		return new ResponseWrapper<>(ResponseCode.BAD_REQUEST, "catalogDeployYaml"); 
    	}
    	
    	if ( catalogDeploy.getCatalogDeployId() == null || catalogDeploy.getCatalogDeployId() == 0 ) {
    		catalogDeploy.setCatalogDeployId(catalogDeployId);
    	}

    	catalogDeploy.setModId("admin");
    	catalogDeploy.setModName("admin");
    	
    	return new ResponseWrapper<>(catalogDeployService.upodateCatalogDeploy(catalogDeploy));
    }
	
    @Operation(summary="카탈로그 배포 실행") 
    @GetMapping("/catalog/deploy/{catalogDeployId}/run")
    public ResponseWrapper<String> runCatalogDeploy(@PathVariable int catalogDeployId) {
    	return new ResponseWrapper<>(catalogDeployService.runCatalogDeploy(catalogDeployId, "admin", "admin"));
    }
	 
    @Operation(summary="카탈로그 배포 삭제")
    @DeleteMapping("/catalog/deploy/{catalogDeployId}")
    public ResponseWrapper<Integer> deleteCatalogDeploy(@PathVariable int catalogDeployId) {
    	return new ResponseWrapper<>(catalogDeployService.deleteCatalogDeploy(catalogDeployId));
    }
}
