package kr.co.mcmp.externalrepo.model;

import lombok.Data;

@Data
public class ArtifactHubRespository {

    public String repository_id;
    public String name;
    public String display_name;
    public String url;
    public Integer kind;
    public boolean erified_publisher;
    public String official;
    public boolean disabled;
    public boolean scanner_disabled;
    public String digest;
    public Long last_tracking_ts;
    public String user_alias;

}
