package m.cmp.appManager.jenkins.api;

import com.cdancy.jenkins.rest.JenkinsApi;
import com.cdancy.jenkins.rest.JenkinsClient;
import com.cdancy.jenkins.rest.domain.common.RequestStatus;
import com.cdancy.jenkins.rest.domain.crumb.Crumb;
import com.cdancy.jenkins.rest.domain.job.*;
import com.cdancy.jenkins.rest.domain.queue.QueueItem;
import com.cdancy.jenkins.rest.features.JobsApi;
import com.cdancy.jenkins.rest.features.QueueApi;
import com.fasterxml.jackson.databind.JsonNode;
import m.cmp.appManager.jenkins.model.JenkinsBuildDescribeLog;
import m.cmp.appManager.jenkins.model.JenkinsBuildDetailLog;
import m.cmp.appManager.jenkins.model.JenkinsCredential;
import m.cmp.appManager.jenkins.model.JenkinsWorkflow;
import m.cmp.appManager.util.AES256Util;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class JenkinsRestApi {
	
	private static final int DEFAULT_RETRY_INTERVAL = 3000;

	@Autowired
	private JenkinsRestClient client;

    /**
     * JenkinsClient Object 획득
     */
    private JenkinsClient getJenkinsClient(String url, String id, String password) {
    	String plainTextPassword = AES256Util.decrypt(password);
    	
        return JenkinsClient.builder().endPoint(url)
			                .credentials(id + ":" + plainTextPassword)
			                .build();
    }

    /**
     * Jenkins 연결 확인
     */
    public boolean isConnect(String url, String id, String password) {
        boolean isRunning = false;
        try {
        	String plainTextPassword = AES256Util.decrypt(password);
        	
            JenkinsClient jenkinsClient = JenkinsClient.builder().endPoint(url)
                    									.credentials(id + ":" + plainTextPassword).build();
            
            String jenkinsSessionStr = jenkinsClient.api().systemApi().systemInfo().jenkinsSession();
            log.info("jenkinsSessionStr >>> {}", jenkinsSessionStr);
            if (!jenkinsSessionStr.equals("-1") && !jenkinsSessionStr.isEmpty()) {
                isRunning = true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        return isRunning;
    }

    /**
     * Crumb 조회
     */
    public Crumb getJenkinsCrumb(String url, String id, String password) {
        JenkinsClient jenkinsClient = getJenkinsClient(url, id, password);
        
        return jenkinsClient.api().crumbIssuerApi().crumb();
    }

    /**
     * Jenkins Job 목록 조회
     */
    public List<Job> getJenkinsJobList(String url, String id, String password) {
        JenkinsClient jenkinsClient = getJenkinsClient(url, id, password);
        JobsApi jobsApi = jenkinsClient.api().jobsApi();
        return jobsApi.jobList("/").jobs();
    }

    /**
     * Jenkins Job 조회
     */
    public JobInfo getJenkinsJob(String url, String id, String password, String jobName) {
        JenkinsClient jenkinsClient = getJenkinsClient(url, id, password);
        JobsApi jobsApi = jenkinsClient.api().jobsApi();
        return jobsApi.jobInfo("/",jobName);
    }

    /**
     * Jenkins Job 생성
     * @throws UnsupportedEncodingException 
     */
    public RequestStatus createJenkinsJob(String url, String id, String password, String jobName, String configXml) throws UnsupportedEncodingException {
        configXml = URLEncoder.encode(configXml, "UTF-8");        
        log.info("[JenkinsRestApi.createJenkinsJob] configXml >>> {}", configXml);
        
        JenkinsClient jenkinsClient = getJenkinsClient(url, id, password);
        JobsApi jobsApi = jenkinsClient.api().jobsApi();
        return jobsApi.create(null, jobName, configXml);
    }
    
    /**
     * Jenkins Job 수정
     * @throws UnsupportedEncodingException 
     */
    public boolean updateJenkinsJob(String url, String id, String password, String jobName, String configXml) throws UnsupportedEncodingException {
        configXml = URLEncoder.encode(configXml, "UTF-8");
        
        JenkinsClient jenkinsClient = getJenkinsClient(url, id, password);
        JobsApi jobsApi = jenkinsClient.api().jobsApi();
        return jobsApi.config(null, jobName, configXml);
    }

    /**
     * Jenkins Job 삭제
     */
    public RequestStatus deleteJenkinsJob(String url, String id, String password, String jobName) {
        JenkinsClient jenkinsClient = getJenkinsClient(url, id, password);
        JobsApi jobsApi = jenkinsClient.api().jobsApi();
        return jobsApi.delete(null, jobName);
    }

    /**
     * Jenkins Job 빌드
     */
    public int buildJenkinsJob(String url, String id, String password, String jobName, Map<String, List<String>> params) {
        JenkinsClient jenkinsClient = getJenkinsClient(url, id, password);
        JobsApi jobsApi = jenkinsClient.api().jobsApi();
        return jobsApi.buildWithParameters(null, jobName, params).value();
    }
    
    /**
     * Jenkins Job > Build Number 조회
     * @param url
     * @param id
     * @param password
     //* @param jobName
     * @param jenkinsBuildId
     * @return
     */
    public int getQueueExecutableNumber(String url, String id, String password, int jenkinsBuildId) {
        JenkinsClient jenkinsClient = getJenkinsClient(url, id, password);

        QueueApi queueApi = jenkinsClient.api().queueApi();

        List<QueueItem> queueItemList = queueApi.queue();

        QueueItem currentQueueItem = null;
        if (queueItemList.size() > 0) {
            currentQueueItem = queueApi.queueItem(jenkinsBuildId);
        }

        int buildNumber = 0;
        try {
	        if (currentQueueItem != null) {
	            while (currentQueueItem.executable() == null && !currentQueueItem.cancelled()) {
	                log.info("[ 빌드 큐에서 대기중... ] / queue id:{} / url:{}", currentQueueItem.id(), currentQueueItem.url());
	
	                Thread.sleep(DEFAULT_RETRY_INTERVAL);
	
	                currentQueueItem = queueApi.queueItem(jenkinsBuildId);
	            }
	            
	            buildNumber = currentQueueItem.executable().number();
	        }
        } catch (InterruptedException e) {
            log.error("[getQueueExecutableNumber] InterruptedException >>>>", e);
        }
        
        return buildNumber;
    }

    /**
     * Jenkins Job 빌드 대기, 빌드 진행 상태 모니터링
     */
    public BuildInfo waitJenkinsBuild(String url, String id, String password, String jobName, int jenkinsBuildId, int buildNumber) {
        BuildInfo buildInfo = null;

        JenkinsClient jenkinsClient = getJenkinsClient(url, id, password);
        JenkinsApi jenkinsApi = jenkinsClient.api();

        JobsApi jobsApi = jenkinsApi.jobsApi();
        QueueApi queueApi = jenkinsApi.queueApi();

        List<QueueItem> queueItemList = queueApi.queue();

        QueueItem currentQueueItem = null;

        if (queueItemList.size() > 0) {
            currentQueueItem = queueApi.queueItem(jenkinsBuildId);
        }

        try {
            if (currentQueueItem != null) {
            	buildNumber = currentQueueItem.executable().number();
                while (currentQueueItem.executable() == null && !currentQueueItem.cancelled()) {
                    log.info("[ 빌드 큐에서 대기중... ] / queue id:{} / url:{}", currentQueueItem.id(), currentQueueItem.url());

                    Thread.sleep(DEFAULT_RETRY_INTERVAL);

                    currentQueueItem = queueApi.queueItem(jenkinsBuildId);
                }                
            } 

            buildInfo = jobsApi.buildInfo(null, jobName, buildNumber);
            while (buildInfo.building()) {
                log.info("[ 빌드 진행 중... ] / jobName:{} / jenkins build id:{} / build queue number:{}", jobName, jenkinsBuildId, buildNumber);

                Thread.sleep(DEFAULT_RETRY_INTERVAL);

                buildInfo = jobsApi.buildInfo(null, jobName, buildNumber);
            }

            log.info("[ 빌드 종료. ]/ result:{} / building():{} / duration:{} / number:{} / jenkinsBuildId:{} / displayName:{} / fullDisplayName:{}",
                    buildInfo.result(), buildInfo.building(), buildInfo.duration(), buildInfo.number(), buildInfo.queueId(),
                    buildInfo.displayName(), buildInfo.fullDisplayName(), buildInfo.queueId());

        } catch (InterruptedException e) {
            log.error("[waitJenkinsBuild] InterruptedException >>>>", e);
        }
        return buildInfo;
    }

    /**
     * Build Console Log 조회
     */
    public String getJenkinsBuildConsoleLog(String url, String id, String password, String jobName, int buildNumber, int queueNumber) {
        JenkinsClient jenkinsClient = getJenkinsClient(url, id, password);
        JobsApi jobsApi = jenkinsClient.api().jobsApi();
        return jobsApi.progressiveText(null, jobName, buildNumber, 0).text();
    }
    
    /**
     * Workflow 조회
     */
    public Workflow getJenkinsWorkflow(String url, String id, String password, String jobName, int buildNumber) {
        JenkinsClient jenkinsClient = getJenkinsClient(url, id, password);
        JobsApi jobsApi = jenkinsClient.api().jobsApi();
        return jobsApi.workflow(null, jobName, buildNumber);
    }

    /**
     * Pipeline Node 조회
     */
    public PipelineNode getJenkinsPipelineNode(String url, String id, String password, String jobName, int buildNumber, int stageId) {
        JenkinsClient jenkinsClient = getJenkinsClient(url, id, password);
        JobsApi jobsApi = jenkinsClient.api().jobsApi();
        return jobsApi.pipelineNode(null, jobName, buildNumber, stageId);
    }
    
    /**
     * JUnit Test 결과 조회
     */
    public ResponseEntity<JsonNode> getJunitTestReport(String baseUrl, String id, String password, String jobName, int buildNumber) {
    	String apiUrl = String.format("%s/job/%s/%d/testReport/api/json", baseUrl, jobName, buildNumber);
    	
    	ResponseEntity<JsonNode> response = client.requestByBasicAuth(apiUrl, id, password, HttpMethod.GET, null, JsonNode.class);
    	
    	return response;
    }

    /**
     * Build Stage View 조회
     */
    public ResponseEntity<JenkinsWorkflow> getWorkflow(String baseUrl, String id, String password, String jobName, int buildNumber) {
        String apiUrl = String.format("%s/job/%s/%d/wfapi/describe", baseUrl, jobName, buildNumber);

        ResponseEntity<JenkinsWorkflow> response = client.requestByBasicAuth(apiUrl, id, password, HttpMethod.GET, null, JenkinsWorkflow.class);

        return response;
    }
    
    /**
     * Build Stage Logs 조회
     */
    public ResponseEntity<JenkinsBuildDescribeLog> getPipelineNode(String baseUrl, String id, String password, String jobName, int buildNumber, int nodeId) {
    	String apiUrl = String.format("%s/job/%s/%d/execution/node/%d/wfapi/describe", baseUrl, jobName, buildNumber,nodeId);
    	
    	ResponseEntity<JenkinsBuildDescribeLog> response = client.requestByBasicAuth(apiUrl, id, password, HttpMethod.GET, null, JenkinsBuildDescribeLog.class);
    	
    	return response;
    }

    /**
     * Build Stage Detail Log 조회
     */
    public ResponseEntity<JenkinsBuildDetailLog> getPipelineNodeLog(String baseUrl, String id, String password, String jobName, int buildNumber, int nodeId) {
        String apiUrl = String.format("%s/job/%s/%d/execution/node/%d/wfapi/log", baseUrl, jobName, buildNumber,nodeId);

        ResponseEntity<JenkinsBuildDetailLog> response = client.requestByBasicAuth(apiUrl, id, password, HttpMethod.GET, null, JenkinsBuildDetailLog.class);

        return response;
    }

    /**
     * Job Pipeline Script 조회
     */
    public ResponseEntity<byte[]> getJobPipelineScript(String baseUrl, String id, String password, String jobName) {
        String apiUrl = String.format("%s/job/%s/config.xml", baseUrl, jobName);

        ResponseEntity<byte[]> response = client.requestByBasicAuth(apiUrl, id, password, HttpMethod.GET, null, byte[].class);

        return response;
    }

    /**
     * jenkins credential 상세 조회
     */
    public ResponseEntity<JenkinsCredential> getCredential(String baseUrl, String id, String password, String credentialName) {
        String apiUrl = String.format("%s/credentials/store/system/domain/_/credential/%s/api/json/", baseUrl, credentialName);

        ResponseEntity<JenkinsCredential> response = client.requestByBasicAuth(apiUrl, id, password, HttpMethod.GET, null, JenkinsCredential.class);

        return response;
    }

    /**
     * jenkins credential 생성
     */
    public ResponseEntity<String> createCredential(String baseUrl, String id, String password, Crumb crumb, String credentialXml) {
        String apiUrl = String.format("%s/credentials/store/system/domain/_/createCredentials", baseUrl);

        ResponseEntity<String> response = client.requestJenkinsCrumbAPI(apiUrl, id, password, crumb, HttpMethod.POST, credentialXml, String.class);

        return response;
    }
    
    /**
     * jenkins credential 수정
     */
    public ResponseEntity<String> updateCredential(String baseUrl, String id, String password, Crumb crumb, String credentialName, String credentialXml) {
    	String apiUrl = String.format("%s/credentials/store/system/domain/_/credential/%s/config.xml", baseUrl, credentialName);
    	
    	ResponseEntity<String> response = client.requestJenkinsCrumbAPI(apiUrl, id, password, crumb, HttpMethod.POST, credentialXml, String.class);
    	
    	return response;
    }
    
    /**
     * jenkins credential 삭제
     */
    public ResponseEntity<Object> deleteCredential(String baseUrl, String id, String password, Crumb crumb, String credentialName) {
    	String apiUrl = String.format("%s/credentials/store/system/domain/_/credential/%s/doDelete", baseUrl, credentialName);
    	
    	ResponseEntity<Object> response = client.requestJenkinsCrumbAPI(apiUrl, id, password, crumb, HttpMethod.POST, null, Object.class);
    	
    	return response;
    }
}