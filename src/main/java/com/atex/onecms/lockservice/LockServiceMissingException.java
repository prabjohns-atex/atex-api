package com.atex.onecms.lockservice;

public class LockServiceMissingException extends Exception {
    public LockServiceMissingException(String message) {
        super(message);
    }

    public LockServiceMissingException(String message, Throwable cause) {
        super(message, cause);
    }
}
