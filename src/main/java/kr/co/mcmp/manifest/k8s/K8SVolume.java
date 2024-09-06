package kr.co.mcmp.manifest.k8s;

public class K8SVolume {

	private String name;
	
	private String mountPath;
	private String hostPath;
	private String type;
	
	private String claimName;
	
	private String secretName;
	private String shareName;
	
	//private String accessMode;
	private String requestStorage;
	//private String volumeType;
	
	private String storageClassName;
	
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getMountPath() {
		return mountPath;
	}

	public void setMountPath(String mountPath) {
		this.mountPath = mountPath;
	}

	public String getHostPath() {
		return hostPath;
	}

	public void setHostPath(String hostPath) {
		this.hostPath = hostPath;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getClaimName() {
		return claimName;
	}

	public void setClaimName(String claimName) {
		this.claimName = claimName;
	}

	public String getSecretName() {
		return secretName;
	}

	public void setSecretName(String secretName) {
		this.secretName = secretName;
	}

	public String getShareName() {
		return shareName;
	}

	public void setShareName(String shareName) {
		this.shareName = shareName;
	}

	public String getRequestStorage() {
		return requestStorage;
	}

	public void setRequestStorage(String requestStorage) {
		this.requestStorage = requestStorage;
	}

	public String getStorageClassName() {
		return storageClassName;
	}

	public void setStorageClassName(String storageClassName) {
		this.storageClassName = storageClassName;
	}
	
}
