
package m.cmp.appManager.argocd.api.model;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Sync implements Serializable
{

    private boolean dryRun;
    private List<String> manifests = null;
    private boolean prune;
    private List<Resource> resources = null;
    private String revision;
    private Source source;
    private List<String> syncOptions = null;
    private Map<String, Object> syncStrategy;
    private final static long serialVersionUID = -2261180385241695671L;

}
