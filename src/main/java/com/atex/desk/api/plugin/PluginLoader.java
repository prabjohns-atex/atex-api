package com.atex.desk.api.plugin;

import com.atex.desk.api.onecms.LocalContentManager;
import com.atex.onecms.content.ContentResult;
import com.atex.onecms.content.lifecycle.LifecycleContextPreStore;
import com.atex.onecms.content.lifecycle.LifecyclePreStore;
import com.atex.onecms.content.mapping.ContentComposer;
import com.atex.onecms.content.mapping.Context;
import com.atex.onecms.content.mapping.Request;
import jakarta.annotation.PostConstruct;
import org.pf4j.PluginManager;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Discovers PF4J extensions at startup and registers them with LocalContentManager.
 * Depends on BuiltInHookRegistrar and BuiltInComposerRegistrar to ensure built-in
 * hooks/composers are registered first.
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

    @PostConstruct
    public void registerExtensions() {
        // Register pre-store hooks
        List<DeskPreStoreHook> hooks = pluginManager.getExtensions(DeskPreStoreHook.class);
        for (DeskPreStoreHook hook : hooks) {
            for (String contentType : hook.contentTypes()) {
                contentManager.registerPreStoreHook(contentType, adaptToLifecycle(hook));
                LOG.info("Registered pre-store hook " + hook.getClass().getName()
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

    private LifecyclePreStore<Object, Object> adaptToLifecycle(DeskPreStoreHook hook) {
        return (input, existing, context) ->
            hook.preStore(input, existing, context.getContentManager(), context.getSubject());
    }

    private ContentComposer<Object, Object, Object> adaptToComposer(DeskContentComposer composer) {
        return (source, variant, request, context) ->
            composer.compose(source, request != null ? request.getRequestParameters() : Map.of());
    }
}
