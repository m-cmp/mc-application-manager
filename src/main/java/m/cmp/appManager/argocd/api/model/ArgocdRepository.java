package m.cmp.appManager.argocd.api.model;

import java.io.Serializable;

import javax.annotation.Generated;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Generated("jsonschema2pojo")
public class ArgocdRepository implements Serializable {

	private static final long serialVersionUID = 782856169648628145L;
	
	private String type;
	private String repo;
	private String project;
	private String username;
	private String password;
	private String name;
	
	private ConnectionState connectionState;
	private Boolean enableLfs;
	private Boolean enableOCI;
	private String githubAppEnterpriseBaseUrl;
	private String githubAppID;
	private String githubAppInstallationID;
	private String githubAppPrivateKey;
	private Boolean inheritedCreds;
	private Boolean insecure;
	private Boolean insecureIgnoreHostKey;
	private String proxy;
	private String sshPrivateKey;
	private String tlsClientCertData;
	private String tlsClientCertKey;

	

}