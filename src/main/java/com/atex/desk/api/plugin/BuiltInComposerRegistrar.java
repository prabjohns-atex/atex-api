package com.atex.desk.api.plugin;
import com.atex.desk.api.indexing.DamIndexComposer;
import com.atex.desk.api.onecms.LocalContentManager;
import com.atex.onecms.content.ContentResult;
import com.atex.onecms.content.ContentResultBuilder;
import com.atex.onecms.content.mapping.ContentComposer;
import com.atex.onecms.content.mapping.Context;
import com.atex.onecms.content.mapping.Request;
import com.google.gson.JsonObject;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import java.util.logging.Logger;
/**
 * Registers built-in content composers at startup.
 * Follows the same pattern as BuiltInHookRegistrar.
 */
@Component("builtInComposerRegistrar")
public class BuiltInComposerRegistrar {
    private static final Logger LOG = Logger.getLogger(BuiltInComposerRegistrar.class.getName());
    private final LocalContentManager contentManager;
    private final DamIndexComposer damIndexComposer;
    public BuiltInComposerRegistrar(LocalContentManager contentManager,
                                     DamIndexComposer damIndexComposer) {
        this.contentManager = contentManager;
        this.damIndexComposer = damIndexComposer;
    }
    @PostConstruct
    public void registerComposers() {
        // 1. atex.onecms.indexing — wraps existing DamIndexComposer
        ContentComposer<Object, Object, Object> indexingAdapter = (source, variant, request, context) -> {
            if (source.getContentId() != null) {
                JsonObject doc = damIndexComposer.compose(source, source.getContentId());
                return new ContentResultBuilder<Object>()
                    .id(source.getContentId())
                    .status(source.getStatus())
                    .mainAspectData(doc)
                    .variant(variant)
                    .build();
            }
            return source;
        };
        contentManager.registerComposer("atex.onecms.indexing", "*", indexingAdapter);
        LOG.info("Built-in composer registration complete: 1 composer(s) registered");
    }
}
