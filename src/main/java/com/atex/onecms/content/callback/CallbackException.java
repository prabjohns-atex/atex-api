package com.atex.onecms.content.callback;

import com.atex.onecms.content.ErrorResult;
import com.atex.onecms.content.Status;

/**
 * Used to report a failure in a lifecycle hook.
 */
public class CallbackException extends RuntimeException {

    private final ErrorResult errorResult;

    public CallbackException(String message) {
        this(message, null, null);
    }

    public CallbackException(String message, Throwable cause) {
        this(message, cause, null);
    }

    public CallbackException(String message, ErrorResult errorResult) {
        this(message, null, errorResult);
    }

    public CallbackException(String message, Throwable cause, ErrorResult errorResult) {
        super(message, cause);
        this.errorResult = errorResult == null ? new ErrorResult(null) : errorResult;
    }

    public Status getStatus() {
        return errorResult.getStatus();
    }

    public ErrorResult getErrorResult() {
        return errorResult;
    }

    public static CallbackExceptionBuilder message(String message) {
        return new CallbackExceptionBuilder().message(message);
    }

    public static CallbackExceptionBuilder status(final Status status) {
        return new CallbackExceptionBuilder().status(status);
    }

    public static class CallbackExceptionBuilder {
        private Throwable cause;
        private String message;
        private ErrorResult errorResult;

        public CallbackExceptionBuilder cause(Throwable cause) {
            this.cause = cause;
            return this;
        }

        public CallbackExceptionBuilder message(String message) {
            this.message = message;
            return this;
        }

        public CallbackExceptionBuilder status(final Status status) {
            this.errorResult = new ErrorResult(status);
            return this;
        }

        public CallbackExceptionBuilder result(ErrorResult errorResult) {
            this.errorResult = errorResult;
            return this;
        }

        public CallbackException build() {
            ErrorResult actualResult = errorResult != null ? errorResult
                : new ErrorResult(Status.FAILURE, message, null);
            String actualMessage = message != null ? message
                : (errorResult != null ? errorResult.getMessage() : null);
            return new CallbackException(actualMessage, cause, actualResult);
        }

        public void raise() throws CallbackException {
            throw build();
        }
    }
}
