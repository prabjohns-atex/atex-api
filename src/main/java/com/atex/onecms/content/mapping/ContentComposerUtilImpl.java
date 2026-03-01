package com.atex.onecms.content.mapping;
import com.atex.onecms.content.ContentResult;
import com.atex.onecms.content.ContentResultBuilder;
import com.atex.onecms.content.ContentVersionId;
import com.atex.onecms.content.Status;
import java.util.Collections;
import java.util.Set;
/**
 * Simplified ContentComposerUtil implementation for desk-api.
 * Since desk-api does not have AspectMappers, all aspects are considered unmapped.
 */
public class ContentComposerUtilImpl implements ContentComposerUtil {
    private final ContentResult originalContent;
    public ContentComposerUtilImpl(ContentResult originalContent) {
        this.originalContent = originalContent;
    }
    @Override
    public <T> ContentResult<T> filterUnmappedAspects(ContentResult<T> in) {
        // No aspect mapper infrastructure — return as-is
        return in;
    }
    @Override
    public ContentResult getOriginalContent() {
        return originalContent;
    }
    @Override
    public boolean isAspectMapped(String aspectName) {
        return false;
    }
    @Override
    public <T> ContentResult<T> notFoundInVariant(ContentVersionId id) {
        return new ContentResultBuilder<T>()
            .id(id)
            .status(Status.NOT_FOUND)
            .build();
    }
}
