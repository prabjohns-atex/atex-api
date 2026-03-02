package com.atex.onecms.ws.activity;

import com.atex.onecms.content.Status;
import com.atex.onecms.content.Subject;
import org.springframework.stereotype.Component;

@Component
public class ActivityServiceSecured {

    private final ActivityService activityService;

    public ActivityServiceSecured(ActivityService activityService) {
        this.activityService = activityService;
    }

    public ActivityInfo get(Subject subject, String activityId) throws ActivityException {
        assertLoggedIn(subject);
        return activityService.get(subject, activityId);
    }

    public ActivityInfo write(Subject subject, String activityId, String userName,
                               String applicationId, ApplicationInfo applicationInfo)
            throws ActivityException {
        assertWritePermission(subject, userName);
        return activityService.write(subject, activityId, userName, applicationId, applicationInfo);
    }

    public ActivityInfo delete(Subject subject, String activityId, String userName,
                                String applicationId) throws ActivityException {
        // Allowed to delete others' activities — easier than figuring out permissions
        assertLoggedIn(subject);
        return activityService.delete(subject, activityId, userName, applicationId);
    }

    private void assertWritePermission(Subject subject, String userName)
            throws ActivityException {
        if (subject == null) {
            throw new ActivityException(Status.NOT_AUTHENTICATED, "User not authenticated.");
        }
        if (userName == null) {
            throw new ActivityException(Status.BAD_REQUEST, "User name may not be null.");
        }
        if (!subject.getPrincipalId().equals(userName)) {
            throw new ActivityException(Status.NOT_AUTHENTICATED,
                    String.format("%s is not allowed to modify activity belonging to %s.",
                            subject.getPrincipalId(), userName));
        }
    }

    private void assertLoggedIn(Subject subject) throws ActivityException {
        if (subject == null) {
            throw new ActivityException(Status.NOT_AUTHENTICATED, "User not authenticated.");
        }
    }
}
