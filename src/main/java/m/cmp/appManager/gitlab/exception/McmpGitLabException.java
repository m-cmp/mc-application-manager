package m.cmp.appManager.gitlab.exception;

import m.cmp.appManager.api.response.ResponseCode;
import lombok.Getter;

@Getter
public class McmpGitLabException extends RuntimeException {

	private static final long serialVersionUID = 8544789430380652973L;

	private int code;
	private String messag;
	private String detail;

	public McmpGitLabException(int code, String message) {
		this.code = code;
		this.messag = message;
	}
		
	public McmpGitLabException(int code, String message, String detail) {
		this(code, message);
		this.detail = detail;
	}

	public McmpGitLabException(ResponseCode responseCode) {
    	this.code = responseCode.getCode();
    	this.messag = responseCode.getMessage();
    }

    public McmpGitLabException(ResponseCode responseCode, String detail) {
    	this(responseCode);
        this.detail = detail;
    }

}