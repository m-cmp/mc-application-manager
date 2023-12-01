package m.cmp.appManager.argocd.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;

@Getter
public class ArgocdException extends RuntimeException {

	private static final long serialVersionUID = 8544789430380652973L;

	private int code;
	private String messag;
	private String detail;

	public ArgocdException(int code, String message) {
		this.code = code;
		this.messag = message;
	}
		
	public ArgocdException(int code, String message, String detail) {
		this(code, message);
		this.detail = detail;
	}

	public ArgocdException(HttpStatus httpStatus) {
		this(httpStatus.value(), httpStatus.getReasonPhrase());
	}

	public ArgocdException(HttpStatus httpStatus, String detail) {
		this(httpStatus.value(), httpStatus.getReasonPhrase(), detail);
	}

}
