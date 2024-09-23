package kr.co.mcmp.ape.workflow.service.jenkins.service;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.xml.xpath.XPathExpressionException;

import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.cdancy.jenkins.rest.domain.common.RequestStatus;
import com.cdancy.jenkins.rest.domain.crumb.Crumb;
import com.cdancy.jenkins.rest.domain.job.BuildInfo;

import kr.co.mcmp.ape.workflow.service.jenkins.api.JenkinsRestApi;
import kr.co.mcmp.ape.workflow.service.jenkins.model.JenkinsCredential;
import kr.co.mcmp.exception.McmpException;
import kr.co.mcmp.oss.dto.OssDto;
import kr.co.mcmp.response.ResponseCode;
import kr.co.mcmp.util.NamingUtils;
import kr.co.mcmp.util.XMLUtil;
import kr.co.mcmp.ape.workflow.dto.entityMappingDto.WorkflowParamDto;
// import kr.co.mcmp.ape.dto.entityMappingDto.WorkflowParamDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Service
public class JenkinsService {

	private static final String RESOURCE_JENKINS_PATH = "/static/jenkins/";

	private static final String PIPELINE_XML_PATH = "/flow-definition/definition/script";

    private static final String INIT_PIPELINE_XML_FILE_PATH = "jenkins/dynamic-application-provisioning-pipeline.xml";

	private final JenkinsRestApi api;

    /*******
     * jenkins 연결 확인
     */
    public boolean isJenkinsConnect(OssDto jenkins) {
        return api.isConnect(jenkins.getOssUrl(), jenkins.getOssUsername(), jenkins.getOssPassword());
    }

