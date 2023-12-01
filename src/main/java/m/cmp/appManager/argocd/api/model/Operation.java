
package m.cmp.appManager.argocd.api.model;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Operation implements Serializable
{

	private List<Map<String, Object>> info;
	private Map<String, Object> initiatedBy;
	private Map<String, Object> retry;
    private Sync sync;
    private final static long serialVersionUID = 6575536128244095255L;

}
