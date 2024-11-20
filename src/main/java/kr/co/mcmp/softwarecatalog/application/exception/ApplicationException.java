package kr.co.mcmp.softwarecatalog.application.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;

@Getter
public class ApplicationException extends RuntimeException{

    private int code;
    private String message;
    private String detail;

    public ApplicationException(String message){
        this.message = message;
    }

    public ApplicationException(int code, String message){
        this.code = code;
        this.message = message;
    }

    public ApplicationException(int code, String message, String detail){
        this(code, message);
        this.detail = detail;
    }

    public ApplicationException(HttpStatus httpStatus){
        this(httpStatus.value(), httpStatus.getReasonPhrase());
    }

    public ApplicationException(HttpStatus httpStatus, String detail){
        this(httpStatus.value(), httpStatus.getReasonPhrase(), detail);
    }
}
