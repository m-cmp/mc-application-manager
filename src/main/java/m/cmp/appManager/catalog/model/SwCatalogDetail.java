package m.cmp.appManager.catalog.model;

import io.swagger.v3.oas.annotations.tags.Tag;
import m.cmp.appManager.jenkins.model.JenkinsWorkflow;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Tag(name = "ApplicationCatalog", description = "SW 카탈로그 정보 상세")
public class SwCatalogDetail extends SwCatalog {

    public String scReference;

    public String scDescription;

    public String scCategory;

    public List<JenkinsWorkflow> workflows;

    public List<SwCatalog> relationSwCatalog;

}
