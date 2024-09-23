package kr.co.mcmp.ape.workflow.service.jenkins;

import kr.co.mcmp.util.JenkinsPipelineUtil;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class JenkinsPipelineGeneratorService {

	/**
	 * Template 조회 (Workflow)
	 * @return
	 */
	/* 
	public List<WorkflowStageMappingDto> getWorkflowTemplate(String workflowName) {

		// Checkout And Build 파이프라인(CHECKOUTBUILD)
		WorkflowStageMappingDto startPipeline = WorkflowStageMappingDto.setWorkflowTemplate(getStartPipelineWorkflow(workflowName));

		// Docker Image or WAR File Upload(FILEUPLOAD)
		WorkflowStageMappingDto endPipeline = WorkflowStageMappingDto.setWorkflowTemplate(getEndPipeline());

		List<WorkflowStageMappingDto> pipelines = new ArrayList<>();
		pipelines.add(startPipeline);
		pipelines.add(endPipeline);

		return pipelines;
	}
 */
	/**
	 * Pipeline 시작 부분 생성
	 */
	private String getStartPipelineWorkflow(String workflowName) {
		StringBuffer sb = new StringBuffer();

		JenkinsPipelineUtil.appendLine(sb,
			"import groovy.json.JsonOutput\n" +
				"import groovy.json.JsonSlurper\n" +
				"import groovy.json.JsonSlurperClassic");

		JenkinsPipelineUtil.appendLine(sb, "\n");

		JenkinsPipelineUtil.appendLine(sb, "" +
				"import groovy.json.JsonSlurper\n" +
				"\n" +
				"def getSSHKey(jsonInput) {\n" +
				"    def json = new JsonSlurper().parseText(jsonInput)\n" +
				"    return json.findResult { it.key == 'McisSubGroupAccessInfo' ? \n" +
				"        it.value.findResult { it.McisVmAccessInfo?.findResult { it.privateKey } } : null \n" +
				"    } ?: ''\n" +
				"}\n" +
				"\n" +
				"def getPublicInfoList(jsonInput) {\n" +
				"    def json = new JsonSlurper().parseText(jsonInput)\n" +
				"    return json.findAll { it.key == 'McisSubGroupAccessInfo' }\n" +
				"        .collectMany { it.value.McisVmAccessInfo*.publicIP }\n" +
				"}\n");

		JenkinsPipelineUtil.appendLine(sb, "\n");

		JenkinsPipelineUtil.appendLine(sb, "pipeline {\n" +
				"  agent any\n" +
				"  \n" +
				"  environment {\n" +
				"    env = ''\n" +
				"  }\n" +
				"  \n" +
				"  stages {\n");

		return sb.toString();
	}
	/**
	 * Pipeline 끝 부분 생성
	 */
	public String getEndPipeline() {
		StringBuffer sb = new StringBuffer();

		JenkinsPipelineUtil.appendLine(sb, "  }\n" +
				"}\n");

		return sb.toString();
	}
}
