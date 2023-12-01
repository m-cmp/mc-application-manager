
package m.cmp.appManager.argocd.api.model;

import java.io.Serializable;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class History implements Serializable
{

    private String deployStartedAt;
    private String deployedAt;
    private String id;
    private String revision;
    private Source source;
    private final static long serialVersionUID = -6811845471327717612L;


}
