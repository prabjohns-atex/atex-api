package com.atex.onecms.ws.activity;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-application activity entry with server-generated timestamp.
 */
public class ApplicationInfo {

    private long timestamp;
    private String activity;
    private Map<String, String> params = new HashMap<>();

    public ApplicationInfo() {}

    public ApplicationInfo(long timestamp, String activity, Map<String, String> params) {
        this.timestamp = timestamp;
        this.activity = activity;
        this.params = params;
    }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getActivity() { return activity; }
    public void setActivity(String activity) { this.activity = activity; }

    public Map<String, String> getParams() { return params; }
    public void setParams(Map<String, String> params) { this.params = params; }
}
