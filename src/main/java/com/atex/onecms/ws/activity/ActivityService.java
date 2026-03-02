package com.atex.onecms.ws.activity;

import com.atex.onecms.content.callback.CallbackException;
import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.ContentResult;
import com.atex.onecms.content.ContentVersionId;
import com.atex.onecms.content.ContentWrite;
import com.atex.onecms.content.ContentWriteBuilder;
import com.atex.onecms.content.DeleteResult;
import com.atex.onecms.content.SetAliasOperation;
import com.atex.onecms.content.Status;
import com.atex.onecms.content.repository.StorageException;
import com.atex.onecms.content.Subject;
import com.atex.onecms.content.aspects.Aspect;
import com.atex.onecms.content.repository.ContentModifiedException;
import org.springframework.stereotype.Service;

import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class ActivityService {

    private static final Logger LOGGER = Logger.getLogger(ActivityService.class.getName());

    private static final long WAIT_BETWEEN_TRIES = 10;
    private static final int NUMBER_OF_TRIES = 10;

    private final ContentManager contentManager;

    public ActivityService(ContentManager contentManager) {
        this.contentManager = contentManager;
    }

    public ActivityInfo get(Subject subject, String activityId) throws ActivityException {
        ContentVersionId contentId = resolve(subject, activityId);
        if (contentId == null) {
            return new ActivityInfo();
        }
        return get(contentId, subject);
    }

    public ActivityInfo write(Subject subject, String activityId, String userName,
                               String applicationId, ApplicationInfo applicationInfo)
            throws ActivityException {
        return retry(() -> doWrite(activityId, subject, userName, applicationId, applicationInfo),
                "update");
    }

    public ActivityInfo delete(Subject subject, String activityId, String userName,
                                String applicationId) throws ActivityException {
        return retry(() -> doDelete(activityId, userName, applicationId, subject), "delete");
    }

    private ActivityInfo doWrite(String activityId, Subject subject, String userName,
                                  String applicationId, ApplicationInfo applicationInfo)
            throws ActivityException, ContentModifiedException, StorageException, CallbackException {
        ContentVersionId activityInfoId = resolve(subject, activityId);
        if (activityInfoId == null) {
            UserActivities userActivities = new UserActivities(applicationId, applicationInfo);
            return create(activityId, userName, userActivities, subject);
        } else {
            return update(activityInfoId, subject, userName, applicationId, applicationInfo);
        }
    }

    private ActivityInfo create(String activityId, String userName,
                                 UserActivities userActivities, Subject subject)
            throws ActivityException {
        ActivityInfo activityInfo = new ActivityInfo();
        activityInfo.put(userName, userActivities);

        ContentWrite<ActivityInfo> write = new ContentWriteBuilder<ActivityInfo>()
                .mainAspect(new Aspect<>(ActivityInfo.ASPECT_NAME, activityInfo))
                .operation(new SetAliasOperation(SetAliasOperation.EXTERNAL_ID,
                        toExternalId(activityId)))
                .buildCreate();

        ContentResult<?> result = contentManager.create(write, subject);
        Status status = result.getStatus();
        if (!status.isSuccess()) {
            if (status.isConflict()) {
                // Alias already exists — signal conflict for retry
                throw new ActivityException(Status.CONFLICT, "Failed to create activity.");
            }
            throw new ActivityException(status, "Failed to create activity.");
        }
        return activityInfo;
    }

    private ActivityInfo update(ContentVersionId activityId, Subject subject,
                                 String userName, String applicationId,
                                 ApplicationInfo applicationInfo)
            throws ActivityException, ContentModifiedException, StorageException, CallbackException {
        ActivityInfo activityInfo = get(activityId, subject);
        UserActivities userActivities = activityInfo.get(userName);
        if (userActivities == null) {
            userActivities = new UserActivities(applicationId, applicationInfo);
            activityInfo.put(userName, userActivities);
        } else {
            userActivities.put(applicationId, applicationInfo);
        }

        ContentWrite<ActivityInfo> updateWrite = new ContentWriteBuilder<ActivityInfo>()
                .mainAspectData(activityInfo)
                .origin(activityId)
                .buildUpdate();
        ContentResult<?> result = contentManager.update(activityId.getContentId(),
                updateWrite, subject);

        Status status = result.getStatus();
        if (!status.isSuccess()) {
            throw new ActivityException(status, "Failed to update activity.");
        }
        return activityInfo;
    }

    private ActivityInfo doDelete(String activityId, String userName,
                                   String applicationId, Subject subject)
            throws ActivityException, ContentModifiedException, StorageException, CallbackException {
        ContentVersionId cvid = resolve(subject, activityId);
        if (cvid == null) {
            return new ActivityInfo();
        }
        ActivityInfo activities = get(cvid, subject);
        activities.remove(userName, applicationId);

        if (activities.isEmpty()) {
            DeleteResult deleteResult = contentManager.delete(cvid.getContentId(), cvid, subject);
            Status status = deleteResult.getStatus();
            if (!status.isSuccess()) {
                throw new ActivityException(status, "Failed to delete activity.");
            }
        } else {
            ContentWrite<ActivityInfo> updateWrite = new ContentWriteBuilder<ActivityInfo>()
                    .mainAspectData(activities)
                    .origin(cvid)
                    .buildUpdate();
            ContentResult<?> result = contentManager.update(cvid.getContentId(),
                    updateWrite, subject);
            Status status = result.getStatus();
            if (!status.isSuccess()) {
                throw new ActivityException(status, "Failed to delete activity.");
            }
        }
        return activities;
    }

    private ActivityInfo get(ContentVersionId activityId, Subject subject)
            throws ActivityException {
        ContentResult<ActivityInfo> result = contentManager.get(activityId,
                ActivityInfo.class, subject);
        if (!result.getStatus().isSuccess()) {
            throw new ActivityException(result.getStatus(),
                    "Failed to get activity '" + activityId + "'.");
        }
        return result.getContent().getContentData();
    }

    private ContentVersionId resolve(Subject subject, String activityId) {
        return contentManager.resolve(toExternalId(activityId), subject);
    }

    String toExternalId(String activityId) {
        return "activity:" + activityId;
    }

    private <T> T retry(Callable<T> task, String op) throws ActivityException {
        int triesLeft = NUMBER_OF_TRIES;
        while (true) {
            try {
                return task.call();
            } catch (ActivityException e) {
                if (!e.getStatus().isConflict()) {
                    throw e;
                }
                triesLeft--;
                if (triesLeft == 0) {
                    LOGGER.log(Level.FINE,
                            NUMBER_OF_TRIES + " attempts to retry failed at "
                                    + WAIT_BETWEEN_TRIES + "ms interval", e);
                    throw e;
                }
                try {
                    Thread.sleep(WAIT_BETWEEN_TRIES);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new ActivityException(Status.FAILURE,
                            "Interrupted during retry", ie);
                }
            } catch (Exception e) {
                throw new ActivityException(Status.FAILURE,
                        "Failed to " + op + " activity.", e);
            }
        }
    }
}
