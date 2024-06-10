package m.cmp.appManager.jenkins.model;

import com.cdancy.jenkins.rest.domain.job.PipelineNode;
import com.cdancy.jenkins.rest.domain.job.Workflow;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Map;

@Getter
@Setter
public class JenkinsBuildStageAllLog implements Serializable {
    private static final long serialVersionUID = 1592933627347511691L;

	private Workflow workflow;

    private Map<String, PipelineNode> pipelineNodeMap;
}