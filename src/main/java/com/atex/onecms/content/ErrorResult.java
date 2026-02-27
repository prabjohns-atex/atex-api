package com.atex.onecms.content;

import java.util.Collections;
import java.util.Map;

/**
 * Error result with status, message and extra info.
 */
public class ErrorResult {
    private final Status status;
    private final String message;
    private final Map<String, Object> extraInfo;

    public ErrorResult(Status status) {
        this(status, null, Collections.emptyMap());
    }

    public ErrorResult(Status status, String message, Map<String, Object> extraInfo) {
        this.status = status != null ? status : Status.FAILURE;
        this.message = message;
        this.extraInfo = extraInfo != null ? extraInfo : Collections.emptyMap();
    }

    public Status getStatus() { return status; }
    public String getMessage() { return message; }
    public Map<String, Object> getExtraInfo() { return extraInfo; }
}
