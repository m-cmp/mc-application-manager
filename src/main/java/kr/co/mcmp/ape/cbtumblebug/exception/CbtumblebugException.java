package kr.co.mcmp.ape.cbtumblebug.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;

@Getter
public class CbtumblebugException extends RuntimeException{

    private int code;
    private String message;
    private String detail;

    public CbtumblebugException(String message){
        this.message = message;
    }

    public CbtumblebugException(int code, String message){
        this.code = code;
        this.message = message;
    }

    public CbtumblebugException(int code, String message, String detail){
        this(code, message);
        this.detail = detail;
    }

    public CbtumblebugException(HttpStatus httpStatus){
        this(httpStatus.value(), httpStatus.getReasonPhrase());
    }

    public CbtumblebugException(HttpStatus httpStatus, String detail){
        this(httpStatus.value(), httpStatus.getReasonPhrase(), detail);
    }

}
