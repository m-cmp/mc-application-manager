package m.cmp.appManager.argocd.api.model;


import java.io.Serializable;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ComparedTo implements Serializable {

	private static final long serialVersionUID = -3457161469886280727L;
	private Source source;
    private Destination destination;

}
