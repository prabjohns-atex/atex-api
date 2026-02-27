package com.atex.onecms.content;

import java.util.List;

/**
 * Contains version metadata for a content version.
 */
public class ContentVersionInfo {
    private final ContentVersionId version;
    private final long creationTime;
    private final String creatorId;
    private final List<String> views;

    public ContentVersionInfo(final ContentVersionId version, final long creationTime,
                              final String creatorId, final List<String> views) {
        this.version = version;
        this.creationTime = creationTime;
        this.creatorId = creatorId;
        this.views = views;
    }

    public ContentVersionId getVersion() { return version; }
    public long getCreationTime() { return creationTime; }
    public String getCreatorId() { return creatorId; }
    public List<String> getViews() { return views; }

    @Override
    public String toString() {
        return String.format("ContentVersionInfo [version: %s, creationTime: %d, creatorId: %s, views: %s]",
                             version, creationTime, creatorId, views);
    }
}
