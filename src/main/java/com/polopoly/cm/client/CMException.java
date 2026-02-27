package com.polopoly.cm.client;

/**
 * Base checked exception for CM system.
 */
public class CMException extends Exception {

    public CMException() {
        super();
    }

    public CMException(String message) {
        super(message);
    }

    public CMException(String message, Throwable cause) {
        super(message, cause);
    }

    public CMException(Throwable cause) {
        super(cause);
    }
}
