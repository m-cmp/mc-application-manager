package kr.co.mcmp.manifest.k8s;

import java.time.LocalDateTime;

public class DeployDTO{
	
	public static final int DEPLOY_FROM_NORMAL = 1;
	public static final int DEPLOY_FROM_MODELER = 2;
	
	public static final int DEPLOY_TYPE_DOCKER = 1;
	public static final int DEPLOY_TYPE_K8S = 2;
	public static final int DEPLOY_TYPE_AKS = 3;
	public static final int DEPLOY_TYPE_OPENSHIFT = 4;
	public static final int DEPLOY_TYPE_BM = 5;
	
	public static final int DEPLOY_STAGE_STAGE = 1;
	public static final int DEPLOY_STAGE_PRODUCTION = 2;
	public static final int DEPLOY_STAGE_DEVELOPMENT = 3;
	
	public static final String[] DEPLOY_TYPE_NAMES = {"Docker", "Kubernetes", "AKS", "OpenShift", "BM"};
	
	
	private Long deployId;
	private String deployName;
	private int deployFrom;	
	private int deployType;
	private Long stageId;
	private Long projectId;
	private Long buildId;	
	private String regId;
	private LocalDateTime regDate;
	private String modId;
	private LocalDateTime modDate;
	private int deployApproveFlow;
	
	
	public Long getDeployId() {
		return deployId;
	}
	public void setDeployId(Long deployId) {
		this.deployId = deployId;
	}
	public String getDeployName() {
		return deployName;
	}
	public void setDeployName(String deployName) {
		this.deployName = deployName;
	}
	public int getDeployType() {
		return deployType;
	}
	public void setDeployType(int deployType) {
		this.deployType = deployType;
	}
	public int getDeployFrom() {
		return deployFrom;
	}
	public void setDeployFrom(int deployFrom) {
		this.deployFrom = deployFrom;
	}
	public Long getStageId() {
		return stageId;
	}
	public void setStageId(Long stageId) {
		this.stageId = stageId;
	}
	public Long getProjectId() {
		return projectId;
	}
	public void setProjectId(Long projectId) {
		this.projectId = projectId;
	}
	public Long getBuildId() {
		return buildId;
	}
	public void setBuildId(Long buildId) {
		this.buildId = buildId;
	}
	public String getRegId() {
		return regId;
	}
	public void setRegId(String regId) {
		this.regId = regId;
	}
	public LocalDateTime getRegDate() {
		return regDate;
	}
	public void setRegDate(LocalDateTime regDate) {
		this.regDate = regDate;
	}
	public String getModId() {
		return modId;
	}
	public void setModId(String modId) {
		this.modId = modId;
	}
	public LocalDateTime getModDate() {
		return modDate;
	}
	public void setModDate(LocalDateTime modDate) {
		this.modDate = modDate;
	}
	public int getDeployApproveFlow() {
		return deployApproveFlow;
	}
	public void setDeployApproveFlow(int deployApproveFlow) {
		this.deployApproveFlow = deployApproveFlow;
	}
}
