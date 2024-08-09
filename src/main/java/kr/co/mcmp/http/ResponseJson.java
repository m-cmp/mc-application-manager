package kr.co.mcmp.http;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class ResponseJson {
	private int code;	
	private String message;
	private JsonElement data;
	
	public ResponseJson() {
		this.code = HttpStatus.NO_ERROR.value();
		this.message = HttpStatus.NO_ERROR.getReasonPhraseCode();
	}

	public ResponseJson(HttpStatus status) {
		this(status, null);
	}

	public ResponseJson(HttpStatus status, JsonElement data) {
		this.code = status.value();
		this.message = status.getReasonPhraseCode();
		this.data = data;
	}
	
	public ResponseJson(JsonElement data) {
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
	
	public JsonElement getData() {
		return data;
	}

	public void setData(JsonElement data) {
		this.data = data;
	}
	
	public String toJson() {
		JsonObject jsonObject = new JsonObject();
		
		jsonObject.addProperty("code", getCode());
		if(getMessage() != null) {
			jsonObject.addProperty("message", getMessage());
		}
		
		if(getData() != null) {
			jsonObject.add("data", getData());		
		}		
		return jsonObject.toString();
	}
	
}
