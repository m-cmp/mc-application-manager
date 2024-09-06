package kr.co.mcmp.manifest.k8s;

import java.io.Serializable;

public class K8SConfigDTO implements Serializable {

	private static final long serialVersionUID = 7700620840360863426L;

	//private Integer configId;
	private String configId;
	private String name;
	private String stageId;
	private String description;
	private String content;
	
	private String imagePullSecret;
	
	private String argocdUrl;
	private String argocdId;
	private String argocdPassword;
	private String argocdToken;
		
	private String regId;
	private String regName;
	private String regDate;
	private String modId;
	private String modName;
	private String modDate;
	
	private String zoneId;
	private String region;
	private String networkLocation;
	private String cluster;
	
	private String zoneCode;
	private String defaultDomain;
	
	private String stage;
	private String stageName;
	public String getConfigId() {
		return configId;
	}
	public void setConfigId(String configId) {
		this.configId = configId;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getStageId() {
		return stageId;
	}
	public void setStageId(String stageId) {
		this.stageId = stageId;
	}
	public String getDescription() {
		return description;
	}
	public void setDescription(String description) {
		this.description = description;
	}
	public String getContent() {
		return content;
	}
	public void setContent(String content) {
		this.content = content;
	}
	public String getImagePullSecret() {
		return imagePullSecret;
	}
	public void setImagePullSecret(String imagePullSecret) {
		this.imagePullSecret = imagePullSecret;
	}
	public String getArgocdUrl() {
		return argocdUrl;
	}
	public void setArgocdUrl(String argocdUrl) {
		this.argocdUrl = argocdUrl;
	}
	public String getArgocdId() {
		return argocdId;
	}
	public void setArgocdId(String argocdId) {
		this.argocdId = argocdId;
	}
	public String getArgocdPassword() {
		return argocdPassword;
	}
	public void setArgocdPassword(String argocdPassword) {
		this.argocdPassword = argocdPassword;
	}
	public String getArgocdToken() {
		return argocdToken;
	}
	public void setArgocdToken(String argocdToken) {
		this.argocdToken = argocdToken;
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
	public String getModId() {
		return modId;
	}
	public void setModId(String modId) {
		this.modId = modId;
	}
	public String getModName() {
		return modName;
	}
	public void setModName(String modName) {
		this.modName = modName;
	}
	public String getModDate() {
		return modDate;
	}
	public void setModDate(String modDate) {
		this.modDate = modDate;
	}
	public String getZoneId() {
		return zoneId;
	}
	public void setZoneId(String zoneId) {
		this.zoneId = zoneId;
	}
	public String getRegion() {
		return region;
	}
	public void setRegion(String region) {
		this.region = region;
	}
	public String getNetworkLocation() {
		return networkLocation;
	}
	public void setNetworkLocation(String networkLocation) {
		this.networkLocation = networkLocation;
	}
	public String getCluster() {
		return cluster;
	}
	public void setCluster(String cluster) {
		this.cluster = cluster;
	}
	public String getZoneCode() {
		return zoneCode;
	}
	public void setZoneCode(String zoneCode) {
		this.zoneCode = zoneCode;
	}
	public String getDefaultDomain() {
		return defaultDomain;
	}
	public void setDefaultDomain(String defaultDomain) {
		this.defaultDomain = defaultDomain;
	}
	public String getStage() {
		return stage;
	}
	public void setStage(String stage) {
		this.stage = stage;
	}
	public String getStageName() {
		return stageName;
	}
	public void setStageName(String stageName) {
		this.stageName = stageName;
	}

}
