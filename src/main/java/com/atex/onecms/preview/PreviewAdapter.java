package com.atex.onecms.preview;

import com.google.gson.JsonObject;
import com.atex.onecms.content.ContentVersionId;

/**
 * Adapter interface for generating preview URLs.
 * Implementations handle specific preview backends (ACE, web dispatcher, etc.).
 *
 * @param <CONFIG> the configuration type for this adapter
 */
public interface PreviewAdapter<CONFIG> {

    /**
     * Generate a preview for the given content.
     *
     * @param contentVersionId the content version to preview
     * @param workspace        the workspace name (may be null for live content)
     * @param body             additional request body parameters
     * @param context          preview context with config, content manager, subject
     * @return JSON response with at least a "previewUrl" field
     */
    JsonObject preview(ContentVersionId contentVersionId, String workspace,
                       JsonObject body, PreviewContext<CONFIG> context);
}