    /*******
     * job 존재 여부 확인
     */
    public boolean isExistJobName(OssDto jenkins, String jobName) {
        return Optional.ofNullable(api.getJenkinsJob(jenkins.getOssUrl(), jenkins.getOssUsername(), jenkins.getOssPassword(), jobName)).isPresent();
    }
    /*****
     * jenkins job 생성
     */
    public boolean createJenkinsJob_v2(OssDto jenkins, String jenkinsJobName, String pipelineScript, List<WorkflowParamDto> params) throws IOException {

        if ( isExistJobName(jenkins, jenkinsJobName) ) {
            log.error("[createJenkinsJob] Jenkins Job Name {} is exist.", jenkinsJobName);
            throw new McmpException(ResponseCode.EXISTS_JENKINS_JOB);
        }

        Document jobTemplateDocument = XMLUtil.getDocument(new ClassPathResource(RESOURCE_JENKINS_PATH+"jenkins-k8s-deploy-job-template.xml").getInputStream());

        addParameter(jobTemplateDocument, params);

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

    public String getXmlContent(){
        try(var inputStream = JenkinsService.class.getClassLoader().getResourceAsStream(INIT_PIPELINE_XML_FILE_PATH)){
            if(inputStream == null){
                throw new IOException("File not found : " + INIT_PIPELINE_XML_FILE_PATH);
            }
            return new String(inputStream.readAllBytes());
        }catch(IOException e){
            log.error("Error reading Jenkins pipeline XML file: {}", e.getMessage(), e);
            return null;
        }
    }
    
    public boolean createJenkinsPipeline(OssDto jenkins, String jobName){
        String xmlContent = getXmlContent();
        try {
            api.createJenkinsJob(jenkins.getOssUrl(), jenkins.getOssUsername(), jenkins.getOssPassword(), jobName, xmlContent);
        } catch (UnsupportedEncodingException e) {
            log.error(e.getMessage(), e);
        }
        return false;
    }

//    /*******
//     * jenkins job Pipeline, Description 수정
//     * job에서 'pipeline script'와 'description(설명)'만 수정한다.
//     * @throws UnsupportedEncodingException
//     *
//     */
//    public boolean updateJenkinsJobPipeline(Oss jenkins, String jobName, String pipeline) throws UnsupportedEncodingException {
//        if ( !isExistJobName(jenkins, jobName) ) {
//            log.error("Jenkins Job Name {} is not exist.", jobName);
//            throw new McmpException(ResponseCode.NOT_EXISTS_JENKINS_JOB);
//        }
//
//        Document document = this.getJobConfigXml(jenkins, jobName);
//
//        String configXml = null;
//        try {
//            Document addPipelineDoc = XMLUtil.appendXml(document, PIPELINE_XML_PATH, pipeline);
//            configXml = XMLUtil.XmlToString(addPipelineDoc);
//        } catch (XPathExpressionException e) {
//            e.printStackTrace();
//            throw new McmpException(ResponseCode.UNKNOWN_ERROR);
//        }
//
//        boolean result = api.updateJenkinsJob(jenkins.getOssUrl(), jenkins.getOssUsername(), jenkins.getOssPassword(), jobName, configXml);
//        if ( !result ) {
//            log.error("[updateJenkinsJobPipeline] Jenkins Job pipeline update fail.");
//            throw new McmpException(ResponseCode.ERROR_JENKINS_API);
//        }
//
//        return result;
//    }
//
    /*******
     * jenkins job Pipeline, Description 수정
     * job에서 'pipeline script'와 'description(설명)'만 수정한다.
     * @throws UnsupportedEncodingException
     *
     */
    // public boolean updateJenkinsJobPipeline_v2(OssDto jenkins, String jenkinsJobName, String pipelineScript, List<WorkflowParamDto> params) throws IOException {
    //     if ( !isExistJobName(jenkins, jenkinsJobName) ) {
    //         log.error("Jenkins Job Name {} is not exist.", jenkinsJobName);
    //         throw new McmpException(ResponseCode.NOT_EXISTS_JENKINS_JOB);
    //     }

    //     // 새로운 XML 파일 생성
    //     Document document = XMLUtil.getDocument(new ClassPathResource(RESOURCE_JENKINS_PATH+"jenkins-k8s-deploy-job-template.xml").getInputStream());

    //     // Jenkins에서 XML 파일 받아오기
    //     // Document document = this.getJobConfigXml(jenkins, jenkinsJobName);

    //     addParameter(document, params);

    //     String configXml = null;
    //     try {
    //         Document addPipelineDoc = XMLUtil.appendXml(document, PIPELINE_XML_PATH, pipelineScript);
    //         configXml = XMLUtil.XmlToString(addPipelineDoc);
    //     } catch (XPathExpressionException e) {
    //         e.printStackTrace();
    //         throw new McmpException(ResponseCode.UNKNOWN_ERROR);
    //     }

    //     boolean result = api.updateJenkinsJob(jenkins.getOssUrl(), jenkins.getOssUsername(), jenkins.getOssPassword(), jenkinsJobName, configXml);
    //     if ( !result ) {
    //         log.error("[updateJenkinsJobPipeline] Jenkins Job pipeline update fail.");
    //         throw new McmpException(ResponseCode.ERROR_JENKINS_API);
    //     }

    //     return result;
    // }

    /*******
     * Job Config.xml 조회
     *
     */
    public Document getJobConfigXml(OssDto jenkins, String jobName) {
        ResponseEntity<byte[]> response = api.getJobPipelineScript(jenkins.getOssUrl(), jenkins.getOssUsername(), jenkins.getOssPassword(), jobName);
        String xmlStr = new String(response.getBody());
        Document jobConfigXml = XMLUtil.getDocument(xmlStr);

        return jobConfigXml;
    }


    /*******
     * jenkins job 삭제
     *
     */
    public boolean deleteJenkinsJob(OssDto jenkins, String jobName) {
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

    /**
     * Jenkins Crumb 조회
     */
    public Crumb getJenkinsCrumb(OssDto jenkins) {

        Crumb crumb = api.getJenkinsCrumb(jenkins.getOssUrl(), jenkins.getOssUsername(), jenkins.getOssPassword());

        return crumb;
    }

    /**
     * credential 존재 여부 확인
     *
     */
    public boolean isCredentialExist(OssDto jenkins, String credentialName){
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
//
//    /**
//     * credential 생성
//     */
//    public String createCredential(Oss jenkins, Oss credentialOss, K8SConfig k8s, String credentialType) {
//        Crumb crumb = getJenkinsCrumb(jenkins);
//
//        String credentialName = null;
//        if ( credentialOss != null ) {
//        	credentialName = NamingUtils.getCredentialName(credentialOss.getOssId(), credentialOss.getOssName());
//        }
//        else {
//        	credentialName = NamingUtils.getCredentialName(k8s.getK8sId(), k8s.getK8sName());
//        }
//
//        boolean isCredentialExist = this.isCredentialExist(jenkins, credentialName);
//        if ( !isCredentialExist ) {
//	        String createCredentialXml = JenkinsCredential.createCredentialXml(credentialOss, k8s, credentialType);
//	        log.info("credentialXml >>> {}", createCredentialXml);
//	        api.createCredential(jenkins.getOssUrl(), jenkins.getOssUsername(), jenkins.getOssPassword(), crumb, createCredentialXml);
//        }
//
//        return credentialName;
//    }
//
    /**
     * credential 수정
     */
    public String updateCredential(OssDto jenkins, OssDto credentialOss, String credentialType) {
    	Crumb crumb = getJenkinsCrumb(jenkins);

        String credentialName = null;
        if ( credentialOss != null ) {
        	credentialName = NamingUtils.getCredentialName(credentialOss.getOssIdx(), credentialOss.getOssName());
        }
		log.info("update credentialName >>> {}", credentialName);

    	boolean isCredentialExist = this.isCredentialExist(jenkins, credentialName);
    	if ( isCredentialExist ) {
    		String createCredentialXml = JenkinsCredential.createCredentialXml(credentialOss, credentialType);
    		log.info("credentialXml >>> {}", createCredentialXml);
    		api.updateCredential(jenkins.getOssUrl(), jenkins.getOssUsername(), jenkins.getOssPassword(), crumb, credentialName, createCredentialXml);
    	}

    	return credentialName;
    }

    /**
     * credential 삭제
     */
    public String deleteCredential(OssDto jenkins, OssDto credentialOss, String credentialType) {
    	Crumb crumb = getJenkinsCrumb(jenkins);

        String credentialName = null;
        if ( credentialOss != null ) {
        	credentialName = NamingUtils.getCredentialName(credentialOss.getOssIdx(), credentialOss.getOssName());
        }
    	log.info("delete credentialName >>> {}", credentialName);

    	boolean isCredentialExist = this.isCredentialExist(jenkins, credentialName);
    	if ( isCredentialExist ) {
    		api.deleteCredential(jenkins.getOssUrl(), jenkins.getOssUsername(), jenkins.getOssPassword(), crumb, credentialName);
    	}

    	return credentialName;
    }
//
//    /**
//     * credential 가져오기
//     */
//    public String getOssCredential(Oss jenkinsOss, Oss credentialOss, K8SConfig k8s, String credentialType) {
//        String credentialName = null;
//        if ( credentialOss != null ) {
//        	credentialName = NamingUtils.getCredentialName(credentialOss.getOssId(), credentialOss.getOssName());
//        }
//        else {
//        	credentialName = NamingUtils.getCredentialName(k8s.getK8sId(), k8s.getK8sName());
//        }
//
//        boolean isCredentialExist = this.isCredentialExist(jenkinsOss, credentialName);
//        if ( !isCredentialExist ) {
//            this.createCredential(jenkinsOss, credentialOss, k8s, credentialType);
//        }
//
//        return credentialName;
//    }
//
    private void addParameter(Document document, List<WorkflowParamDto> params) {

        // parametersDefinitionProperty
        NodeList propertiesList = document.getElementsByTagName("properties");
        Element properties = (Element) propertiesList.item(0);

        // addParametersDefinitionProperty
        Element addParametersDefinitionProperty = document.createElement("hudson.model.ParametersDefinitionProperty");
        properties.appendChild(addParametersDefinitionProperty);

        /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

        // parametersDefinitionProperty
        propertiesList = document.getElementsByTagName("properties");
        properties = (Element) propertiesList.item(0);

        // parameterDefinitions
        NodeList parametersDefinitionPropertyList = properties.getElementsByTagName("hudson.model.ParametersDefinitionProperty");
        Element parameterDefinitionProperties = (Element) parametersDefinitionPropertyList.item(0);

        // addParameterDefinitions
        Element addParameterDefinitions = document.createElement("parameterDefinitions");
        parameterDefinitionProperties.appendChild(addParameterDefinitions);

        /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

        // parameterDefinitions
        parametersDefinitionPropertyList = properties.getElementsByTagName("hudson.model.ParametersDefinitionProperty");
        parameterDefinitionProperties = (Element) parametersDefinitionPropertyList.item(0);
        NodeList parameterDefinitionsList = parameterDefinitionProperties.getElementsByTagName("parameterDefinitions");

        if (parameterDefinitionsList.getLength() > 0) {
            params.forEach(item -> {

                Element parameterDefinitions = (Element) parameterDefinitionsList.item(0);

                // Create new parameter node
                Element stringParameterDefinition = document.createElement("hudson.model.StringParameterDefinition");

                Element name = document.createElement("name");
                name.appendChild(document.createTextNode(item.getParamKey()));
                stringParameterDefinition.appendChild(name);

//                Element description = document.createElement("description");
//                description.appendChild(document.createTextNode(item.getParamDesc()));
//                stringParameterDefinition.appendChild(description);

                Element defaultValue = document.createElement("defaultValue");
                defaultValue.appendChild(document.createTextNode(item.getParamValue()));
                stringParameterDefinition.appendChild(defaultValue);

                Element trim = document.createElement("trim");
                trim.appendChild(document.createTextNode("true"));
                stringParameterDefinition.appendChild(trim);

                // Add the new parameter to the parameter definitions
                parameterDefinitions.appendChild(stringParameterDefinition);
            });
        }
    }


    /*******
     * jenkins job 빌드
     *
     */
    public int buildJenkinsJob(OssDto jenkins, String jobName, Map<String, List<String>> jenkinsJobParams) {
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
    public int getQueueExecutableNumber(OssDto jenkins, int jenkinsBuildId) {
    	return api.getQueueExecutableNumber(jenkins.getOssUrl(), jenkins.getOssUsername(), jenkins.getOssPassword(), jenkinsBuildId);
    }

    /*******
     * jenkins job 빌드
     *
     */
    public BuildInfo waitJenkinsBuild(OssDto jenkins, String jobName, int jenkinsBuildId, int buildNumber) {
        log.info("[buildJenkinsJob] Wait jenkins job >> JENKINS_BUILD_ID: {}", jenkinsBuildId);
        BuildInfo buildInfo = api.waitJenkinsBuild(jenkins.getOssUrl(), jenkins.getOssUsername(), jenkins.getOssPassword(), jobName, jenkinsBuildId, buildNumber);

        return buildInfo;
    }

}
