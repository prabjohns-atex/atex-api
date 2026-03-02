package com.atex.onecms.ws.activity;

import com.atex.onecms.content.Status;

public class ActivityException extends Exception {

    private final Status status;

    public ActivityException(Status status, String message) {
        super(message);
        this.status = status;
    }

    public ActivityException(Status status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public Status getStatus() { return status; }

    @Override
    public String getMessage() {
        return String.format("%s [Status: '%s']", super.getMessage(), status);
    }
}
