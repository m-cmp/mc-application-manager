package kr.co.mcmp.ape.workflow.service.jenkins.model;

import java.util.List;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class JenkinsStageFlowNode extends JenkinsPipelinieLog {

    private List<String> parentNodes;
}
