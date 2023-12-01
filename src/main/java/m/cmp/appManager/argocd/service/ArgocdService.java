package m.cmp.appManager.argocd.service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import m.cmp.appManager.api.response.ResponseCode;
import m.cmp.appManager.argocd.api.ApplicationApi;
import m.cmp.appManager.argocd.api.ProjectApi;
import m.cmp.appManager.argocd.api.RepositoryApi;
import m.cmp.appManager.argocd.api.model.ArgocdApplication;
import m.cmp.appManager.argocd.api.model.ArgocdProject;
import m.cmp.appManager.argocd.api.model.ArgocdRepository;
import m.cmp.appManager.argocd.api.model.Destination;
import m.cmp.appManager.argocd.api.model.Helm;
import m.cmp.appManager.argocd.api.model.HelmChartDetail;
import m.cmp.appManager.argocd.api.model.Metadata;
import m.cmp.appManager.argocd.api.model.Source;
import m.cmp.appManager.argocd.api.model.Spec;
import m.cmp.appManager.argocd.api.model.SyncPolicy;
import m.cmp.appManager.argocd.exception.ArgocdException;
import m.cmp.appManager.argocd.model.ArgocdConfig;
import m.cmp.appManager.catalog.model.CatalogDeploy;
import m.cmp.appManager.exception.McmpException;
import m.cmp.appManager.k8s.mapper.K8SMapper;
import m.cmp.appManager.oss.model.Oss;
import m.cmp.appManager.oss.service.OssService;
import m.cmp.appManager.util.AES256Util;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ArgocdService {
	
	@Value("${argocd.project.name}")
	private String argocdProjectName; 
	
    @Autowired
    private K8SMapper k8sMapper;

    @Autowired
    private ApplicationApi applicationApi;
    
    @Autowired
    private ProjectApi projectApi;
    
    @Autowired
    private RepositoryApi repositoryApi;
    
    @Autowired
    private OssService ossService;
	
	/**
	 * deploy 배포 > Helm Chart Application 생성
	 * @param deploy
	 * @param projectName
	 * @param repoUrl
	 * @param nexusId
	 * @return
	 */
	public ArgocdApplication createCatalogHelmChartApplication(CatalogDeploy deploy, String projectName, String repoUrl, int nexusId) {
		// 1. project 등록
		createProject(deploy.getK8sId(), projectName);
		
		// 2. repo 등록
		createNexusRepository(deploy.getK8sId(), projectName, repoUrl, nexusId);
		
		// 3. Application 등록
		log.info("app 생성요청 applicationName={}", deploy.getDeployName());
		ArgocdConfig config = k8sMapper.selectArgoCd(deploy.getK8sId());
		
		ArgocdApplication application = generateHelmChartApplication(deploy, repoUrl);
		try {
			application = (ArgocdApplication) applicationApi.createApplication(config, application, ArgocdApplication.class);
			log.info("app 생성요청 applicationName={}", deploy.getDeployName());
		} catch ( ArgocdException e ) {
			log.error("app 생성 요청 실패");
			log.error("error code : {} /// error message : {}", e.getCode(), e.getMessag());
			throw new McmpException(ResponseCode.CREATE_FAILED_APPLICATION, String.format("%s>>>%s", e.getCode(), e.getMessage( )) );
		}
		
		return application;
	}
	
	/**
	 * deploy 배포 > Helm Chart Application 수정
	 * @param deploy
	 * @param repoUrl
	 * @return
	 */
	public ArgocdApplication updateCatalogHelmChartApplication(CatalogDeploy deploy, String repoUrl) {		
		log.info("app 수정 요청 applicationName={}", deploy.getDeployName());
		ArgocdConfig config = k8sMapper.selectArgoCd(deploy.getK8sId());

		ArgocdApplication application = generateHelmChartApplication(deploy, repoUrl);

		application = (ArgocdApplication) applicationApi.updateHelmChartApplication(config, deploy.getDeployName(), application, ArgocdApplication.class);
		log.info("app 생성요청 applicationName={}", deploy.getDeployName());
		
		return application;
	}
    
    /**
     * Application 정보 조회
     * @param k8sId
     * @param applicationName
     * @return
     */
	public ArgocdApplication getApplication(int k8sId, String applicationName) {
    	ArgocdConfig config = k8sMapper.selectArgoCd(k8sId);
    	ArgocdApplication application = (ArgocdApplication) applicationApi.getApplication(config, applicationName, ArgocdApplication.class);
    	return application;
    }
	
	/**
	 * 삭제
	 * @param k8sId
	 * @param applicationName
	 * @return
	 */
	public void deleteApplication(int k8sId, String applicationName) {
		log.info("app 삭제요청 applicationName={}", applicationName);
		ArgocdConfig config = k8sMapper.selectArgoCd(k8sId);

		try {			
			if (getApplication(k8sId, applicationName) != null) {
				applicationApi.deleteApplication(config, applicationName);
			}
			log.info("app 삭제완료 applicationName={}", applicationName);
		} catch (ArgocdException e) {
			log.info("app 삭제실패 applicationName={}, {}", applicationName, e.getDetail());
		}
	}
	
	/**
	 * Repository 정보 조회
	 * @param config
	 * @param repoUrl
	 * @return
	 */
	private ArgocdRepository getRepository(ArgocdConfig config, String repoUrl) {		
		try { 
			return (ArgocdRepository)repositoryApi.getRepository(config, repoUrl, ArgocdRepository.class);
		} catch(ArgocdException e) {
			if (e.getCode() == ResponseCode.NOT_FOUND.getCode()) {
				log.info("repo 조회실패 url={}, {}", repoUrl, e.getDetail());
				return null;
			} else {
				throw e;
			}
		}
	}
	
	/**
	 * Repository 생성 > HelmChart배포를 위한 Nexus Repository 생성 
	 * @param k8sId
	 * @param repoUrl
	 * @return
	 */
	public ArgocdRepository createNexusRepository(int k8sId, String projectName, String repoUrl, int nexusId) {
		log.info("repo 생성요청 url={}", repoUrl);
		ArgocdConfig config = k8sMapper.selectArgoCd(k8sId);
		
		ArgocdRepository repository = getRepository(config, repoUrl);
		if (repository == null) {
			Oss nexus = ossService.getOss(nexusId);
			repository = (ArgocdRepository)repositoryApi.createHelmChartRepository(config, projectName, repoUrl, nexus.getOssUsername(), AES256Util.decrypt(nexus.getOssPassword()), ArgocdRepository.class);
		}
		log.info("repo 생성완료 url={}", repoUrl);
		
		return repository;
	}
	
	/**
	 * Helm Chart > Values.yaml 조회 
	 * @param k8sId
	 * @param source
	 * @return
	 */
	public String getAppDetails(int k8sId, int nexusId, Source source) {
		String valuesYaml = null;
		
		ArgocdConfig config = k8sMapper.selectArgoCd(k8sId);

		// 프로젝트 등록
		createProject(k8sId, argocdProjectName);
		
		// Repository 등록 
		createNexusRepository(k8sId, argocdProjectName, source.getRepoURL(), nexusId);
		
		HelmChartDetail helmChartDetail = (HelmChartDetail) repositoryApi.getAppDetails(config, source, argocdProjectName, HelmChartDetail.class);
		if ( helmChartDetail != null ) {
			valuesYaml = helmChartDetail.getHelm().getValues();
		}
		
		return valuesYaml;
	}
	
	/**
	 * Repository 삭제
	 * Repository가 속해있는 Project가 존재하는 경우 삭제하지 않는다.  
	 */
	public void deleteRepository(int k8sId, String repoUrl) {
		log.info("repo 삭제요청 url={}", repoUrl);
		ArgocdConfig config = k8sMapper.selectArgoCd(k8sId);

		//repository 조회
		ArgocdRepository repository = getRepository(config, repoUrl);
		if (repository == null) return;
		
		try {
			//project 조회
			String projectName = repository.getProject();
			ArgocdProject project = getProject(config, projectName);
			
			//project가 존재하지 않으면 삭제할 수 있다
			if (project == null) {
				repositoryApi.deleteRepository(config, repoUrl);
				log.info("repo 삭제완료 url={}", repoUrl);
			} else {
				log.info("repo 삭제실패 url={}, project={} exists", repoUrl, projectName);
			}
		} catch (ArgocdException e) {
			log.info("repo 삭제실패 url={}, {}", repoUrl, e.getDetail());
		}
	}
	
	/**
	 * ArgoCd 연결 체크
	 * @param config
	 * @param projectName
	 * @return
	 */
	public ArgocdProject getProject(ArgocdConfig config, String projectName) {
		try { 
			return (ArgocdProject)projectApi.getProject(config, projectName, ArgocdProject.class);
		} catch(ArgocdException e) {
			if (e.getCode() == ResponseCode.NOT_FOUND.getCode()) {
				log.info("프로젝트 조회실패 projectName={}, {}", projectName, e.getDetail());
				return null;
			} else {
				throw e;
			}
		}
	}	
	
	/**
	 * 프로젝트 생성
	 * @param k8sId
	 * @param projectName
	 * @return
	 */
	public ArgocdProject createProject(int k8sId, String projectName) {
		log.info("프로젝트 생성요청 projectName={}", projectName);
		ArgocdConfig config = k8sMapper.selectArgoCd(k8sId);
		
		ArgocdProject project = getProject(config, projectName);
		if (project == null) {
			project = (ArgocdProject)projectApi.createProject(config, projectName, ArgocdProject.class);
		}
		log.info("프로젝트 생성완료 projectName={}", projectName);
		return project;
	}
    
	/**
	 * Helm Chart 생성 시 파라미터
	 * @param deploy
	 * @param repoUrl
	 * @return
	 */
    private ArgocdApplication generateHelmChartApplication(CatalogDeploy deploy, String repoUrl) {    	
    	ArgocdApplication application = new ArgocdApplication();
    	
    	Metadata metadata = new Metadata();
    	metadata.setName(deploy.getDeployName());
    	application.setMetadata(metadata);
    	
    	Spec spec = new Spec();
    	
    	Destination destination = new Destination();
    	destination.setServer("https://kubernetes.default.svc"); //https://kubernetes.default.svc
    	destination.setNamespace(deploy.getNamespace()); //default
    	spec.setDestination(destination);
    	
    	spec.setProject("default");
    	
    	SyncPolicy syncPolicy = new SyncPolicy();
    	List<String> syncOptions = Arrays.asList(new String[]{"CreateNamespace=true"});
    	syncPolicy.setSyncOptions(syncOptions);
    	Map<String, Boolean> automated = new HashMap<>();
    	automated.put("prune", false);
    	syncPolicy.setAutomated(automated);
    	spec.setSyncPolicy(syncPolicy);
    	
    	Helm helm = new Helm();
    	helm.setValues(deploy.getCatalogDeployYaml());
    	
    	Source source = new Source();
    	source.setRepoURL(repoUrl);
    	source.setChart(deploy.getCatalogName());
    	source.setTargetRevision(deploy.getCatalogVersion());
    	source.setHelm(helm);
    	spec.setSource(source);
    	
    	application.setMetadata(metadata);
    	application.setSpec(spec);
    	
    	return application;    	
    }
}    