package com.atex.onecms.app.dam.util;
public class WebServiceFailedResponse {
    private int statusCode;
    private String message;
    public WebServiceFailedResponse() {}
    public WebServiceFailedResponse(int statusCode, String message) { this.statusCode = statusCode; this.message = message; }
    public int getStatusCode() { return statusCode; }
    public void setStatusCode(int v) { this.statusCode = v; }
    public String getMessage() { return message; }
    public void setMessage(String v) { this.message = v; }
}
