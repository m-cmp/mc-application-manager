package kr.co.mcmp.manifest.k8s;


public class K8SProxyInfo {

	private String defaultDomainYn = "Y";

	private String domainName;
	private String tlsYn; //사용여부
	private String tlsSecretName;

	private String tlsCrt;
	private String tlsCrtName;
	private String tlsKey;
	private String tlsKeyName;
	
	public String getDefaultDomainYn() {
		return defaultDomainYn;
	}

	public void setDefaultDomainYn(String defaultDomainYn) {
		this.defaultDomainYn = defaultDomainYn;
	}

	public String getDomainName() {
		return domainName;
	}

	public void setDomainName(String domainName) {
		this.domainName = domainName;
	}

	public String getTlsYn() {
		return tlsYn;
	}

	public void setTlsYn(String tlsYn) {
		this.tlsYn = tlsYn;
	}

	public String getTlsSecretName() {
		return tlsSecretName;
	}

	public void setTlsSecretName(String tlsSecretName) {
		this.tlsSecretName = tlsSecretName;
	}

	public String getTlsCrt() {
		return tlsCrt;
	}

	public void setTlsCrt(String tlsCrt) {
		this.tlsCrt = tlsCrt;
	}

	public String getTlsCrtName() {
		return tlsCrtName;
	}

	public void setTlsCrtName(String tlsCrtName) {
		this.tlsCrtName = tlsCrtName;
	}

	public String getTlsKey() {
		return tlsKey;
	}

	public void setTlsKey(String tlsKey) {
		this.tlsKey = tlsKey;
	}

	public String getTlsKeyName() {
		return tlsKeyName;
	}

	public void setTlsKeyName(String tlsKeyName) {
		this.tlsKeyName = tlsKeyName;
	}
	
}
