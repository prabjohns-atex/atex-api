package com.atex.desk.api.plugin;

import com.atex.desk.api.onecms.LocalContentManager;
import com.atex.onecms.app.dam.lifecycle.audio.DamAudioPreStoreHook;
import com.atex.onecms.app.dam.lifecycle.charcount.OneCharCountPreStoreHook;
import com.atex.onecms.app.dam.lifecycle.collection.CollectionPreStore;
import com.atex.onecms.app.dam.lifecycle.engagement.AddEngagementPreStoreHook;
import com.atex.onecms.app.dam.lifecycle.handleItemState.HandleItemStatePreStore;
import com.atex.onecms.app.dam.lifecycle.onecontent.OneContentPreStore;
import com.atex.onecms.app.dam.lifecycle.onecontent.OneImagePreStore;
import com.atex.onecms.app.dam.lifecycle.partition.SecParentPreStoreHook;
import com.atex.onecms.app.dam.lifecycle.status.SetStatusPreStoreHook;
import com.atex.onecms.app.dam.lifecycle.wordcount.OneWordCountPreStoreHook;
import com.atex.onecms.content.lifecycle.LifecyclePreStore;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.logging.Logger;

/**
 * Registers all built-in pre-store hooks with LocalContentManager at startup.
 * Runs before PluginLoader so built-in hooks are first in the chain.
 *
 * <p>Hook chains match the original gong callbacks.xml configuration:
 * <ul>
 *   <li>Image: image → partition → workflow → secparent → engagement</li>
 *   <li>WireImage: image → partition → workflow → secparent</li>
 *   <li>Article: partition → workflow → wordcount → secparent → engagement</li>
 *   <li>WireArticle: partition → workflow → wordcount → secparent</li>
 * </ul>
 *
 * <p>Additionally, wildcard hooks (OneContentPreStore, HandleItemStatePreStore)
 * apply to all types for common initialization.
 */
@Component
public class BuiltInHookRegistrar {

    private static final Logger LOG = Logger.getLogger(BuiltInHookRegistrar.class.getName());

    /**
     * Wildcard key for hooks that apply to all content types.
     */
    public static final String ALL_TYPES = "*";

    // Original content type names from callbacks.xml
    private static final String IMAGE = "atex.dam.standard.Image";
    private static final String WIRE_IMAGE = "atex.dam.standard.WireImage";
    private static final String ARTICLE = "atex.dam.standard.Article";
    private static final String WIRE_ARTICLE = "atex.dam.standard.WireArticle";

    // Java class-based type names (used by desk-api)
    private static final String DAM_ARTICLE = "com.atex.onecms.app.dam.standard.aspects.DamArticleAspectBean";
    private static final String ONE_ARTICLE = "com.atex.onecms.app.dam.standard.aspects.OneArticleBean";
    private static final String DAM_IMAGE = "com.atex.onecms.app.dam.standard.aspects.DamImageAspectBean";
    private static final String ONE_IMAGE = "com.atex.onecms.app.dam.standard.aspects.OneImageBean";
    private static final String DAM_AUDIO = "com.atex.onecms.app.dam.standard.aspects.DamAudioAspectBean";
    private static final String DAM_COLLECTION = "com.atex.onecms.app.dam.standard.aspects.DamCollectionAspectBean";

    private final LocalContentManager contentManager;
    private final PartitionProperties partitionProperties;

    @Value("${desk.image-metadata-service.url:}")
    private String imageMetadataServiceUrl;

    @Value("${desk.image-metadata-service.enabled:false}")
    private boolean imageMetadataServiceEnabled;

    public BuiltInHookRegistrar(LocalContentManager contentManager,
                                 PartitionProperties partitionProperties) {
        this.contentManager = contentManager;
        this.partitionProperties = partitionProperties;
    }

    @PostConstruct
    public void registerBuiltInHooks() {
        // Shared hook instances
        SecParentPreStoreHook partitionHook = new SecParentPreStoreHook(partitionProperties);
        SetStatusPreStoreHook workflowHook = new SetStatusPreStoreHook();
        OneWordCountPreStoreHook wordCountHook = new OneWordCountPreStoreHook();
        OneCharCountPreStoreHook charCountHook = new OneCharCountPreStoreHook();
        AddEngagementPreStoreHook engagementHook = new AddEngagementPreStoreHook();
        OneImagePreStore imageHook = new OneImagePreStore(
            imageMetadataServiceUrl, imageMetadataServiceEnabled);

        // --- Wildcard hooks (all types) ---
        // These provide common initialization for all content
        contentManager.registerPreStoreHook(ALL_TYPES, new OneContentPreStore());
        contentManager.registerPreStoreHook(ALL_TYPES, new HandleItemStatePreStore());

        // --- Image types (matching callbacks.xml order) ---
        // damimage.prestore → dam.partition → dam.workflow → dam.secparent → dam.addengagement
        for (String type : new String[]{IMAGE, DAM_IMAGE, ONE_IMAGE}) {
            contentManager.registerPreStoreHook(type, imageHook);
            contentManager.registerPreStoreHook(type, partitionHook);
            contentManager.registerPreStoreHook(type, workflowHook);
            contentManager.registerPreStoreHook(type, engagementHook);
        }

        // WireImage: same but without engagement
        contentManager.registerPreStoreHook(WIRE_IMAGE, imageHook);
        contentManager.registerPreStoreHook(WIRE_IMAGE, partitionHook);
        contentManager.registerPreStoreHook(WIRE_IMAGE, workflowHook);

        // --- Article types (matching callbacks.xml order) ---
        // dam.partition → dam.workflow → dam.wordcount → dam.secparent → dam.addengagement
        for (String type : new String[]{ARTICLE, DAM_ARTICLE, ONE_ARTICLE}) {
            contentManager.registerPreStoreHook(type, partitionHook);
            contentManager.registerPreStoreHook(type, workflowHook);
            contentManager.registerPreStoreHook(type, wordCountHook);
            contentManager.registerPreStoreHook(type, charCountHook);
            contentManager.registerPreStoreHook(type, engagementHook);
        }

        // WireArticle: same but without engagement
        contentManager.registerPreStoreHook(WIRE_ARTICLE, partitionHook);
        contentManager.registerPreStoreHook(WIRE_ARTICLE, workflowHook);
        contentManager.registerPreStoreHook(WIRE_ARTICLE, wordCountHook);
        contentManager.registerPreStoreHook(WIRE_ARTICLE, charCountHook);

        // --- Audio types ---
        contentManager.registerPreStoreHook(DAM_AUDIO, new DamAudioPreStoreHook());
        contentManager.registerPreStoreHook(DAM_AUDIO, partitionHook);
        contentManager.registerPreStoreHook(DAM_AUDIO, workflowHook);
        contentManager.registerPreStoreHook(DAM_AUDIO, engagementHook);

        // --- Collection types ---
        contentManager.registerPreStoreHook(DAM_COLLECTION, new CollectionPreStore());
        contentManager.registerPreStoreHook(DAM_COLLECTION, partitionHook);
        contentManager.registerPreStoreHook(DAM_COLLECTION, workflowHook);
        contentManager.registerPreStoreHook(DAM_COLLECTION, engagementHook);

        LOG.info("Built-in pre-store hooks registered for "
            + "Image, WireImage, Article, WireArticle, Audio, Collection types "
            + "(+ wildcard hooks for all types)");
    }
}
