
package m.cmp.appManager.argocd.api.model;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SyncPolicy implements Serializable
{

    private Map<String, Boolean> automated;
    private Map<String, Object> retry;
    private List<String> syncOptions = null;
    private final static long serialVersionUID = -6243779685797633315L;

}
