package m.cmp.appManager.argocd.api.model;

import java.io.Serializable;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SyncStatus implements Serializable {

	private static final long serialVersionUID = 1303375713436509944L;
	private String status;
    private ComparedTo comparedTo;
    private String revision;

}
