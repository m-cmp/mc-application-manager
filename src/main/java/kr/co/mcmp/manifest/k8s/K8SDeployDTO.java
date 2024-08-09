package kr.co.mcmp.manifest.k8s;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

public class K8SDeployDTO extends K8SDeployBaseDTO implements Serializable {

	private static final long serialVersionUID = 5433248174008755823L;

	@NotNull
	private String namespace = "default";

	@NotNull
	private String controller;

	private Integer replicas;
	private String strategyType;

	private String schedule;

	@NotNull
	private String name;
	private Map<String, String> labels;
	private List<String> command;
	private List<String> args;

	private Map<String, String> configMapData;
	private Map<String, String> secretData;

	private String hostname;
	private List<String> nodeSelector;

	private List<K8SVolume> hostPathVolumes;
	private List<K8SVolume> pvcVolumes;
	private List<K8SVolume> azureFileVolumes;

	@NotNull
	private List<K8SPort> ports;
	@NotNull
	private String ingressPathRewriteYn = "N";
	@NotNull
	private String serviceType;
	
	//private String headlessYn = "N";
	private List<String> externalIPs;
	
	private String imagePullSecret;
	
	private String disableDelete;

	private K8SAutoscale autoscale;
	
	private K8SResource resource;
	
	private K8SProxyInfo proxyInfo;
	

	
	//private Map<String, String> proxyTls;

	public String getNamespace() {
		return namespace;
	}

	public void setNamespace(String namespace) {
		this.namespace = namespace;
	}

	public String getController() {
		return controller;
	}

	public void setController(String controller) {
		this.controller = controller;
	}

	public Integer getReplicas() {
		return replicas;
	}

	public void setReplicas(Integer replicas) {
		this.replicas = replicas;
	}

	public String getStrategyType() {
		return strategyType;
	}

	public void setStrategyType(String strategyType) {
		this.strategyType = strategyType;
	}

	public String getSchedule() {
		return schedule;
	}

	public void setSchedule(String schedule) {
		this.schedule = schedule;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Map<String, String> getLabels() {
		return labels;
	}

	public void setLabels(Map<String, String> labels) {
		this.labels = labels;
	}

	public List<String> getCommand() {
		return command;
	}

	public void setCommand(List<String> command) {
		this.command = command;
	}

	public List<String> getArgs() {
		return args;
	}

	public void setArgs(List<String> args) {
		this.args = args;
	}

	public Map<String, String> getConfigMapData() {
		return configMapData;
	}

	public void setConfigMapData(Map<String, String> configMapData) {
		this.configMapData = configMapData;
	}

	public Map<String, String> getSecretData() {
		return secretData;
	}

	public void setSecretData(Map<String, String> secretData) {
		this.secretData = secretData;
	}

	public String getHostname() {
		return hostname;
	}

	public void setHostname(String hostname) {
		this.hostname = hostname;
	}

	public List<String> getNodeSelector() {
		return nodeSelector;
	}

	public void setNodeSelector(List<String> nodeSelector) {
		this.nodeSelector = nodeSelector;
	}

	public List<K8SVolume> getHostPathVolumes() {
		return hostPathVolumes;
	}

	public void setHostPathVolumes(List<K8SVolume> hostPathVolumes) {
		this.hostPathVolumes = hostPathVolumes;
	}

	public List<K8SVolume> getPvcVolumes() {
		return pvcVolumes;
	}

	public void setPvcVolumes(List<K8SVolume> pvcVolumes) {
		this.pvcVolumes = pvcVolumes;
	}

	public List<K8SVolume> getAzureFileVolumes() {
		return azureFileVolumes;
	}

	public void setAzureFileVolumes(List<K8SVolume> azureFileVolumes) {
		this.azureFileVolumes = azureFileVolumes;
	}

	public List<K8SPort> getPorts() {
		return ports;
	}

	public void setPorts(List<K8SPort> ports) {
		this.ports = ports;
	}

	public String getIngressPathRewriteYn() {
		return ingressPathRewriteYn;
	}

	public void setIngressPathRewriteYn(String ingressPathRewriteYn) {
		this.ingressPathRewriteYn = ingressPathRewriteYn;
	}

	public String getServiceType() {
		return serviceType;
	}

	public void setServiceType(String serviceType) {
		this.serviceType = serviceType;
	}

	public List<String> getExternalIPs() {
		return externalIPs;
	}

	public void setExternalIPs(List<String> externalIPs) {
		this.externalIPs = externalIPs;
	}

	public String getImagePullSecret() {
		return imagePullSecret;
	}

	public void setImagePullSecret(String imagePullSecret) {
		this.imagePullSecret = imagePullSecret;
	}

	public String getDisableDelete() {
		return disableDelete;
	}

	public void setDisableDelete(String disableDelete) {
		this.disableDelete = disableDelete;
	}

	public K8SResource getResource() {
		return resource;
	}

	public void setResource(K8SResource resource) {
		this.resource = resource;
	}

	public K8SAutoscale getAutoscale() {
		return autoscale;
	}

	public void setAutoscale(K8SAutoscale autoscale) {
		this.autoscale = autoscale;
	}

	public K8SProxyInfo getProxyInfo() {
		return proxyInfo;
	}

	public void setProxyInfo(K8SProxyInfo proxyInfo) {
		this.proxyInfo = proxyInfo;
	}
	


}
