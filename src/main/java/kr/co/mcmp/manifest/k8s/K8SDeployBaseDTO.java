package kr.co.mcmp.manifest.k8s;

import java.io.Serializable;

public class K8SDeployBaseDTO extends DeployDTO implements Serializable {

	private static final long serialVersionUID = -3817943578925605249L;

	//private Long deployId;
	
	//private String deployName;
	
	private Long stageId;
	
	private String stageNickName;
	
	//private Long buildId;
	
	private String buildName;
	
	private Integer configId;

	private String configName;
	
	private String deployYaml;

	//private Integer projectId;
	
	private String projectName;
	
	//private Integer subgroupId;

	private String subgroupName;
	
	private String serviceGroupName;

	private String accessUserType;
	
	private String serviceGroupRole;
	
	private String deployAvailable;
	
	
	private String regName;
	private String modName;

	private String deployResult;
	
	public Long getStageId() {
		return stageId;
	}

	public void setStageId(Long stageId) {
		this.stageId = stageId;
	}
	
	public String getStageNickName() {
		return stageNickName;
	}

	public void setStageNickName(String stageNickName) {
		this.stageNickName = stageNickName;
	}

	public String getBuildName() {
		return buildName;
	}

	public void setBuildName(String buildName) {
		this.buildName = buildName;
	}

	public Integer getConfigId() {
		return configId;
	}

	public void setConfigId(Integer configId) {
		this.configId = configId;
	}

	public String getConfigName() {
		return configName;
	}

	public void setConfigName(String configName) {
		this.configName = configName;
	}

	public String getDeployYaml() {
		return deployYaml;
	}

	public void setDeployYaml(String deployYaml) {
		this.deployYaml = deployYaml;
	}

	public String getProjectName() {
		return projectName;
	}

	public void setProjectName(String projectName) {
		this.projectName = projectName;
	}

	public String getSubgroupName() {
		return subgroupName;
	}

	public void setSubgroupName(String subgroupName) {
		this.subgroupName = subgroupName;
	}

	public String getServiceGroupName() {
		return serviceGroupName;
	}

	public void setServiceGroupName(String serviceGroupName) {
		this.serviceGroupName = serviceGroupName;
	}

	public String getRegName() {
		return regName;
	}

	public void setRegName(String regName) {
		this.regName = regName;
	}

	public String getModName() {
		return modName;
	}

	public void setModName(String modName) {
		this.modName = modName;
	}

	public String getAccessUserType() {
		return accessUserType;
	}

	public void setAccessUserType(String accessUserType) {
		this.accessUserType = accessUserType;
	}

	public String getServiceGroupRole() {
		return serviceGroupRole;
	}

	public void setServiceGroupRole(String serviceGroupRole) {
		this.serviceGroupRole = serviceGroupRole;
	}

	public String getDeployAvailable() {
		return deployAvailable;
	}

	public void setDeployAvailable(String deployAvailable) {
		this.deployAvailable = deployAvailable;
	}

	public String getDeployResult() { return deployResult; }

	public void setDeployResult(String deployResult) { this.deployResult = deployResult; }

}
