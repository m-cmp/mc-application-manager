package m.cmp.appManager.jenkins.model;

import lombok.Getter;
import lombok.ToString;

import java.util.List;

@Getter
@ToString
public class JenkinsWorkflow {

	private String name;

	private String status;

	private long startTimeMillis;

	private long durationTimeMillis;

	private List<JenkinsStage> stages;
}
