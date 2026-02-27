package com.atex.onecms.content;

/**
 * Provides access to ContentManager and ContentWriteHelper.
 */
public class RepositoryClient {
    private final ContentManager contentManager;
    private final ContentWriteHelper contentWriteHelper;

    public RepositoryClient(ContentManager contentManager) {
        this.contentManager = contentManager;
        this.contentWriteHelper = contentDataType -> false;
    }

    public ContentManager getContentManager() {
        return contentManager;
    }

    public ContentWriteHelper getContentWriteHelper() {
        return contentWriteHelper;
    }
}
