package kr.co.mcmp.manifest.k8s;

import javax.validation.constraints.NotNull;

public class K8SPort {

	@NotNull
	private String name;
	private String protocol;
	@NotNull
	private Integer port;
	@NotNull
	private Integer containerPort;
	private Integer nodePort;
	private String ingressPath;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getProtocol() {
		return protocol;
	}

	public void setProtocol(String protocol) {
		this.protocol = protocol;
	}

	public Integer getPort() {
		return port;
	}

	public void setPort(Integer port) {
		this.port = port;
	}

	public Integer getContainerPort() {
		return containerPort;
	}

	public void setContainerPort(Integer containerPort) {
		this.containerPort = containerPort;
	}

	public Integer getNodePort() {
		return nodePort;
	}

	public void setNodePort(Integer nodePort) {
		this.nodePort = nodePort;
	}

	public String getIngressPath() {
		return ingressPath;
	}

	public void setIngressPath(String ingressPath) {
		this.ingressPath = ingressPath;
	}
	
}
