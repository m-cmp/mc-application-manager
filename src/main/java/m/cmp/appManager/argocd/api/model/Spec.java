
package m.cmp.appManager.argocd.api.model;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Spec implements Serializable
{

    private Destination destination;
    private List<Map<String, String>> ignoreDifferences = null;
    private List<Map<String, String>> info = null;
    private String project;
    private String revisionHistoryLimit;
    private Source source;
    private SyncPolicy syncPolicy;
    private final static long serialVersionUID = 8569851094266934597L;
 
 
}
