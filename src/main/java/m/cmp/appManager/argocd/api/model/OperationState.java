
package m.cmp.appManager.argocd.api.model;

import java.io.Serializable;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OperationState implements Serializable
{

    private String finishedAt;
    private String message;
    private Operation operation;
    private String phase;
    private String retryCount;
    private String startedAt;
    private SyncResult syncResult;
    private final static long serialVersionUID = 234361878076611479L;

}
