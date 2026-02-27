package com.atex.onecms.app.dam.publish;

import com.atex.onecms.app.dam.publish.config.RemoteConfigBean;
import com.atex.onecms.content.ContentId;
import com.polopoly.user.server.Caller;

public class PublishingContextImpl implements PublishingContext {
    private final RemoteConfigBean remoteConfig;
    private final DamPublisherConfiguration publishConfig;
    private final ContentId sourceId;
    private final String workspaceId;
    private final Caller caller;

    public PublishingContextImpl(RemoteConfigBean remoteConfig, DamPublisherConfiguration publishConfig,
                                 ContentId sourceId, String workspaceId, Caller caller) {
        this.remoteConfig = remoteConfig;
        this.publishConfig = publishConfig;
        this.sourceId = sourceId;
        this.workspaceId = workspaceId;
        this.caller = caller;
    }

    @Override public RemoteConfigBean getRemoteConfiguration() { return remoteConfig; }
    @Override public DamPublisherConfiguration getPublishConfiguration() { return publishConfig; }
    @Override public ContentId getSourceId() { return sourceId; }
    @Override public String getSourceWorkspaceId() { return workspaceId; }
    @Override public Caller getCaller() { return caller; }
}
