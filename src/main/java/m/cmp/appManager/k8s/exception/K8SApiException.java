package m.cmp.appManager.k8s.exception;

import m.cmp.appManager.api.response.ResponseCode;
import lombok.Getter;

@Getter
public class K8SApiException extends RuntimeException {

	private static final long serialVersionUID = -4882100845341169597L;

	private int code;
	private String messag;
	private String detail;

	public K8SApiException(int code, String message) {
		this.code = code;
		this.messag = message;
	}

	public K8SApiException(int code, String message, String detail) {
		this(code, message);
		this.detail = detail;
	}
	
    public K8SApiException(ResponseCode responseCode) {
    	this(responseCode.getCode(), responseCode.getMessage());
    }

    public K8SApiException(ResponseCode responseCode, String detail) {
    	this(responseCode);
        this.detail = detail;
    }


}
