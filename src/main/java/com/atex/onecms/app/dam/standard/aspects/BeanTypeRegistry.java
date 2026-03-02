package com.atex.onecms.app.dam.standard.aspects;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps _type strings to their corresponding bean classes.
 * Used by OneContentPreStore to convert Map data to typed beans,
 * applying constructor defaults (objectType, inputTemplate, priority, etc.).
 */
public class BeanTypeRegistry {

    private static final Map<String, Class<? extends OneContentBean>> REGISTRY = new HashMap<>();

    static {
        // OneContentBean subtypes (production types)
        REGISTRY.put("atex.onecms.article", OneArticleBean.class);
        REGISTRY.put("atex.onecms.image", OneImageBean.class);

        // Dam standard types (OneArchiveBean subtypes)
        REGISTRY.put("atex.dam.standard.Audio", DamAudioAspectBean.class);
        REGISTRY.put("atex.dam.standard.Collection", DamCollectionAspectBean.class);
        REGISTRY.put("atex.dam.standard.Video", DamVideoAspectBean.class);
        REGISTRY.put("atex.dam.standard.Graphic", DamGraphicAspectBean.class);
        REGISTRY.put("atex.dam.standard.Page", DamPageAspectBean.class);

        // Dam standard types (OneContentBean subtypes)
        REGISTRY.put("atex.dam.standard.Document", DamDocumentAspectBean.class);
        REGISTRY.put("atex.dam.standard.Embed", DamEmbedAspectBean.class);
        REGISTRY.put("atex.dam.standard.Folder", DamFolderAspectBean.class);
        REGISTRY.put("atex.dam.standard.Tweet", DamTweetAspectBean.class);
        REGISTRY.put("atex.dam.standard.Instagram", DamInstagramAspectBean.class);
        REGISTRY.put("atex.dam.standard.ShapeDB", DamShapeDBAspectBean.class);
        REGISTRY.put("atex.dam.standard.NewslistItem", DamNewsListItemAspectBean.class);
        REGISTRY.put("atex.dam.standard.BulkImages", DamBulkImagesBean.class);
        REGISTRY.put("atex.dam.standard.AutoPage", DamAutoPageAspectBean.class);

        // Note: Legacy deprecated types (DamArticleAspectBean, DamImageAspectBean, DamWireArticleAspectBean,
        // DamWireImageAspectBean) extend DamContentBean, not OneContentBean, so are excluded.

        // Live blog types
        REGISTRY.put("atex.dam.standard.LiveBlog.article", LiveBlogArticleBean.class);
        REGISTRY.put("atex.dam.standard.LiveBlog.event", LiveBlogEventBean.class);
    }

    public static Class<? extends OneContentBean> resolve(String type) {
        return type != null ? REGISTRY.get(type) : null;
    }
}
