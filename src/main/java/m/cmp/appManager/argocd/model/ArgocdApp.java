package m.cmp.appManager.argocd.model;


import java.io.Serializable;

import javax.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Tag(name = "ArgocdApp", description = "배포된 ArgoCd Application 객체")
public class ArgocdApp implements Serializable {

	private static final long serialVersionUID = -7251724879026853872L;
	
	private Integer applicationId;
	
	private	String	projectName;                // project 이름
	
	private	String	applicationName;                 // application(deploy) 이름

    @NotBlank(message="server not be blank")
    private String server;

    @NotBlank(message="namespace not be blank")
    private String  namespace = "default";

    private String repoUrl;                     // git repo url
    private String repoPath;              		// git path
    private String repoTargetRevision;         // git target revision (branch)

    private Integer deployId;                        
    private Integer k8sId;
    
 
}
