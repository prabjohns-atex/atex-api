package com.atex.onecms.content;

import java.util.Objects;

/**
 * Utility interface for analyzing content data types.
 */
public interface ContentWriteHelper {

    default <T> boolean isPolicyContent(final ContentWrite<T> contentWrite) {
        return isPolicyContent(Objects.requireNonNull(contentWrite).getContentDataType());
    }

    boolean isPolicyContent(final String contentDataType);
}
