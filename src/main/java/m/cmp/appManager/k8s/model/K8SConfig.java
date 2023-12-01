package m.cmp.appManager.k8s.model;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.tags.Tag;
import m.cmp.appManager.argocd.model.ArgocdConfig;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Tag(name = "K8SConfig", description = "K8S Config")
public class K8SConfig extends ArgocdConfig implements Serializable {

	private static final long serialVersionUID = -7190917536141142232L;
	
	private Integer k8sId;
	private String k8sName;
	private String providerCd;
	private String k8sDesc;
	private String content;
	
	private String regId;
	private String regName;
	private String regDate;
	private String modId;
	private String modName;
	private String modDate;
	

}
