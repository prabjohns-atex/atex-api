package com.atex.onecms.content.lifecycle;

import com.atex.onecms.content.Content;
import com.atex.onecms.content.ContentWrite;
import com.atex.onecms.content.callback.CallbackException;

/**
 * Pre-store lifecycle hook. Runs before content is persisted and can modify
 * the ContentWrite before storage.
 *
 * @param <TYPE>   the type of the main aspect data
 * @param <CONFIG> the configuration type for this hook
 */
public interface LifecyclePreStore<TYPE, CONFIG> {

    /**
     * Called before a content create operation.
     *
     * @param input   the content write to be stored
     * @param context lifecycle context
     * @return the potentially modified content write
     * @throws CallbackException to abort the store operation
     */
    default ContentWrite<TYPE> preStore(ContentWrite<TYPE> input,
                                         LifecycleContextPreStore<CONFIG> context)
            throws CallbackException {
        return preStore(input, null, context);
    }

    /**
     * Called before a content update operation.
     *
     * @param input   the content write to be stored
     * @param content the existing content (null for creates)
     * @param context lifecycle context
     * @return the potentially modified content write
     * @throws CallbackException to abort the store operation
     */
    ContentWrite<TYPE> preStore(ContentWrite<TYPE> input,
                                 Content<TYPE> content,
                                 LifecycleContextPreStore<CONFIG> context)
            throws CallbackException;
}
