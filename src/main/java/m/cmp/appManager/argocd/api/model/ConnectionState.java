package m.cmp.appManager.argocd.api.model;

import java.io.Serializable;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConnectionState implements Serializable {

	private static final long serialVersionUID = -4316499922946540324L;

	//private String attemptedAt;
	private String message;
	private String status;


}