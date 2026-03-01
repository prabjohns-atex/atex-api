package com.atex.onecms.app.dam.publishingschedule;

import com.atex.onecms.content.ContentId;
import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.Subject;

/**
 * Publishing schedule utilities.
 * Stub implementation — returns null (no scheduling support yet).
 */
public class DamPublishingScheduleUtils {

    private final ContentManager contentManager;
    private final Subject subject;

    public DamPublishingScheduleUtils(ContentManager contentManager, Object modelDomain,
                                       Subject subject, Object searchClient) {
        this.contentManager = contentManager;
        this.subject = subject;
    }

    public ContentId implementPlan(ContentId contentId, long pubDate) {
        throw new UnsupportedOperationException("Publishing schedule not implemented");
    }
}

