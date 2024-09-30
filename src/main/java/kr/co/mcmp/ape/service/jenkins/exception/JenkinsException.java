package kr.co.mcmp.ape.service.jenkins.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;

@Getter
public class JenkinsException  extends RuntimeException {

	private static final long serialVersionUID = 139760879938122059L;
	
	private int code;
	private String messag;
	private String detail;

	public JenkinsException(int code, String message) {
		this.code = code;
		this.messag = message;
	}
		
	public JenkinsException(int code, String message, String detail) {
		this(code, message);
		this.detail = detail;
	}

	public JenkinsException(HttpStatus httpStatus) {
		this(httpStatus.value(), httpStatus.getReasonPhrase());
	}

	public JenkinsException(HttpStatus httpStatus, String detail) {
		this(httpStatus.value(), httpStatus.getReasonPhrase(), detail);
	}

}
