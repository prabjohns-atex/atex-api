package com.atex.onecms.content.mapping;
import com.atex.onecms.content.ContentResult;
import com.atex.onecms.content.ContentVersionId;
/**
 * Utility interface for ContentComposer implementations.
 */
public interface ContentComposerUtil {
    <T> ContentResult<T> filterUnmappedAspects(ContentResult<T> in);
    ContentResult getOriginalContent();
    boolean isAspectMapped(String aspectName);
    <T> ContentResult<T> notFoundInVariant(ContentVersionId id);
}
