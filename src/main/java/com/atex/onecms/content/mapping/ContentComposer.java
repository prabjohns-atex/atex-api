package com.atex.onecms.content.mapping;

import com.atex.onecms.content.ContentResult;

import java.util.Map;

/**
 * Interface for variant-based content transformation.
 * ContentComposers transform content from one representation to another
 * based on the requested variant.
 */
public interface ContentComposer<IN, OUT, CONFIG> {

    /**
     * Compose the source content result into a new representation.
     *
     * @param source  the source content result
     * @param variant the name of the variant being requested
     * @param params  additional parameters
     * @param config  composer configuration
     * @return the composed content result
     */
    ContentResult<OUT> compose(ContentResult<IN> source, String variant,
                                Map<String, Object> params, CONFIG config);
}
