package m.cmp.appManager.jenkins.model;

import lombok.Getter;
import lombok.ToString;

import java.util.List;

@Getter
@ToString
public class JenkinsBuildDescribeLog  extends JenkinsPipelinieLog {

   private List<JenkinsStageFlowNode> stageFlowNodes;
}
