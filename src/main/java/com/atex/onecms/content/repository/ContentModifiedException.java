package com.atex.onecms.content.repository;

import com.atex.onecms.content.ContentVersionId;

/**
 * Thrown when content has been modified concurrently.
 */
public class ContentModifiedException extends Exception {
    private final ContentVersionId latestVersion;
    private final String committer;

    public ContentModifiedException(String message) {
        this(message, null, null);
    }

    public ContentModifiedException(String message, ContentVersionId latestVersion, String committer) {
        super(message);
        this.latestVersion = latestVersion;
        this.committer = committer;
    }

    public ContentVersionId getLatestVersion() { return latestVersion; }
    public String getCommitter() { return committer; }
}
