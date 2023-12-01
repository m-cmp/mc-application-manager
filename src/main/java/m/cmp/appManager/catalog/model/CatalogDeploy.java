package m.cmp.appManager.catalog.model;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.tags.Tag;
import m.cmp.appManager.k8s.model.K8SConfig;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Tag(name = "CatalogDeploy", description = "카탈로그 배포")
public class CatalogDeploy extends K8SConfig implements Serializable {
    private static final long serialVersionUID = 7773159173335481522L;
    
	private Integer catalogDeployId;
    private Integer k8sId;
    private Integer nexusId;
	private String  namespace = "default";
    private String  catalogName;
    private String  catalogVersion;
    private String  deployName;
    private String  catalogTypeCd;				// 카탈로그 배포 형태 구분 ('IMAGE' - Docker Image 배포 / 'HELMCHART' - Helm Chart 배포)
    private String  catalogDeployYaml;
    private String  regId;
    private String  regName;
    private String  regDate;
    private String  modId;
    private String  modName;
    private String  modDate;
}
