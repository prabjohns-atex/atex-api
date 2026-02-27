package com.atex.onecms.app.dam.ws;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown by DAM endpoints. Converted to HTTP error responses
 * by ContentApiExceptionHandler.
 */
public class ContentApiException extends RuntimeException {

    private final HttpStatus httpStatus;
    private final int detailCode;

    public ContentApiException(String message, HttpStatus httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
        this.detailCode = httpStatus.value() * 100;
    }

    public ContentApiException(String message, HttpStatus httpStatus, int detailCode) {
        super(message);
        this.httpStatus = httpStatus;
        this.detailCode = detailCode;
    }

    public ContentApiException(String message, HttpStatus httpStatus, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.detailCode = httpStatus.value() * 100;
    }

    public ContentApiException(String message, int detailCode, int httpCode) {
        super(message);
        this.httpStatus = HttpStatus.valueOf(httpCode);
        this.detailCode = detailCode;
    }

    public ContentApiException(String message, int detailCode, int httpCode, Throwable cause) {
        super(message, cause);
        this.httpStatus = HttpStatus.valueOf(httpCode);
        this.detailCode = detailCode;
    }

    public HttpStatus getHttpStatus() { return httpStatus; }
    public int getDetailCode() { return detailCode; }

    // Factory methods matching the old ErrorFactory patterns
    public static ContentApiException badRequest(String message) {
        return new ContentApiException(message, HttpStatus.BAD_REQUEST);
    }

    public static ContentApiException notFound(String message) {
        return new ContentApiException(message, HttpStatus.NOT_FOUND);
    }

    public static ContentApiException notFound(Object id) {
        return new ContentApiException("Not found: " + id, HttpStatus.NOT_FOUND);
    }

    public static ContentApiException forbidden(String message) {
        return new ContentApiException(message, HttpStatus.FORBIDDEN);
    }

    public static ContentApiException internal(String message) {
        return new ContentApiException(message, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public static ContentApiException internal(String message, Throwable cause) {
        return new ContentApiException(message, HttpStatus.INTERNAL_SERVER_ERROR, cause);
    }

    public static ContentApiException error(String message, int detailCode, int httpCode) {
        return new ContentApiException(message, detailCode, httpCode);
    }

    public static ContentApiException error(String message, com.atex.onecms.content.Status status) {
        return new ContentApiException(message, status.getDetailCode(), status.getHttpCode());
    }
}
