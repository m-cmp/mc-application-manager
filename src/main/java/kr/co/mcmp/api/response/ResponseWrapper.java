package kr.co.mcmp.api.response;

import lombok.Getter;

import java.io.Serializable;

@Getter
public class ResponseWrapper<T> implements Serializable {
	
	private static final long serialVersionUID = -1745006582949878939L;

	private int code;
	private String message;
	private String detail;
	private T data;
	
	public ResponseWrapper() {
		this(ResponseCode.OK);
	}

    public ResponseWrapper(ResponseCode status){
        this.code 	= status.getCode();
        this.message = status.getMessage();
    }

	public ResponseWrapper(T data) {
		this();
		this.data = data;
	}

    public ResponseWrapper(ResponseCode status, String detail){
    	this(status);
    	this.detail = detail;
    }
    
    public ResponseWrapper(int code, String message, String detail){
    	this.code = code;
    	this.message = message;
    	this.detail = detail;
    }
    
}
