package com.polopoly.cm;

import java.io.Serializable;

/**
 * Polopoly content ID (major/minor). Distinct from OneCMS ContentId.
 */
public class ContentId implements Serializable, Comparable<ContentId> {
    public static final int UNDEFINED_VERSION = -1;
    public static final int MIN_MAJOR = 1;
    public static final int MIN_MINOR = 100;

    private final int major;
    private final int minor;

    public ContentId(int major, int minor) {
        this.major = major;
        this.minor = minor;
    }

    public int getMajor() { return major; }
    public int getMinor() { return minor; }
    public int getVersion() { return UNDEFINED_VERSION; }

    public ContentId getContentId() { return this; }

    public boolean isSymbolicId() { return false; }

    public String getContentIdString() {
        return major + "." + minor;
    }

    @Override
    public int compareTo(ContentId other) {
        int cmp = Integer.compare(this.major, other.major);
        return cmp != 0 ? cmp : Integer.compare(this.minor, other.minor);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContentId other = (ContentId) o;
        return major == other.major && minor == other.minor;
    }

    @Override
    public int hashCode() {
        return 31 * major + minor;
    }

    @Override
    public String toString() {
        return getContentIdString();
    }
}
