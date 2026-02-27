package com.atex.onecms.content;

/**
 * Status codes used in {@link ContentResult}.
 */
public enum Status {
    OK(200, 20000, StatusType.OK),
    NO_EFFECT(200, 20001, StatusType.OK),
    CREATED(201, 20100, StatusType.OK),
    BAD_REQUEST(400, 40001, StatusType.ERROR),
    NOT_AUTHENTICATED(401, 40100, StatusType.FORBIDDEN),
    FORBIDDEN(403, 40300, StatusType.FORBIDDEN),
    NOT_FOUND(404, 40400, StatusType.NOT_FOUND),
    VERSION_NOT_FOUND(404, 40403, StatusType.NOT_FOUND),
    VARIANT_NOT_FOUND(404, 40460, StatusType.NOT_FOUND),
    NOT_FOUND_IN_VARIANT(404, 40461, StatusType.NOT_FOUND),
    INPUT_TEMPLATE_MAPPING_NOT_FOUND(404, 40480, StatusType.NOT_FOUND),
    CONFLICT(409, 40900, StatusType.CONFLICT),
    ALIAS_EXISTS(409, 40910, StatusType.CONFLICT),
    PRECONDITION_FAILED(412, 41200, StatusType.ERROR),
    REMOVED(204, 20401, StatusType.OK),
    FAILURE(500, 50000, StatusType.ERROR),
    NOT_IMPLEMENTED(501, 50100, StatusType.ERROR);

    private final int httpCode;
    private final int detailCode;
    private final StatusType statusType;

    Status(int httpCode, int detailCode, StatusType statusType) {
        this.httpCode = httpCode;
        this.detailCode = detailCode;
        this.statusType = statusType;
    }

    public boolean isNotFound() {
        return statusType == StatusType.NOT_FOUND;
    }

    public boolean isOk() {
        return isSuccess();
    }

    public boolean isSuccess() {
        return statusType == StatusType.OK;
    }

    public boolean isError() {
        return statusType == StatusType.ERROR;
    }

    public boolean isForbidden() {
        return statusType == StatusType.FORBIDDEN;
    }

    public boolean isConflict() {
        return statusType == StatusType.CONFLICT;
    }

    public int getHttpCode() {
        return httpCode;
    }

    public int getDetailCode() {
        return detailCode;
    }
}

enum StatusType {
    OK, NOT_FOUND, FORBIDDEN, CONFLICT, ERROR
}
