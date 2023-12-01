
package m.cmp.appManager.argocd.api.model;

import java.io.Serializable;

import javax.annotation.Generated;

@Generated("jsonschema2pojo")
public class Health implements Serializable
{

    private String message;
    private String status;
    private final static long serialVersionUID = -4009762909783457992L;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

}
