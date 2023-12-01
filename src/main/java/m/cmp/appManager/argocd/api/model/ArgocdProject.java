package m.cmp.appManager.argocd.api.model;

import java.io.Serializable;

import javax.annotation.Generated;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Generated("jsonschema2pojo")
public class ArgocdProject implements Serializable {

	private Project project;
	private Boolean upsert;
	private final static long serialVersionUID = -6498892099896454669L;

}