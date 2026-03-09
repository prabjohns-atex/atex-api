package com.atex.onecms.ws.activity;

import com.atex.desk.api.repository.AppUserRepository;
import com.atex.desk.api.entity.AppUser;
import com.atex.desk.api.service.ObjectCacheService;
import com.atex.onecms.content.Status;
import com.atex.onecms.content.Subject;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class ActivityServiceSecured {

    private static final String CACHE_PRINCIPAL_MAP = "principalMap";

    private final ActivityService activityService;
    private final AppUserRepository appUserRepository;
    private final ObjectCacheService cacheService;

    public ActivityServiceSecured(ActivityService activityService,
                                   AppUserRepository appUserRepository,
                                   ObjectCacheService cacheService) {
        this.activityService = activityService;
        this.appUserRepository = appUserRepository;
        this.cacheService = cacheService;
    }

    @PostConstruct
    void initCaches() {
        cacheService.configure(CACHE_PRINCIPAL_MAP, 5 * 60 * 1000L, 200);
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
        String loginName = subject.getPrincipalId();
        if (!loginName.equals(userName)) {
            // The client may send the numeric principalId (from registeredusers table)
            // while the JWT subject is the login name — accept either
            String cachedPid = cacheService.get(CACHE_PRINCIPAL_MAP, loginName);
            if (cachedPid == null) {
                cachedPid = appUserRepository.findByLoginName(loginName)
                        .map(AppUser::getPrincipalId)
                        .orElse(null);
                if (cachedPid != null) {
                    cacheService.put(CACHE_PRINCIPAL_MAP, loginName, cachedPid);
                }
            }
            boolean matches = userName.equals(cachedPid);
            if (!matches) {
                throw new ActivityException(Status.NOT_AUTHENTICATED,
                        String.format("%s is not allowed to modify activity belonging to %s.",
                                loginName, userName));
            }
        }
    }

    private void assertLoggedIn(Subject subject) throws ActivityException {
        if (subject == null) {
            throw new ActivityException(Status.NOT_AUTHENTICATED, "User not authenticated.");
        }
    }
}
