package com.atex.onecms.content.lifecycle;

import com.atex.onecms.content.Content;
import com.atex.onecms.content.ContentWrite;
import com.atex.onecms.content.callback.CallbackException;
import org.pf4j.ExtensionPoint;

/**
 * Pre-store lifecycle hook. Runs before content is persisted and can modify
 * the ContentWrite before storage.
 *
 * <p>This interface is both the internal hook interface used by built-in hooks
 * (e.g., OneContentPreStore, SetStatusPreStoreHook) and the PF4J extension point
 * for plugins. Plugins can:
 * <ul>
 *   <li>Implement this interface directly to add a new hook</li>
 *   <li>Extend a built-in hook class to replace it (inheritance-based replacement)</li>
 * </ul>
 *
 * <p>When a plugin extends a built-in hook, the built-in is automatically removed
 * from the chain. The plugin can call {@code super.preStore()} to include the
 * original logic, or override completely to redefine it.
 *
 * @param <TYPE>   the type of the main aspect data
 * @param <CONFIG> the configuration type for this hook
 */
public interface LifecyclePreStore<TYPE, CONFIG> extends ExtensionPoint {

    /**
     * Content type(s) this hook applies to.
     * Use {@code "*"} for hooks that apply to all content types.
     *
     * <p>Built-in hooks do not use this method — they are registered explicitly
     * by {@link com.atex.desk.api.plugin.BuiltInHookRegistrar} with specific type names.
     * Plugin hooks must override this to declare which content types they handle.
     *
     * @return array of content type names (e.g., "atex.onecms.article", "*")
     */
    default String[] contentTypes() {
        return new String[]{"*"};
    }

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
