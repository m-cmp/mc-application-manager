package m.cmp.appManager.catalog.service;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import m.cmp.appManager.api.response.ResponseCode;
import m.cmp.appManager.argocd.api.model.ArgocdApplication;
import m.cmp.appManager.argocd.api.model.Helm;
import m.cmp.appManager.argocd.api.model.Source;
import m.cmp.appManager.argocd.exception.ArgocdException;
import m.cmp.appManager.argocd.mapper.ArgocdAppMapper;
import m.cmp.appManager.argocd.model.ArgocdApp;
import m.cmp.appManager.argocd.service.ArgocdService;
import m.cmp.appManager.catalog.mapper.CatalogDeployMapper;
import m.cmp.appManager.catalog.model.CatalogDeploy;
import m.cmp.appManager.catalog.model.CatalogDeployHistory;
import m.cmp.appManager.exception.McmpException;
import m.cmp.appManager.nexus.service.NexusService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CatalogDeployService {
	
	@Autowired
	private NexusService nexusService;
	
	@Autowired
	private ArgocdService argocdService;
	
	@Autowired
	private CatalogDeployMapper catalogDeployMapper;
	
	@Autowired
	private ArgocdAppMapper argocdAppMapper;
	
	/**
	 * 카탈로그 배포 목록 조회
	 * @param catalogDeploy
	 * @return
	 */
	public List<CatalogDeploy> getCatalogDeployList(CatalogDeploy catalogDeploy) {
		return catalogDeployMapper.selectCatalogDeployList(catalogDeploy);
	}

	/**
	 * 카탈로그 배포 상세 조회
	 * @param catalogDeployId
	 * @return
	 */
	public CatalogDeploy getCatalogDeploy(int catalogDeployId) {
		return catalogDeployMapper.selectCatalogDeploy(catalogDeployId);
	}
	
	/**
	 * 카탈로그 배포 설정 조회 > values.yaml 파일 조회 
	 * @param catalogDeploy
	 * @return
	 */
	public String getValuesYaml(CatalogDeploy catalogDeploy) {
		// 저장소 URL
		String repoUrl  = nexusService.getNexusRepositoryUrl(catalogDeploy.getNexusId());
		
		// 빌드명이 없을 경우, 카탈로그명으로 조회
		if ( StringUtils.isBlank(catalogDeploy.getDeployName()) ) {
			catalogDeploy.setDeployName(catalogDeploy.getCatalogName());
		}
		
		Source source = new Source();
		source.setAppName(catalogDeploy.getDeployName());
		source.setChart(catalogDeploy.getCatalogName());
		source.setTargetRevision(catalogDeploy.getCatalogVersion());
		source.setRepoURL(repoUrl);
		
		// 이미 배포된 경우, 기존에 배포했던 values.yaml 내용을 파라미터에 추가해야 함.
		if ( catalogDeploy.getCatalogDeployId() != null && catalogDeploy.getCatalogDeployId() > 0 ) {
			CatalogDeploy orgCatalogDeploy = catalogDeployMapper.selectCatalogDeploy(catalogDeploy.getCatalogDeployId());
			
			Helm helm = new Helm();
			helm.setValues(orgCatalogDeploy.getCatalogDeployYaml());
			source.setHelm(helm);
		}
		
		return argocdService.getAppDetails(catalogDeploy.getK8sId(), catalogDeploy.getNexusId(), source);
	}
	
	/**
	 * 카탈로그 배포명 중복 체크
	 * @param k8sId
	 * @param deployName
	 * @return
	 */
	public boolean isCatalogDeployNameDuplicated(int k8sId, String deployName) {
		// 중복이면 true / 아니면 false
		boolean duplicated = catalogDeployMapper.isCatalogDeployNameDuplicated(k8sId, deployName);
	
		if ( !duplicated ) {
			try {
				ArgocdApplication application = argocdService.getApplication(k8sId, deployName);
				if ( application != null ) {
					duplicated = true;
				}
			} catch( ArgocdException e ) {
				log.debug("[argocdService.getApplication] error code : {}, error message : {}", e.getCode(), e.getMessag());
			}
		}
		
		return duplicated;
	}
	
	/**
	 * 카탈로그 배포 정보 복사 
	 * @param catalogDeploy
	 * @return
	 */
	public List<CatalogDeploy> getCatalogDeployByCatalogName(CatalogDeploy catalogDeploy) {
		return catalogDeployMapper.selectCatalogDeployByCatalogName(catalogDeploy);
	}
	
	/**
	 * 카탈로그 배포 등록
	 * @param catalogDeploy
	 * @return
	 */
	@Transactional
	public int createCatalogDeploy(CatalogDeploy catalogDeploy) {			
		// cat_argocd_app 정보 조회
		String repoUrl  = nexusService.getNexusRepositoryUrl(catalogDeploy.getNexusId());
		
		// 배포 정보 등록 
		catalogDeployMapper.insertCatalogDeploy(catalogDeploy);
		
		// ArgoCd 어플리케이션 정보 등록
		ArgocdApp argocdApp = new ArgocdApp();
		argocdApp.setDeployId(catalogDeploy.getCatalogDeployId());
		argocdApp.setApplicationName(catalogDeploy.getDeployName());
		argocdApp.setProjectName("default");
		argocdApp.setNamespace(catalogDeploy.getNamespace());
		argocdApp.setRepoUrl(repoUrl);
		argocdApp.setServer("https://kubernetes.default.svc");
		argocdAppMapper.insertCatalogArgocdApplication(argocdApp);
		
		return catalogDeploy.getCatalogDeployId();
	}
	
	/**
	 * 카탈로그 배포 수정 - TODO 수정 범위 확인 필요(Yaml만 수정인 경우 추가 작업 필요 없음)
	 * @param catalogDeploy
	 * @return
	 */
	@Transactional
	public int upodateCatalogDeploy(CatalogDeploy catalogDeploy) {
		return catalogDeployMapper.updateCatalogDeploy(catalogDeploy);
	}
	
	/**
	 * 카탈로그 배포 실행 
	 * @param catalogDeployId
	 * @param deployUserId
	 * @param deployUserName
	 * @return
	 */
	public String runCatalogDeploy(int catalogDeployId, String deployUserId, String deployUserName) {
		CatalogDeploy catalogDeploy = catalogDeployMapper.selectCatalogDeploy(catalogDeployId);
		if ( catalogDeploy == null ) {
			throw new McmpException(ResponseCode.BAD_REQUEST, "Catalog Deploy cannot be found.");
		}
		
		// ArgoCd 어플리케이션 정보 조회
		ArgocdApp argocdApp = argocdAppMapper.selectCatalogArgocdApplication(catalogDeployId);
		
		// 배포 이력 등록
		CatalogDeployHistory history = new CatalogDeployHistory();
		history.setCatalogDeployId(catalogDeployId);
		history.setCatalogDeployYaml(catalogDeploy.getCatalogDeployYaml());
		history.setDeployUserId(deployUserId);
		history.setDeployUserName(deployUserName);
		catalogDeployMapper.insertCatalogDeployHistory(history);
		
		String deployResult = "FAILURE";
		String deployDesc   = null;
		try {
			// 배포 
			ArgocdApplication argocdApplication = runDeploy(catalogDeploy, argocdApp);
			
			// 베포 결과 등록
			if ( argocdApplication != null ) {
				deployResult = "SUCCESS";
			}
		} catch ( McmpException e ) {
			deployDesc = String.format("status code : %s, message : %s", e.getResponseCode(), e.getDetail());
		}
		
		history.setDeployResult(deployResult);
		history.setDeployDesc(deployDesc);
		catalogDeployMapper.updateCatalogDeployHistory(history);
		
		return deployResult;
	}
	
	private ArgocdApplication runDeploy(CatalogDeploy catalogDeploy, ArgocdApp argocdApp) {
		// Argocd Application 생성 여부 조회 
		String deployType = "CREATE";
		ArgocdApplication application = null;
		try {
			application = argocdService.getApplication(catalogDeploy.getK8sId(), catalogDeploy.getDeployName());
			if ( application != null ) {
				deployType = "UPDATE";
			}
		} catch ( ArgocdException e ) {
			log.error("[CatalogDeploy.runDeploy] error code : {} // error message >>> {}", e.getCode(), e.getMessag());
		}
		
		// TODO ArgoCd에서 Nexus Repository 연결 여부 확인 
		if ( StringUtils.equals("CREATE", deployType) ) {
			application = argocdService.createCatalogHelmChartApplication(catalogDeploy, argocdApp.getProjectName(), argocdApp.getRepoUrl(), catalogDeploy.getNexusId());
		} 
		else {
			application = argocdService.updateCatalogHelmChartApplication(catalogDeploy, argocdApp.getRepoUrl());
		}
		
		return application;
	}
	
	/**
	 * 카탈로그 배포 삭제 
	 * @param catalogDeployId
	 * @return
	 */
	@Transactional
	public int deleteCatalogDeploy(int catalogDeployId) {
		// Argocd 배포된 Application 삭제 
		CatalogDeploy catalogDeploy = catalogDeployMapper.selectCatalogDeploy(catalogDeployId);
		argocdService.deleteApplication(catalogDeploy.getK8sId(), catalogDeploy.getDeployName());
		
		// ArgoCd 어플리케이션 정보 삭제 
		argocdAppMapper.deleteCatalogArgocdApplication(catalogDeployId);
		
		// 배포 이력 데이터 삭제 
		catalogDeployMapper.deleteCatalogDeployHistory(catalogDeployId);
		
		// 배포 정보 삭제 
		return catalogDeployMapper.deleteCatalogDeploy(catalogDeployId);
	}
}