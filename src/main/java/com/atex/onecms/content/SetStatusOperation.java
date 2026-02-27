package com.atex.onecms.content;

import java.util.HashMap;
import java.util.Map;

/**
 * Operation to set workflow status on content.
 */
public class SetStatusOperation implements ContentOperation {

    private final String statusId;
    private final String comment;
    private final Map<String, String> attributes;

    public SetStatusOperation(String statusId) {
        this(statusId, null, null);
    }

    public SetStatusOperation(String statusId, String comment) {
        this(statusId, comment, null);
    }

    public SetStatusOperation(String statusId, String comment, Map<String, String> attributes) {
        this.statusId = statusId;
        this.comment = comment;
        this.attributes = attributes != null ? new HashMap<>(attributes) : new HashMap<>();
    }

    public String getStatusId() { return statusId; }
    public String getComment() { return comment; }
    public Map<String, String> getAttributes() { return attributes; }
}
