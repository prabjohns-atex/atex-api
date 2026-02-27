package com.atex.onecms.content;

import static java.lang.String.format;

import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility methods for content ID conversions.
 */
public final class IdUtil {
    private static final Logger LOGGER = Logger.getLogger(IdUtil.class.getName());

    private IdUtil() {}

    /**
     * Convert the ContentId to a string. Use {@link #fromString(String)} to convert back.
     */
    public static String toIdString(ContentId id) {
        return id.getDelegationId() + ":" + id.getKey();
    }

    /**
     * Convert the ContentVersionId to a string.
     */
    public static String toVersionedIdString(ContentVersionId id) {
        return toIdString(id.getContentId()) + ":" + id.getVersion();
    }

    /**
     * Get a ContentId from its string representation (delegationId:key).
     */
    public static ContentId fromString(String id) throws IllegalArgumentException {
        String[] parts = id.split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException(
                format("String does not represent a ContentId: '%s'", id));
        }
        return new ContentId(parts[0], parts[1]);
    }

    /**
     * Get a ContentVersionId from its string representation (delegationId:key:version).
     */
    public static ContentVersionId fromVersionedString(String id) throws IllegalArgumentException {
        String[] parts = id.split(":");
        if (parts.length != 3) {
            throw new IllegalArgumentException(
                format("String does not represent a ContentVersionId: '%s'", id));
        }
        return new ContentVersionId(parts[0], parts[1], parts[2]);
    }

    /**
     * Returns true if the string represents a ContentVersionId.
     */
    public static boolean isVersionedIdString(String id) {
        return id.split(":").length == 3;
    }

    /**
     * Parse an ID string to a ContentId. Handles both legacy (numeric) and
     * standard (delegationId:key) formats.
     */
    public static Optional<ContentId> parseId(final String id) {
        if (id == null || id.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(fromString(id));
        } catch (IllegalArgumentException e) {
            LOGGER.log(Level.FINE, e.getMessage(), e);
        }
        return Optional.empty();
    }

    /**
     * Parse a versioned ID string to a ContentVersionId.
     */
    public static Optional<ContentVersionId> parseVersionedId(final String id) {
        if (id == null || id.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(fromVersionedString(id));
        } catch (IllegalArgumentException e) {
            LOGGER.log(Level.FINE, e.getMessage(), e);
        }
        return Optional.empty();
    }
}
