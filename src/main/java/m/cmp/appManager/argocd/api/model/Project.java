package m.cmp.appManager.argocd.api.model;

import java.io.Serializable;

public class Project implements Serializable {

	private Metadata metadata;
	private Spec spec;
	private Status status;
	private final static long serialVersionUID = -1868096633831309840L;

	public Metadata getMetadata() {
		return metadata;
	}

	public void setMetadata(Metadata metadata) {
		this.metadata = metadata;
	}

	public Spec getSpec() {
		return spec;
	}

	public void setSpec(Spec spec) {
		this.spec = spec;
	}

	public Status getStatus() {
		return status;
	}

	public void setStatus(Status status) {
		this.status = status;
	}

}