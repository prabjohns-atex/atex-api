package com.atex.desk.api.plugin;

import com.atex.onecms.content.lifecycle.LifecyclePreStore;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a plugin extension as a replacement for a built-in (product) class.
 * When a plugin hook or composer is annotated with {@code @Replaces},
 * the built-in class is removed from the chain before the plugin is registered.
 *
 * <p>In most cases, {@code @Replaces} is not needed — simply extending the built-in
 * hook class triggers automatic replacement via inheritance detection. Use this
 * annotation only when inheritance isn't practical (e.g., replacing multiple hooks,
 * or the plugin doesn't extend the built-in class).
 *
 * <p>Example — replacing multiple hooks with a single plugin:
 * <pre>
 * {@literal @}Extension
 * {@literal @}Replaces({SecParentPreStoreHook.class, SetStatusPreStoreHook.class})
 * public class CombinedSecStatusHook implements LifecyclePreStore&lt;Object, Object&gt; {
 *     public String[] contentTypes() { return new String[]{"atex.onecms.article"}; }
 *     public ContentWrite&lt;Object&gt; preStore(ContentWrite&lt;Object&gt; input,
 *             Content&lt;Object&gt; existing, LifecycleContextPreStore&lt;Object&gt; ctx)
 *             throws CallbackException {
 *         // Combined logic replacing both hooks
 *     }
 * }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Replaces {

    /**
     * The built-in class(es) that this extension replaces.
     * These must be classes registered by {@link BuiltInHookRegistrar}
     * or {@link BuiltInComposerRegistrar}.
     */
    Class<?>[] value();
}
