
package m.cmp.appManager.argocd.api.model;

import java.io.Serializable;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResourceStatus extends Resource implements Serializable
{

    private Health health;
    private boolean hook;
    private boolean requiresPruning;
    private String status;
    private String version;
    
	private String hookPhase;
    private String hookType;
    private String message;
    private String syncPhase;
    
    private final static long serialVersionUID = -5340604516749660763L;

}
