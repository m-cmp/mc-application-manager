package kr.co.mcmp.ape.workflow.service.jenkins.model;

import java.util.List;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class JenkinsWorkflow {

	private String name;

	private String status;

	private long startTimeMillis;

	private long durationTimeMillis;

	private List<JenkinsStage> stages;
}
