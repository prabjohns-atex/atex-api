package com.atex.onecms.preview;

import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.Subject;

/**
 * Simple implementation of {@link PreviewContext}.
 */
public class PreviewContextImpl<CONFIG> implements PreviewContext<CONFIG> {

    private final String channelName;
    private final CONFIG configuration;
    private final ContentManager contentManager;
    private final String userName;
    private final Subject subject;

    public PreviewContextImpl(String channelName, CONFIG configuration,
                              ContentManager contentManager, String userName,
                              Subject subject) {
        this.channelName = channelName;
        this.configuration = configuration;
        this.contentManager = contentManager;
        this.userName = userName;
        this.subject = subject;
    }

    @Override public String getChannelName() { return channelName; }
    @Override public CONFIG getConfiguration() { return configuration; }
    @Override public ContentManager getContentManager() { return contentManager; }
    @Override public String getUserName() { return userName; }
    @Override public Subject getSubject() { return subject; }
}
