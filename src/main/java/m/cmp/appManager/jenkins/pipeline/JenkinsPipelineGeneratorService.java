package m.cmp.appManager.jenkins.pipeline;

import m.cmp.appManager.jenkins.pipeline.model.Pipeline;
import m.cmp.appManager.util.JenkinsPipelineUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class JenkinsPipelineGeneratorService {
	
	@Value("${gitlab.deploy.yaml.path}")
	private String deployYamlPath;
	
	@Value("${gitlab.deploy.yaml.filename}")
	private String deployYamlFileName;
    
    /**
     * 파이프라인 생성 > Default 조회
     * @return
     */
    public List<Pipeline> getDefaultPipeline(String deployName, String gitlabCloneUrl, String branch, String gitlabCredentialId, String clusterCredentialId) {
		// Checkout And Build 파이프라인(CHECKOUTBUILD)
		Pipeline startPipeline = new Pipeline();
		startPipeline.setPipelineCd("");
		startPipeline.setPipelineScript(getStartPipeline(deployName, gitlabCloneUrl, branch, gitlabCredentialId, clusterCredentialId));
		
		// Docker Image or WAR File Upload(FILEUPLOAD)
		Pipeline endPipeline = new Pipeline();
		endPipeline.setPipelineCd("");
		endPipeline.setPipelineScript(getEndPipeline());

		List<Pipeline> pipelines = new ArrayList<>();
		pipelines.add(startPipeline);
		pipelines.add(endPipeline);
		
		return pipelines;
    }
	/**
	 * Pipeline 시작 부분 생성
	 */
	private String getStartPipeline(String deployName, String gitlabCloneUrl, String branch, String gitlabCredentialId, String clusterCredentialId) {
		StringBuffer sb = new StringBuffer();

		JenkinsPipelineUtil.appendLine(sb, "//It was created by the Devops portal.");
		JenkinsPipelineUtil.appendLine(sb, "pipeline {");
		JenkinsPipelineUtil.appendLine(sb, "agent any", 1);
		JenkinsPipelineUtil.appendLine(sb, "", 1);
		JenkinsPipelineUtil.appendLine(sb, "environment {", 1);
		JenkinsPipelineUtil.appendLine(sb, String.format("GIT_CLONE_URL = '%s'", gitlabCloneUrl), 2);
		JenkinsPipelineUtil.appendLine(sb, String.format("BRANCH = '%s'", branch), 2);
		JenkinsPipelineUtil.appendLine(sb, String.format("GIT_CREDENTIAL = '%s'", gitlabCredentialId), 2);
		JenkinsPipelineUtil.appendLine(sb, String.format("DEPLOY_YAML_PATH = '%s/%s'", deployYamlPath, deployName), 2);
		JenkinsPipelineUtil.appendLine(sb, String.format("DEPLOY_YAML_FILE = '%s'", deployYamlFileName), 2);
		JenkinsPipelineUtil.appendLine(sb, String.format("CLUSTER_CREDENTIAL = '%s'", clusterCredentialId), 2);
		JenkinsPipelineUtil.appendLine(sb, "}", 1);		
		JenkinsPipelineUtil.appendLine(sb, "", 1);		
		JenkinsPipelineUtil.appendLine(sb, "stages {", 1);

		return sb.toString();
	}


	/**
	 * 파이프라인 생성 > Default 조회f (Workflow)
	 * @return
	 */
	public List<Pipeline> getDefaultPipelineWorkflow(String workflowName) {
		// Checkout And Build 파이프라인(CHECKOUTBUILD)
		Pipeline startPipeline = new Pipeline();
		startPipeline.setPipelineCd("");
		startPipeline.setPipelineScript(getStartPipelineWorkflow(workflowName));

		// Docker Image or WAR File Upload(FILEUPLOAD)
		Pipeline endPipeline = new Pipeline();
		endPipeline.setPipelineCd("");
		endPipeline.setPipelineScript(getEndPipeline());

		List<Pipeline> pipelines = new ArrayList<>();
		pipelines.add(startPipeline);
		pipelines.add(endPipeline);

		return pipelines;
	}

	/**
	 * Pipeline 시작 부분 생성
	 */
	private String getStartPipelineWorkflow(String workflowName) {
		StringBuffer sb = new StringBuffer();

		JenkinsPipelineUtil.appendLine(sb, "//It was created by the Devops portal.");
		JenkinsPipelineUtil.appendLine(sb, "pipeline {");
		JenkinsPipelineUtil.appendLine(sb, "agent any", 1);
		JenkinsPipelineUtil.appendLine(sb, "", 1);
		JenkinsPipelineUtil.appendLine(sb, "environment {", 1);

		JenkinsPipelineUtil.appendLine(sb, "MC_SPIDER_REST_URI = '${MC_SPIDER_REST_URI}'", 2);
		JenkinsPipelineUtil.appendLine(sb, "CONFIG_ID = '${CONFIG_ID}'", 2);
		JenkinsPipelineUtil.appendLine(sb, "CB_TUMBLEBUG_SWAGGER_URI = '${CB_TUMBLEBUG_SWAGGER_URI}'", 2);
		JenkinsPipelineUtil.appendLine(sb, "MCIS_NAME = '${MCIS_NAME}'", 2);

		// Tomcat
		JenkinsPipelineUtil.appendLine(sb, "TOMCAT_VER = '${TOMCAT_VER}'", 2);
		JenkinsPipelineUtil.appendLine(sb, "TAR_URL = '${TAR_URL}'", 2);

		// redis
		JenkinsPipelineUtil.appendLine(sb, "REDIS_PASS = '${REDIS_PASS}'", 2);

		JenkinsPipelineUtil.appendLine(sb, "}", 1);
		JenkinsPipelineUtil.appendLine(sb, "", 1);
		JenkinsPipelineUtil.appendLine(sb, "stages {", 1);

		return sb.toString();
	}
	/**
	 * Pipeline 끝 부분 생성
	 */
	public String getEndPipeline() {	
		StringBuffer sb = new StringBuffer();
		
		JenkinsPipelineUtil.appendLine(sb, "}", 1);
		JenkinsPipelineUtil.appendLine(sb, "}");
		
		return sb.toString();
	}
}
