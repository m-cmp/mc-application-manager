
package m.cmp.appManager.argocd.api.model;

import java.io.Serializable;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SyncResult implements Serializable
{

    private List<Resource> resources = null;
    private String revision;
    private Source source;
    private final static long serialVersionUID = 4506310125745236634L;

}
