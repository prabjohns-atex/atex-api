package com.atex.onecms.ws.activity;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-user activity container. Keys are application IDs.
 */
public class UserActivities {

    private Map<String, ApplicationInfo> applications = new HashMap<>();

    public UserActivities() {}

    public UserActivities(String applicationId, ApplicationInfo applicationInfo) {
        put(applicationId, applicationInfo);
    }

    public ApplicationInfo put(String applicationId, ApplicationInfo applicationInfo) {
        return applications.put(applicationId, applicationInfo);
    }

    public boolean remove(String applicationId) {
        return applications.remove(applicationId) != null;
    }

    public Map<String, ApplicationInfo> getApplications() { return applications; }
    public void setApplications(Map<String, ApplicationInfo> applications) {
        this.applications = new HashMap<>(applications);
    }
}
