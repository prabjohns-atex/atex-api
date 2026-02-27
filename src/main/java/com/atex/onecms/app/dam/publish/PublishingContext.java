package com.atex.onecms.app.dam.publish;

import com.atex.onecms.app.dam.publish.config.RemoteConfigBean;
import com.atex.onecms.content.ContentId;
import com.atex.onecms.content.Subject;
import com.polopoly.user.server.Caller;

public interface PublishingContext {
    RemoteConfigBean getRemoteConfiguration();
    DamPublisherConfiguration getPublishConfiguration();
    ContentId getSourceId();
    String getSourceWorkspaceId();
    Caller getCaller();

    default Subject getSubject() {
        Caller c = getCaller();
        return c != null ? Subject.of(c.getLoginName()) : Subject.NOBODY_CALLER;
    }
}
