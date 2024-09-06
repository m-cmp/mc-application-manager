package kr.co.mcmp.manifest.k8s;

public class K8SResource {

	//단위 m(0.1core = 100milliCPU)
	private String requestCpu;
	//단위 M(megabyte)
	private String requestMemory;

	private String limitCpu;
	private String limitMemory;

	public String getRequestCpu() {
		return requestCpu;
	}

	public void setRequestCpu(String requestCpu) {
		this.requestCpu = requestCpu;
	}

	public String getRequestMemory() {
		return requestMemory;
	}

	public void setRequestMemory(String requestMemory) {
		this.requestMemory = requestMemory;
	}

	public String getLimitCpu() {
		return limitCpu;
	}

	public void setLimitCpu(String limitCpu) {
		this.limitCpu = limitCpu;
	}

	public String getLimitMemory() {
		return limitMemory;
	}

	public void setLimitMemory(String limitMemory) {
		this.limitMemory = limitMemory;
	}

}
