package kr.co.mcmp.exception;

import kr.co.mcmp.api.response.ResponseCode;
import lombok.Getter;

@Getter
public class AlreadyExistsException extends RuntimeException {

	private static final long serialVersionUID = -7883507560385080138L;
	
	ResponseCode responseCode = ResponseCode.ALREADY_EXISTS;
    private String detail;

    public AlreadyExistsException() {
    	this(ResponseCode.ALREADY_EXISTS);
    }

    public AlreadyExistsException(ResponseCode responseCode) {
    	this.responseCode = responseCode;
    }

    public AlreadyExistsException(ResponseCode responseCode, String detail) {
    	this.responseCode = responseCode;
        this.detail = detail;
    }
    
    public AlreadyExistsException(String detail) {
    	this.detail = detail;
    }
    
    public AlreadyExistsException(Throwable cause){
        super(cause);
    	this.detail = cause.getMessage();
    }

}
