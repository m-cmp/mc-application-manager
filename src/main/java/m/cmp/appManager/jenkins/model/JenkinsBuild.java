package m.cmp.appManager.jenkins.model;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@Tag(name = "JenkinsBuild", description = "Jenkins Build 정보")
public class JenkinsBuild implements Serializable {

	private static final long serialVersionUID = -5111991560267809480L;

	private String projectName;
	private String groupName;
	private String repositoryUrl;
	private String applicationPort;
	private String applicationInstallPath;
	private String artifactName;
	private String builderPath;
}