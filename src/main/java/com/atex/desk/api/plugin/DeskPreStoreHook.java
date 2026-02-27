package com.atex.desk.api.plugin;

import com.atex.onecms.content.Content;
import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.ContentWrite;
import com.atex.onecms.content.Subject;
import com.atex.onecms.content.callback.CallbackException;
import org.pf4j.ExtensionPoint;

/**
 * PF4J extension point for pre-store hooks.
 * Plugin authors implement this interface to modify content before it is persisted.
 */
public interface DeskPreStoreHook extends ExtensionPoint {

    /**
     * Content type(s) this hook applies to
     * (e.g. "com.atex.onecms.app.dam.standard.aspects.DamArticleAspectBean").
     */
    String[] contentTypes();

    /**
     * Called before content is persisted. Return the (potentially modified) write.
     *
     * @param input          the content write about to be stored
     * @param existing       the existing content (null for creates)
     * @param contentManager the content manager for reading other content
     * @param subject        the authenticated subject
     * @return the potentially modified content write
     * @throws CallbackException to abort the store operation
     */
    ContentWrite<Object> preStore(ContentWrite<Object> input,
                                   Content<Object> existing,
                                   ContentManager contentManager,
                                   Subject subject) throws CallbackException;
}
