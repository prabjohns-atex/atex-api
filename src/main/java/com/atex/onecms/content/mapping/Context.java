package com.atex.onecms.content.mapping;
import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.files.FileService;
/**
 * Context for a ContentComposer. Supplements the composer with configuration
 * and classes for interacting with the system.
 */
public class Context<C> {
    private final ContentManager contentManager;
    private final C config;
    private final ContentComposerUtil contentComposerUtil;
    private final FileService fileService;
    public Context(ContentManager contentManager, C config,
                   ContentComposerUtil contentComposerUtil, FileService fileService) {
        this.contentManager = contentManager;
        this.config = config;
        this.contentComposerUtil = contentComposerUtil;
        this.fileService = fileService;
    }
    public ContentManager getContentManager() {
        return contentManager;
    }
    public C getConfig() {
        return config;
    }
    public ContentComposerUtil getContentComposerUtil() {
        return contentComposerUtil;
    }
    public FileService getFileService() {
        return fileService;
    }
}
