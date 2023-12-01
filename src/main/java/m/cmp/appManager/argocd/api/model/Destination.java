package m.cmp.appManager.argocd.api.model;

import java.io.Serializable;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Destination implements Serializable {

    private String server;
    private String namespace;
    private String name;
    private final static long serialVersionUID = 510849228372283227L;

}
