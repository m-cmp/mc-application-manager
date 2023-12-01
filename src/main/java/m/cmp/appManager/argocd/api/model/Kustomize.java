
package m.cmp.appManager.argocd.api.model;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Kustomize implements Serializable
{

    private Map<String, String> commonAnnotations;
    private Map<String, String> commonLabels;
    private boolean forceCommonAnnotations;
    private boolean forceCommonLabels;
    private List<String> images = null;
    private String namePrefix;
    private String nameSuffix;
    private String version;
    private final static long serialVersionUID = -8816458418608383594L;


}
