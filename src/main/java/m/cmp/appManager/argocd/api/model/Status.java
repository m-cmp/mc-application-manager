
package m.cmp.appManager.argocd.api.model;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Status implements Serializable
{

    private List<Condition> conditions = null;
    private Health health;
    private List<History> history = null;
    private String observedAt;
    private OperationState operationState;
    private String reconciledAt;
    private List<ResourceStatus> resources = null;
    private String sourceType;
    private Map<String, Object> summary;
    //private Sync sync;
    private final static long serialVersionUID = 5132404506954660805L;


}
