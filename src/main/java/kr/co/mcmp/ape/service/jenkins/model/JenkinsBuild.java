package kr.co.mcmp.ape.service.jenkins.model;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Tag(name = "JenkinsBuild", description = "Jenkins Build information")
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