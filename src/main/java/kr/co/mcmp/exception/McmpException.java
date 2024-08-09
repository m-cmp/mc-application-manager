package kr.co.mcmp.exception;

import kr.co.mcmp.api.response.ResponseCode;
import lombok.Getter;

@Getter
public class McmpException extends RuntimeException {

	private static final long serialVersionUID = 5652052703305105438L;
	
	ResponseCode responseCode = ResponseCode.UNKNOWN_ERROR;
    private String detail;

    public McmpException() {
    	this(ResponseCode.UNKNOWN_ERROR);
    }

    public McmpException(ResponseCode responseCode) {
    	this.responseCode = responseCode;
    }

    public McmpException(ResponseCode responseCode, String detail) {
    	this.responseCode = responseCode;
        this.detail = detail;
    }
    
    public McmpException(String detail) {
    	this.detail = detail;
    }
    
    public McmpException(Throwable cause){
        super(cause);
    	this.detail = cause.getMessage();
    }

}
