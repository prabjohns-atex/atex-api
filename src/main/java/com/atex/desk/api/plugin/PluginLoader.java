package com.atex.desk.api.plugin;

import com.atex.desk.api.onecms.LocalContentManager;
import com.atex.onecms.content.lifecycle.LifecyclePreStore;
import com.atex.onecms.content.mapping.ContentComposer;
import jakarta.annotation.PostConstruct;
import org.pf4j.PluginManager;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Discovers PF4J extensions at startup and registers them with LocalContentManager.
 * Depends on BuiltInHookRegistrar and BuiltInComposerRegistrar to ensure built-in
 * hooks/composers are registered first.
 *
 * <h3>Hook replacement via inheritance</h3>
 * <p>If a plugin hook extends a built-in hook class (e.g.,
 * {@code CustomOneContentPreStore extends OneContentPreStore}), the built-in
 * is automatically removed from the chain before the plugin hook is registered.
 * The plugin can call {@code super.preStore()} to extend the original logic,
 * or override completely to redefine it.
 *
 * <h3>Hook replacement via @Replaces</h3>
 * <p>For cases where inheritance isn't practical (replacing multiple hooks, or
 * replacing a hook the plugin doesn't extend), use {@link Replaces @Replaces}.
 */
@Component
@DependsOn({"builtInHookRegistrar", "builtInComposerRegistrar"})
public class PluginLoader {

    private static final Logger LOG = Logger.getLogger(PluginLoader.class.getName());

    private final PluginManager pluginManager;
    private final LocalContentManager contentManager;

    public PluginLoader(PluginManager pluginManager, LocalContentManager contentManager) {
        this.pluginManager = pluginManager;
        this.contentManager = contentManager;
    }

    @SuppressWarnings("unchecked")
    @PostConstruct
    public void registerExtensions() {
        // Discover plugin pre-store hooks (LifecyclePreStore is both the internal
        // interface and the PF4J ExtensionPoint — plugins implement it directly,
        // or extend a built-in hook class)
        List<LifecyclePreStore> hooks = pluginManager.getExtensions(LifecyclePreStore.class);
        for (LifecyclePreStore<Object, Object> hook : hooks) {
            // Remove built-in hooks that this plugin replaces (by inheritance or @Replaces)
            handleReplacements(hook);

            for (String contentType : hook.contentTypes()) {
                contentManager.registerPreStoreHook(contentType, hook);
                LOG.info("Registered plugin pre-store hook " + hook.getClass().getName()
                         + " for content type " + contentType);
            }
        }
        LOG.info("Plugin loading complete: " + hooks.size() + " pre-store hook(s) registered");

        // Register content composers
        List<DeskContentComposer> composers = pluginManager.getExtensions(DeskContentComposer.class);
        for (DeskContentComposer composer : composers) {
            ContentComposer<Object, Object, Object> adapted = adaptToComposer(composer);
            for (String contentType : composer.contentTypes()) {
                contentManager.registerComposer(composer.variant(), contentType, adapted);
                LOG.info("Registered plugin composer " + composer.getClass().getName()
                         + " for variant '" + composer.variant() + "' type '" + contentType + "'");
            }
        }
        if (!composers.isEmpty()) {
            LOG.info("Plugin composer loading complete: " + composers.size() + " composer(s) registered");
        }
    }

    private ContentComposer<Object, Object, Object> adaptToComposer(DeskContentComposer composer) {
        return (source, variant, request, context) ->
            composer.compose(source, request != null ? request.getRequestParameters() : Map.of());
    }

    /**
     * Remove built-in hooks that a plugin hook replaces. Two detection methods:
     *
     * <ol>
     *   <li><b>Inheritance</b>: if the plugin class extends a built-in hook class
     *       (e.g., {@code CustomOneContentPreStore extends OneContentPreStore}),
     *       the parent is automatically replaced.</li>
     *   <li><b>{@link Replaces} annotation</b>: for cases where the plugin can't extend
     *       the built-in class (replacing multiple hooks, or unrelated class hierarchy).
     *       Example: {@code @Replaces({SecParentPreStoreHook.class, SetStatusPreStoreHook.class})}</li>
     * </ol>
     */
    private void handleReplacements(Object extension) {
        String pluginName = extension.getClass().getName();

        // 1. Check class hierarchy — does this plugin extend any registered built-in hook?
        Set<Class<?>> builtInClasses = contentManager.getRegisteredHookClasses();
        Class<?> current = extension.getClass().getSuperclass();
        while (current != null && current != Object.class) {
            if (builtInClasses.contains(current)) {
                int removed = contentManager.unregisterPreStoreHook(current);
                LOG.info("Plugin " + pluginName + " extends " + current.getSimpleName()
                         + " — replaced " + removed + " built-in registration(s)");
            }
            current = current.getSuperclass();
        }

        // 2. Check @Replaces annotation for explicit replacements
        Replaces replaces = extension.getClass().getAnnotation(Replaces.class);
        if (replaces != null) {
            for (Class<?> target : replaces.value()) {
                int removed = contentManager.unregisterPreStoreHook(target);
                if (removed > 0) {
                    LOG.info("Plugin " + pluginName + " @Replaces " + target.getSimpleName()
                             + " — removed " + removed + " built-in registration(s)");
                } else {
                    LOG.warning("Plugin " + pluginName + " @Replaces " + target.getSimpleName()
                                + " but no registrations of that class were found");
                }
            }
        }
    }
}
