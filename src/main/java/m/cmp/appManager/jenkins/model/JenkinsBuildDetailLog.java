package m.cmp.appManager.jenkins.model;

import lombok.Getter;
import lombok.ToString;


@Getter
@ToString
public class JenkinsBuildDetailLog {

   private String nodeId;

   private String nodeStatus;

   private Long length;

   private Boolean hasMore;

   private String text;

   private String consoleUrl;

}
