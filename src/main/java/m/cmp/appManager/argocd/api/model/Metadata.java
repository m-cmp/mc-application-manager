
package m.cmp.appManager.argocd.api.model;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Metadata implements Serializable
{

    private Map<String, String> annotations;
    private String clusterName;
    private String creationTimestamp;
    private String deletionGracePeriodSeconds;
    private String deletionTimestamp;
    private List<String> finalizers = null;
    private String generateName;
    private String generation;
    private Map<String, String> labels;
    //private List<Map<String, Object>> managedFields = null;
    private String name;
    private String namespace;
    private List<Map<String, Object>> ownerReferences = null;
    private String resourceVersion;
    private String selfLink;
    private String uid;
    private final static long serialVersionUID = -2946772870834877363L;


}
