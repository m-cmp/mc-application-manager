package kr.co.mcmp.http;

public class Response {
	private int code;	
	private String message;
	private Object data;
	
	public Response() {
		this.code = HttpStatus.NO_ERROR.value();
		this.message = HttpStatus.NO_ERROR.getReasonPhraseCode();
	}

	public Response(HttpStatus status, Boolean isSuccess) {
		this.code = status.value();
		this.message = status.getReasonPhraseCode();
		this.data = isSuccess;
	}
	
	public Response(HttpStatus status) {
		this(status, null);
	}

	public Response(HttpStatus status, Object data) {
		this.code = status.value();
		this.message = status.getReasonPhraseCode();
		this.data = data;
	}
	
	public Response(HttpStatus status, String message, Object data) {
		this.code = status.value();
		this.message = message;
		this.data = data;
	}
	
	public Response(int statusCode, String message) {
		this.code = statusCode;
		this.message = message;
	}
	
	public Response(Object data) {
		this();
		this.data = data;
	}
	
	public int getCode() {
		return code;
	}
	public void setCode(int code) {
		this.code = code;
	}
	
	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}
	
	public Object getData() {
		return data;
	}

	public void setData(Object data) {
		this.data = data;
	}
	
}
