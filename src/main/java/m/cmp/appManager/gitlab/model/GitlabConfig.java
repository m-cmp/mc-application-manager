package m.cmp.appManager.gitlab.model;

import java.io.Serializable;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GitlabConfig implements Serializable {
	
	private static final long serialVersionUID = -8680758361294722089L;
	
	private String  url;
	private String  username;
	private String  password;
	private String  groupName;
	private String  projectName;
	private String  branch;
}
