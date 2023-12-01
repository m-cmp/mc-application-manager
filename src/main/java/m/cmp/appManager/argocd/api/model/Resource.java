
package m.cmp.appManager.argocd.api.model;

import java.io.Serializable;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Resource implements Serializable
{

    private String group;
    private String kind;
    private String name;
    private String namespace;
    private final static long serialVersionUID = 6309751194091662074L;

}
