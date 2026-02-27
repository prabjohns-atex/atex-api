package com.atex.onecms.content.lifecycle;

import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.Subject;
import com.atex.onecms.content.files.FileService;

/**
 * Context provided to pre-store lifecycle hooks.
 *
 * @param <CONFIG> the configuration type
 */
public class LifecycleContextPreStore<CONFIG> {
    private final ContentManager contentManager;
    private final Subject subject;
    private final CONFIG config;
    private final FileService fileService;

    public LifecycleContextPreStore(ContentManager contentManager, Subject subject, CONFIG config) {
        this(contentManager, subject, config, null);
    }

    public LifecycleContextPreStore(ContentManager contentManager, Subject subject,
                                     CONFIG config, FileService fileService) {
        this.contentManager = contentManager;
        this.subject = subject;
        this.config = config;
        this.fileService = fileService;
    }

    public ContentManager getContentManager() { return contentManager; }
    public Subject getSubject() { return subject; }
    public CONFIG getConfig() { return config; }
    public FileService getFileService() { return fileService; }
}
