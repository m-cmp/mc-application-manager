
package m.cmp.appManager.argocd.api.model;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Helm implements Serializable {

    private List<Map<String, String>> fileParameters = null;
    private boolean ignoreMissingValueFiles;
    private List<Map<String, Object>> parameters = null;
    private boolean passCredentials;
    private String releaseName;
    private boolean skipCrds;
    private List<String> valueFiles = null;
    private String values;
    private String version;
    private final static long serialVersionUID = 4825851675318729991L;

}
