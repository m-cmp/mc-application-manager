
package m.cmp.appManager.argocd.api.model;

import java.io.Serializable;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Source implements Serializable {
	private String appName;
	private String repoURL;
	private String path;
	private String targetRevision;
	
    private String chart;
    private Map<String, Object> directory;
    private Helm helm;
    private Kustomize kustomize;
    private Map<String, Object> plugin;
    
    private final static long serialVersionUID = 8721910742908190603L;


}
