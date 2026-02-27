package com.polopoly.cm;

/**
 * Factory for creating ContentId instances from string representations.
 */
public final class ContentIdFactory {

    private ContentIdFactory() {}

    /**
     * Parse a content ID string (major.minor or major.minor.version).
     */
    public static ContentId createContentId(String id) throws IllegalArgumentException {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("Content ID string cannot be null or empty");
        }
        String[] parts = id.split("\\.");
        if (parts.length == 2) {
            return new ContentId(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        } else if (parts.length == 3) {
            return new VersionedContentId(
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]));
        } else {
            throw new IllegalArgumentException("Invalid content ID format: " + id);
        }
    }
}
