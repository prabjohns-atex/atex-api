package com.atex.onecms.ws.activity;

import com.atex.onecms.content.aspects.annotations.AspectDefinition;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Top-level activity container. Stored as content with aspect name "atex.activity.Activities".
 * Keys are user IDs (login names or principal IDs).
 */
@AspectDefinition(value = ActivityInfo.ASPECT_NAME)
public class ActivityInfo {

    public static final String ASPECT_NAME = "atex.activity.Activities";

    Map<String, UserActivities> users = new HashMap<>();

    public void put(String userName, UserActivities userActivities) {
        users.put(userName, userActivities);
    }

    public UserActivities get(String userName) {
        return users.get(userName);
    }

    public boolean remove(String userName, String applicationId) {
        UserActivities userActivities = users.get(userName);
        if (userActivities == null) {
            return false;
        }
        return userActivities.remove(applicationId);
    }

    public Map<String, UserActivities> getUsers() { return users; }
    public void setUsers(Map<String, UserActivities> users) { this.users = users; }

    public boolean isEmpty() {
        if (users != null) {
            return users.values()
                    .stream()
                    .filter(Objects::nonNull)
                    .map(UserActivities::getApplications)
                    .filter(Objects::nonNull)
                    .allMatch(Map::isEmpty);
        }
        return true;
    }
}
