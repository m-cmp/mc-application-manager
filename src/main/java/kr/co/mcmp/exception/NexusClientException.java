package kr.co.mcmp.exception;

import kr.co.mcmp.response.ResponseCode;
import lombok.Getter;

@Getter
public class NexusClientException extends RuntimeException {

    private static final long serialVersionUID = -7883846160384968138L;

    ResponseCode responseCode = ResponseCode.NEXUS_CLIENT_ERROR;
    private String detail;

    public NexusClientException() {
        this(ResponseCode.NEXUS_CLIENT_ERROR);
    }

    public NexusClientException(ResponseCode responseCode) {
        this.responseCode = responseCode;
    }

    public NexusClientException(ResponseCode responseCode, String detail) {
        this.responseCode = responseCode;
        this.detail = detail;
    }

    public NexusClientException(String detail) {
        this.detail = detail;
    }

    public NexusClientException(Throwable cause) {
        super(cause);
        this.detail = cause.getMessage();
    }
}
