package com.polopoly.cm;

/**
 * Versioned Polopoly content ID (major/minor/version).
 */
public class VersionedContentId extends ContentId {
    private final int version;

    public VersionedContentId(int major, int minor, int version) {
        super(major, minor);
        this.version = version;
    }

    public VersionedContentId(ContentId contentId, int version) {
        super(contentId.getMajor(), contentId.getMinor());
        this.version = version;
    }

    @Override
    public int getVersion() { return version; }

    @Override
    public ContentId getContentId() {
        return new ContentId(getMajor(), getMinor());
    }

    @Override
    public String getContentIdString() {
        return getMajor() + "." + getMinor() + "." + version;
    }
}
