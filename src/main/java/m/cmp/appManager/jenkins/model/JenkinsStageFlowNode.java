package m.cmp.appManager.jenkins.model;

import lombok.Getter;
import lombok.ToString;

import java.util.List;

@Getter
@ToString
public class JenkinsStageFlowNode extends JenkinsPipelinieLog {

    private List<String> parentNodes;
}
