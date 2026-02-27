package com.atex.onecms.ws.service;
public class ErrorResponseException extends RuntimeException {
    private int statusCode;
    public ErrorResponseException(String message) { super(message); }
    public ErrorResponseException(String message, int statusCode) { super(message); this.statusCode = statusCode; }
    public int getStatusCode() { return statusCode; }
}
