package com.atex.onecms.app.dam.lifecycle.article;

import com.atex.onecms.content.Content;
import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.ContentResult;
import com.atex.onecms.content.ContentVersionId;
import com.atex.onecms.content.ContentWrite;
import com.atex.onecms.content.ContentWriteBuilder;
import com.atex.onecms.content.Subject;
import com.atex.onecms.content.callback.CallbackException;
import com.atex.onecms.content.lifecycle.LifecycleContextPreStore;
import com.atex.onecms.content.lifecycle.LifecyclePreStore;
import com.atex.onecms.content.metadata.MetadataInfo;
import com.atex.onecms.content.ConfigurationDataBean;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.polopoly.metadata.Dimension;
import com.polopoly.metadata.Entity;
import com.polopoly.metadata.Metadata;

import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Enforces single-tag categories when configured.
 * If the configuration key "atex.configuration.desk.force-single-tag-category" is set,
 * dimensions listed in that config will be restricted to a single entity (the last one added).
 */
public class MetadataCoercingPreStoreHook implements LifecyclePreStore<Object, Object> {

    private static final Logger LOG = Logger.getLogger(MetadataCoercingPreStoreHook.class.getName());
    private static final String CONFIG_ID = "atex.configuration.desk.force-single-tag-category";
    private static final String METADATA_ASPECT = "atex.Metadata";

    @Override
    public ContentWrite<Object> preStore(ContentWrite<Object> input, Content<Object> existing,
                                          LifecycleContextPreStore<Object> context)
            throws CallbackException {
        try {
            ContentManager cm = context.getContentManager();
            Subject subject = context.getSubject();

            // Load config
            List<String> singleTagDimensions = loadSingleTagDimensions(cm, subject);
            if (singleTagDimensions.isEmpty()) {
                return input;
            }

            // Get metadata aspect
            Object metaAspect = input.getAspect(METADATA_ASPECT);
            if (!(metaAspect instanceof MetadataInfo metadataInfo)) {
                return input;
            }

            Metadata metadata = metadataInfo.getMetadata();
            if (metadata == null || metadata.getDimensions() == null) {
                return input;
            }

            boolean modified = false;
            for (String dimId : singleTagDimensions) {
                Dimension dim = metadata.getDimensionById(dimId);
                if (dim != null && dim.getEntities() != null && dim.getEntities().size() > 1) {
                    // Keep only the last entity
                    Entity last = dim.getEntities().get(dim.getEntities().size() - 1);
                    dim.getEntities().clear();
                    dim.getEntities().add(last);
                    modified = true;
                }
            }

            if (modified) {
                ContentWriteBuilder<Object> builder = ContentWriteBuilder.from(input);
                builder.aspect(METADATA_ASPECT, metadataInfo);
                return builder.build();
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error in MetadataCoercingPreStoreHook", e);
        }

        return input;
    }

    private List<String> loadSingleTagDimensions(ContentManager cm, Subject subject) {
        try {
            ContentVersionId vid = cm.resolve(CONFIG_ID, subject);
            if (vid == null) return Collections.emptyList();

            ContentResult<Object> cr = cm.get(vid, null, Object.class,
                Collections.emptyMap(), subject);
            if (!cr.getStatus().isSuccess()) return Collections.emptyList();

            Object data = cr.getContent().getContentData();
            String json = null;
            if (data instanceof ConfigurationDataBean cdb) {
                json = cdb.getJson();
            } else if (data instanceof java.util.Map<?,?> map) {
                Object j = map.get("json");
                if (j instanceof String s) json = s;
            }
            if (json == null) return Collections.emptyList();

            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            JsonArray dims = obj.getAsJsonArray("dimensions");
            if (dims == null) return Collections.emptyList();

            return dims.asList().stream()
                .filter(JsonElement::isJsonPrimitive)
                .map(JsonElement::getAsString)
                .toList();
        } catch (Exception e) {
            LOG.log(Level.FINE, "Could not load single-tag config", e);
            return Collections.emptyList();
        }
    }
}
