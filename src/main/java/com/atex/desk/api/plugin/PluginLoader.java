package com.atex.desk.api.plugin;

import com.atex.desk.api.onecms.LocalContentManager;
import com.atex.onecms.content.lifecycle.LifecycleContextPreStore;
import com.atex.onecms.content.lifecycle.LifecyclePreStore;
import jakarta.annotation.PostConstruct;
import org.pf4j.PluginManager;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.logging.Logger;

/**
 * Discovers PF4J extensions at startup and registers them with LocalContentManager.
 * Depends on BuiltInHookRegistrar to ensure built-in hooks are registered first.
 */
@Component
@DependsOn("builtInHookRegistrar")
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
        List<DeskPreStoreHook> hooks = pluginManager.getExtensions(DeskPreStoreHook.class);
        for (DeskPreStoreHook hook : hooks) {
            for (String contentType : hook.contentTypes()) {
                contentManager.registerPreStoreHook(contentType, adaptToLifecycle(hook));
                LOG.info("Registered pre-store hook " + hook.getClass().getName()
                         + " for content type " + contentType);
            }
        }
        LOG.info("Plugin loading complete: " + hooks.size() + " pre-store hook(s) registered");

        List<DeskContentComposer> composers = pluginManager.getExtensions(DeskContentComposer.class);
        if (!composers.isEmpty()) {
            LOG.info("Discovered " + composers.size() + " content composer(s) (not yet wired)");
        }
    }

    private LifecyclePreStore<Object, Object> adaptToLifecycle(DeskPreStoreHook hook) {
        return (input, existing, context) ->
            hook.preStore(input, existing, context.getContentManager(), context.getSubject());
    }
}
