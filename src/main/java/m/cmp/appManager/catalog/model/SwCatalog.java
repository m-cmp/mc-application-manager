package m.cmp.appManager.catalog.model;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Tag(name = "ApplicationCatalog", description = "SW 카탈로그 정보 기본")
public class SwCatalog {

    public Integer scIdx;
    public String scTitle;
    public String scVersion;
    public String scIcon;
    public String scSummary;
    public String scLocation;

}
