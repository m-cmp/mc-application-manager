package kr.co.mcmp.manifest.k8s;

public class K8SAutoscale {

	// private int minReplicas;
	private int maxReplicas;
	
	// cpu memory
	private String resourceName;
	
	private int averageUtilization;

	public int getMaxReplicas() {
		return maxReplicas;
	}

	public void setMaxReplicas(int maxReplicas) {
		this.maxReplicas = maxReplicas;
	}

	public String getResourceName() {
		return resourceName;
	}

	public void setResourceName(String resourceName) {
		this.resourceName = resourceName;
	}

	public int getAverageUtilization() {
		return averageUtilization;
	}

	public void setAverageUtilization(int averageUtilization) {
		this.averageUtilization = averageUtilization;
	}

}
