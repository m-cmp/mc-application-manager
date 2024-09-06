package kr.co.mcmp.manifest.k8s;

public class AviConfigDTO {
	
	private Integer aviId;

	private String zoneCode;
	
	private String url;
	
	private String username;
	
	private String password;

	private String subnet;

	public Integer getAviId() {
		return aviId;
	}

	public void setAviId(Integer aviId) {
		this.aviId = aviId;
	}

	public String getZoneCode() {
		return zoneCode;
	}

	public void setZoneCode(String zoneCode) {
		this.zoneCode = zoneCode;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getSubnet() {
		return subnet;
	}

	public void setSubnet(String subnet) {
		this.subnet = subnet;
	}
	
}