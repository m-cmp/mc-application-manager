package kr.co.mcmp.manifest.k8s;

//import kr.co.strato.devops.argocd.model.Resource;
//import kr.co.strato.devops.model.Paging;

public class K8SDeployHistoryDTO {

	private Long deployHistoryId;
	private Long deployId;
	private String deployName;
	private Long buildHistoryId;
	private String description;
	
	private String deployImageName;
	private String deployYaml;
	private int jenkinsBuildId;
	private String deployResult;
	private long deployDuration;
	private String deployDate;
	//private long queueId;
	private String regId;
	private String regName;
	private String regDate;

	private String buildDate;
	
	private String deleteYn ="N"; //기존 controller 삭제 후 배포 진행
	private long queueId;
	
	private String argocdHistoryRevision;
	private int argocdHistoryId;
	private String message;
	//private List<Resource> resources;
	private String status;
	
	private String stageNickName;

	//배포경과일
	private int days;

	
	public Long getDeployHistoryId() {
		return deployHistoryId;
	}

	public void setDeployHistoryId(Long deployHistoryId) {
		this.deployHistoryId = deployHistoryId;
	}

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

	public Long getBuildHistoryId() {
		return buildHistoryId;
	}

	public void setBuildHistoryId(Long buildHistoryId) {
		this.buildHistoryId = buildHistoryId;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}
	
	public String getDeployImageName() {
		return deployImageName;
	}

	public void setDeployImageName(String deployImageName) {
		this.deployImageName = deployImageName;
	}

	public String getDeployYaml() {
		return deployYaml;
	}

	public void setDeployYaml(String deployYaml) {
		this.deployYaml = deployYaml;
	}

	public int getJenkinsBuildId() {
		return jenkinsBuildId;
	}

	public void setJenkinsBuildId(int jenkinsBuildId) {
		this.jenkinsBuildId = jenkinsBuildId;
	}

	public String getDeployResult() {
		return deployResult;
	}

	public void setDeployResult(String deployResult) {
		this.deployResult = deployResult;
	}

	public long getDeployDuration() {
		return deployDuration;
	}

	public void setDeployDuration(long deployDuration) {
		this.deployDuration = deployDuration;
	}

	public String getDeployDate() {
		return deployDate;
	}

	public void setDeployDate(String deployDate) {
		this.deployDate = deployDate;
	}

	public String getRegId() {
		return regId;
	}

	public void setRegId(String regId) {
		this.regId = regId;
	}

	public String getRegName() {
		return regName;
	}

	public void setRegName(String regName) {
		this.regName = regName;
	}

	public String getRegDate() {
		return regDate;
	}

	public void setRegDate(String regDate) {
		this.regDate = regDate;
	}

	public String getBuildDate() {
		return buildDate;
	}

	public void setBuildDate(String buildDate) {
		this.buildDate = buildDate;
	}
	
	public String getArgocdHistoryRevision() {
		return argocdHistoryRevision;
	}

	public void setArgocdHistoryRevision(String argocdHistoryRevision) {
		this.argocdHistoryRevision = argocdHistoryRevision;
	}

	public int getArgocdHistoryId() {
		return argocdHistoryId;
	}

	public void setArgocdHistoryId(int argocdHistoryId) {
		this.argocdHistoryId = argocdHistoryId;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

//	public List<Resource> getResources() {
//		return resources;
//	}
//
//	public void setResources(List<Resource> resources) {
//		this.resources = resources;
//	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getStageNickName() { return stageNickName; }

	public void setStageNickName(String stageNickName) { this.stageNickName = stageNickName; }

	public int getDays() {
		return days;
	}

	public void setDays(int days) {
		this.days = days;
	}

	@Deprecated
	public String getDeleteYn() {
		return deleteYn;
	}
	@Deprecated
	public void setDeleteYn(String deleteYn) {
		this.deleteYn = deleteYn;
	}
	@Deprecated
	public long getQueueId() {
		return queueId;
	}
	@Deprecated
	public void setQueueId(long queueId) {
		this.queueId = queueId;
	}

}
