package com.atex.onecms.content;

import static java.lang.String.format;

/**
 * Identifies a specific version of a content.
 */
public final class ContentVersionId {
    private final String version;
    private final ContentId contentId;

    public ContentVersionId(String delegationId, String key, String version) {
        this(new ContentId(delegationId, key), version);
    }

    public ContentVersionId(ContentId id, String version) {
        if (id == null) {
            throw new IllegalArgumentException("id cannot be null");
        }
        this.contentId = id;
        this.version = version;
    }

    public String getVersion() {
        return version;
    }

    public ContentId getContentId() {
        return contentId;
    }

    public String getKey() {
        return contentId.getKey();
    }

    public String getDelegationId() {
        return contentId.getDelegationId();
    }

    @Override
    public String toString() {
        return format("ContentVersionId [delegationId=%s, id=%s, version=%s]",
                      contentId.getDelegationId(), contentId.getKey(), version);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContentVersionId that = (ContentVersionId) o;
        if (!contentId.equals(that.contentId)) return false;
        return version.equals(that.version);
    }

    @Override
    public int hashCode() {
        int result = contentId.hashCode();
        return 31 * result + version.hashCode();
    }
}
