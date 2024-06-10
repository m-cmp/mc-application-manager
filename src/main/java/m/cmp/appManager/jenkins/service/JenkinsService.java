package m.cmp.appManager.jenkins.service;

import com.cdancy.jenkins.rest.domain.common.RequestStatus;
import com.cdancy.jenkins.rest.domain.crumb.Crumb;
import com.cdancy.jenkins.rest.domain.job.BuildInfo;
import m.cmp.appManager.api.response.ResponseCode;
import m.cmp.appManager.exception.McmpException;
import m.cmp.appManager.jenkins.api.JenkinsRestApi;
import m.cmp.appManager.jenkins.model.JenkinsCredential;
import m.cmp.appManager.k8s.model.K8SConfig;
import m.cmp.appManager.oss.model.Oss;
import m.cmp.appManager.util.NamingUtils;
import m.cmp.appManager.util.XMLUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.w3c.dom.Document;

import javax.xml.xpath.XPathExpressionException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class JenkinsService {

	private static final String RESOURCE_JENKINS_PATH = "/static/jenkins/";

	private static final String PIPELINE_XML_PATH = "/flow-definition/definition/script";

	@Autowired
	private JenkinsRestApi api;

    /*******
     * jenkins 연결 확인
     */
    public boolean isJenkinsConnect(Oss jenkins) {
        return api.isConnect(jenkins.getOssUrl(), jenkins.getOssUsername(), jenkins.getOssPassword());
    }

    /*******
     * job 존재 여부 확인
     */
    public boolean isExistJobName(Oss jenkins, String jobName) {
        return Optional.ofNullable(api.getJenkinsJob(jenkins.getOssUrl(), jenkins.getOssUsername(), jenkins.getOssPassword(), jobName)).isPresent();
    }

    /*****
     * jenkins job 생성
     */
    public boolean createJenkinsJob(Oss jenkins, String jenkinsJobName, String pipelineScript) throws IOException {
        if ( isExistJobName(jenkins, jenkinsJobName) ) {
            log.error("[createJenkinsJob] Jenkins Job Name {} is exist.", jenkinsJobName);
            throw new McmpException(ResponseCode.EXISTS_JENKINS_JOB);
        }

        Document jobTemplateDocument = XMLUtil.getDocument(new ClassPathResource(RESOURCE_JENKINS_PATH+"jenkins-k8s-deploy-job-template.xml").getInputStream());

        String configXml = null;
        try {
            Document addPipelineDoc = XMLUtil.appendXml(jobTemplateDocument, PIPELINE_XML_PATH, pipelineScript);
            configXml = XMLUtil.XmlToString(addPipelineDoc);
        } catch (XPathExpressionException e) {
            e.printStackTrace();
            throw new McmpException(ResponseCode.UNKNOWN_ERROR);
        }
        
        RequestStatus req = api.createJenkinsJob(jenkins.getOssUrl(), jenkins.getOssUsername(), jenkins.getOssPassword(), jenkinsJobName, configXml);
        if ( req.value() ) {
            return true;
        } else {
            log.error("[createJenkinsJob] Jenkins Job create fail. message: {}", req.errors());
            throw new McmpException(ResponseCode.ERROR_JENKINS_API);
        }
    }

    /*******
     * jenkins job Pipeline, Description 수정
     * job에서 'pipeline script'와 'description(설명)'만 수정한다.
     * @throws UnsupportedEncodingException 
     *
     */
    public boolean updateJenkinsJobPipeline(Oss jenkins, String jobName, String pipeline) throws UnsupportedEncodingException {
        if ( !isExistJobName(jenkins, jobName) ) {
            log.error("Jenkins Job Name {} is not exist.", jobName);
            throw new McmpException(ResponseCode.NOT_EXISTS_JENKINS_JOB);
        }

        Document document = this.getJobConfigXml(jenkins, jobName);

        String configXml = null;
        try {
            Document addPipelineDoc = XMLUtil.appendXml(document, PIPELINE_XML_PATH, pipeline);
            configXml = XMLUtil.XmlToString(addPipelineDoc);
        } catch (XPathExpressionException e) {
            e.printStackTrace();
            throw new McmpException(ResponseCode.UNKNOWN_ERROR);
        }

        boolean result = api.updateJenkinsJob(jenkins.getOssUrl(), jenkins.getOssUsername(), jenkins.getOssPassword(), jobName, configXml);
        if ( !result ) {
            log.error("[updateJenkinsJobPipeline] Jenkins Job pipeline update fail.");
            throw new McmpException(ResponseCode.ERROR_JENKINS_API);
        }
        
        return result;
    }

    /*******
     * Job Config.xml 조회
     *
     */
    public Document getJobConfigXml(Oss jenkins, String jobName) {
        ResponseEntity<byte[]> response = api.getJobPipelineScript(jenkins.getOssUrl(), jenkins.getOssUsername(), jenkins.getOssPassword(), jobName);
        String xmlStr = new String(response.getBody());
        Document jobConfigXml = XMLUtil.getDocument(xmlStr);

        return jobConfigXml;
    }


    /*******
     * jenkins job 삭제
     *
     */
    public boolean deleteJenkinsJob(Oss jenkins, String jobName) {
    	boolean result = true;
    	
        // job이 없을 경우 pass
        if ( !isExistJobName(jenkins, jobName) ) {
            log.error("[deleteJenkinsJob] Jenkins Job Name {} does not exist.", jobName);
            return result;
        }

        RequestStatus req = api.deleteJenkinsJob(jenkins.getOssUrl(), jenkins.getOssUsername(), jenkins.getOssPassword(), jobName);
        if ( !req.value() ) {
            log.error("[deleteJenkinsJob] Jenkins Job delete fail. message: {}", req.errors());
            throw new McmpException(ResponseCode.ERROR_JENKINS_API);
        }
        
        return result;
    }

    /*******
     * jenkins job 빌드
     *
     */
    public int buildJenkinsJob(Oss jenkins, String jobName, Map<String, List<String>> jenkinsJobParams) {
        if ( !isExistJobName(jenkins, jobName) ) {
            log.error("Jenkins Job Name {} does not exist.", jobName);
            throw new McmpException(ResponseCode.NOT_EXISTS_JENKINS_JOB);
        }

        log.info("[buildJenkinsJob] Run jenkins job.");
        return api.buildJenkinsJob(jenkins.getOssUrl(), jenkins.getOssUsername(), jenkins.getOssPassword(), jobName, jenkinsJobParams);
    }
    
    /*******
     * jenkins job 빌드 번호 조회
     * @param jenkins
     * @param jenkinsBuildId
     * @return
     */
    public int getQueueExecutableNumber(Oss jenkins, int jenkinsBuildId) {
    	return api.getQueueExecutableNumber(jenkins.getOssUrl(), jenkins.getOssUsername(), jenkins.getOssPassword(), jenkinsBuildId);
    }

    /*******
     * jenkins job 빌드
     *
     */
    public BuildInfo waitJenkinsBuild(Oss jenkins, String jobName, int jenkinsBuildId, int buildNumber) {
        log.info("[buildJenkinsJob] Wait jenkins job >> JENKINS_BUILD_ID: {}", jenkinsBuildId);
        BuildInfo buildInfo = api.waitJenkinsBuild(jenkins.getOssUrl(), jenkins.getOssUsername(), jenkins.getOssPassword(), jobName, jenkinsBuildId, buildNumber);

        return buildInfo;
    }

    /**
     * Jenkins Crumb 조회
     */
    public Crumb getJenkinsCrumb(Oss jenkins) {

        Crumb crumb = api.getJenkinsCrumb(jenkins.getOssUrl(), jenkins.getOssUsername(), jenkins.getOssPassword());

        return crumb;
    }

    /**
     * credential 존재 여부 확인
     *
     */
    public boolean isCredentialExist(Oss jenkins, String credentialName){
        try {
            api.getCredential(jenkins.getOssUrl(), jenkins.getOssUsername(), jenkins.getOssPassword(), credentialName);
            return true;
        } catch (RestClientResponseException e){
            if(e.getRawStatusCode() == ResponseCode.NOT_FOUND.getCode()){
                return false;
            }else{
                throw new McmpException(ResponseCode.ERROR_JENKINS_API);
            }
        }
    }

    /**
     * credential 생성
     */
    public String createCredential(Oss jenkins, Oss credentialOss, K8SConfig k8s, String credentialType) {
        Crumb crumb = getJenkinsCrumb(jenkins);
        
        String credentialName = null;
        if ( credentialOss != null ) {
        	credentialName = NamingUtils.getCredentialName(credentialOss.getOssId(), credentialOss.getOssName());
        }
        else {
        	credentialName = NamingUtils.getCredentialName(k8s.getK8sId(), k8s.getK8sName());
        }

        boolean isCredentialExist = this.isCredentialExist(jenkins, credentialName);
        if ( !isCredentialExist ) {
	        String createCredentialXml = JenkinsCredential.createCredentialXml(credentialOss, k8s, credentialType);
	        log.info("credentialXml >>> {}", createCredentialXml);
	        api.createCredential(jenkins.getOssUrl(), jenkins.getOssUsername(), jenkins.getOssPassword(), crumb, createCredentialXml);
        }

        return credentialName;    
    }
    
    /**
     * credential 수정
     */
    public String updateCredential(Oss jenkins, Oss credentialOss, K8SConfig k8s, String credentialType) {
    	Crumb crumb = getJenkinsCrumb(jenkins);
        
        String credentialName = null;
        if ( credentialOss != null ) {
        	credentialName = NamingUtils.getCredentialName(credentialOss.getOssId(), credentialOss.getOssName());
        }
        else {
        	credentialName = NamingUtils.getCredentialName(k8s.getK8sId(), k8s.getK8sName());
        }
		log.info("update credentialName >>> {}", credentialName);
    	
    	boolean isCredentialExist = this.isCredentialExist(jenkins, credentialName);
    	if ( isCredentialExist ) {
    		String createCredentialXml = JenkinsCredential.createCredentialXml(credentialOss, k8s, credentialType);
    		log.info("credentialXml >>> {}", createCredentialXml);
    		api.updateCredential(jenkins.getOssUrl(), jenkins.getOssUsername(), jenkins.getOssPassword(), crumb, credentialName, createCredentialXml);
    	}
    	
    	return credentialName;    	
    }
    
    /**
     * credential 삭제
     */
    public String deleteCredential(Oss jenkins, Oss credentialOss, K8SConfig k8s, String credentialType) {
    	Crumb crumb = getJenkinsCrumb(jenkins);
        
        String credentialName = null;
        if ( credentialOss != null ) {
        	credentialName = NamingUtils.getCredentialName(credentialOss.getOssId(), credentialOss.getOssName());
        }
        else {
        	credentialName = NamingUtils.getCredentialName(k8s.getK8sId(), k8s.getK8sName());
        }
    	log.info("delete credentialName >>> {}", credentialName);
    	
    	boolean isCredentialExist = this.isCredentialExist(jenkins, credentialName);
    	if ( isCredentialExist ) {
    		api.deleteCredential(jenkins.getOssUrl(), jenkins.getOssUsername(), jenkins.getOssPassword(), crumb, credentialName);
    	}
    	
    	return credentialName;
    }

    /**
     * credential 가져오기
     */
    public String getOssCredential(Oss jenkinsOss, Oss credentialOss, K8SConfig k8s, String credentialType) {        
        String credentialName = null;
        if ( credentialOss != null ) {
        	credentialName = NamingUtils.getCredentialName(credentialOss.getOssId(), credentialOss.getOssName());
        }
        else {
        	credentialName = NamingUtils.getCredentialName(k8s.getK8sId(), k8s.getK8sName());
        }

        boolean isCredentialExist = this.isCredentialExist(jenkinsOss, credentialName);
        if ( !isCredentialExist ) {
            this.createCredential(jenkinsOss, credentialOss, k8s, credentialType);
        }

        return credentialName;
    }
}
