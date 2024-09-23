package kr.co.mcmp.ape.workflow.service.jenkins.model;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class JenkinsPipelinieLog extends JenkinsStage {
	
    private String execNode;

	private Error error;

	private String parameterDescription;

	@Getter
	@ToString
	public static class Error{
	    private String message;
	    private String type;
	}
}
