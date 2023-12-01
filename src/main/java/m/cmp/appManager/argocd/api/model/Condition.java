
package m.cmp.appManager.argocd.api.model;

import java.io.Serializable;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Condition implements Serializable
{

    //private Time lastTransitionTime;
    private String message;
    private String type;
    private final static long serialVersionUID = -2098490461102988914L;

}
