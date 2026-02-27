package com.atex.onecms.content;

import java.util.List;

/**
 * Contains the version history for a content.
 */
public class ContentHistory {
    private final List<ContentVersionInfo> versions;

    public ContentHistory(final List<ContentVersionInfo> versions) {
        this.versions = versions;
    }

    public List<ContentVersionInfo> getVersions() {
        return versions;
    }

    @Override
    public String toString() {
        return String.format("ContentHistory [versions: %s]", versions);
    }
}
