package m.cmp.appManager.catalog.model;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Tag(name = "CatalogDeployHistory", description = "카탈로그 배포 이력")
public class CatalogDeployHistory implements Serializable {
    private static final long serialVersionUID = 7773159173335481522L;

    private Integer catalogDeployHistoryId;
    private Integer catalogDeployId;
    private Integer rownum;
    private String  catalogDeployYaml;
    private String  deployResult;
    private String  deployDesc;
    private String  deployUserId;
    private String  deployUserName;
    private String  deployDate;
}
