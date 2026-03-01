package com.atex.onecms.content.mapping;

import com.atex.onecms.content.ContentResult;
import com.atex.onecms.content.callback.CallbackException;

/**
 * A ContentComposer is used to produce an alternative representation of a content.
 * It can convert the content's data and its type.
 * ContentComposers are called as the last step in a data processing pipeline.
 *
 * @param <IN>     the type to convert from.
 * @param <OUT>    the type to convert to. May be the same as IN if no type conversion is needed.
 * @param <CONFIG> the type of the composer's configuration bean.
 */
public interface ContentComposer<IN, OUT, CONFIG> {

    /**
     * Compose a content based on source.
     *
     * @param source  a content on which aspect mappers have been applied.
     * @param variant name of the requested variant
     * @param request parameters specific to this request only
     * @param context the application context
     * @return a new representation of the content. It cannot be null!
     * @throws CallbackException if it fails to compose the content.
     */
    ContentResult<OUT> compose(ContentResult<IN> source, String variant,
                                Request request, Context<CONFIG> context) throws CallbackException;
}
