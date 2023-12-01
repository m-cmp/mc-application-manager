package m.cmp.appManager.nexus.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;

@Getter
public class NexusException extends RuntimeException {

	private static final long serialVersionUID = -3088444536163499090L;
	private int code;
	private String messag;
	private String detail;

	public NexusException(int code, String message) {
		this.code = code;
		this.messag = message;
	}
		
	public NexusException(int code, String message, String detail) {
		this(code, message);
		this.detail = detail;
	}

	public NexusException(HttpStatus httpStatus) {
		this(httpStatus.value(), httpStatus.getReasonPhrase());
	}

	public NexusException(HttpStatus httpStatus, String detail) {
		this(httpStatus.value(), httpStatus.getReasonPhrase(), detail);
	}

}
