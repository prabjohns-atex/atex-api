package com.atex.onecms.app.dam.lifecycle.onecontent;

import com.atex.onecms.app.dam.standard.aspects.BeanTypeRegistry;
import com.atex.onecms.app.dam.standard.aspects.OneContentBean;
import com.atex.onecms.content.metadata.MetadataInfo;
import com.atex.plugins.structured.text.StructuredText;
import com.atex.onecms.content.CachingFetcher;
import com.atex.onecms.content.Content;
import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.ContentWrite;
import com.atex.onecms.content.ContentWriteBuilder;
import com.atex.onecms.content.InsertionInfoAspectBean;
import com.atex.onecms.content.Subject;
import com.atex.onecms.content.callback.CallbackException;
import com.atex.onecms.content.lifecycle.LifecycleContextPreStore;
import com.atex.onecms.content.lifecycle.LifecyclePreStore;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.polopoly.metadata.Metadata;

import java.util.Date;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Pre-store hook for all OneContentBean-based content.
 * Handles:
 * - Setting creation date if null (new content)
 * - Initializing InsertionInfoAspectBean
 * - Initializing MetadataInfo (atex.Metadata)
 * - Deriving name from headline/title/caption when empty
 * - Adding "Copy of" prefix for duplicated content
 */
public class OneContentPreStore implements LifecyclePreStore<Object, Object> {

    private static final Logger LOG = Logger.getLogger(OneContentPreStore.class.getName());
    private static final String COPY_PREFIX = "Copy of ";
    private static final String METADATA_ASPECT_NAME = "atex.Metadata";
    private static final Gson GSON = new Gson();

    @Override
    public ContentWrite<Object> preStore(ContentWrite<Object> input, Content<Object> existing,
                                          LifecycleContextPreStore<Object> context)
            throws CallbackException {
        Object data = input.getContentData();

        // Convert Map to typed bean if possible, applying constructor defaults
        if (data instanceof Map<?,?> map) {
            Object converted = convertMapToBean(map);
            if (converted != null) {
                data = converted;
                input = ContentWriteBuilder.from(input).mainAspectData(data).build();
            }
        }

        if (!(data instanceof OneContentBean bean)) {
            return input;
        }

        try {
            boolean isCreate = (existing == null);
            ContentManager cm = context.getContentManager();
            Subject subject = context.getSubject();
            CachingFetcher fetcher = CachingFetcher.create(cm, subject);

            // Set creation date for new content
            if (isCreate && bean.getCreationdate() == null) {
                bean.setCreationdate(new Date());
            }

            // Set author from subject for new content
            if (isCreate && (bean.getAuthor() == null || bean.getAuthor().isEmpty()) && subject != null) {
                bean.setAuthor(subject.getPrincipalId());
            }

            // Derive name if empty
            if (bean.getName() == null || bean.getName().isEmpty()) {
                String derivedName = deriveName(bean);
                if (derivedName != null && !derivedName.isEmpty()) {
                    bean.setName(derivedName);
                }
            }

            // For new content, always build to ensure InsertionInfo and Metadata are initialized
            if (isCreate) {
                ContentWriteBuilder<Object> builder = ContentWriteBuilder.from(input);
                builder.mainAspectData(bean);

                // Initialize InsertionInfoAspectBean if missing
                if (input.getAspect(InsertionInfoAspectBean.ASPECT_NAME) == null) {
                    builder.aspect(InsertionInfoAspectBean.ASPECT_NAME, new InsertionInfoAspectBean());
                }

                // Initialize MetadataInfo (atex.Metadata) if missing
                if (input.getAspect(METADATA_ASPECT_NAME) == null) {
                    MetadataInfo metadataInfo = new MetadataInfo();
                    metadataInfo.setMetadata(new Metadata());
                    builder.aspect(METADATA_ASPECT_NAME, metadataInfo);
                }

                return builder.build();
            }

            return ContentWriteBuilder.from(input).mainAspectData(bean).build();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error in OneContentPreStore", e);
            return input;
        }
    }

    /**
     * Converts a Map to a typed bean using the _type field, applying constructor/field defaults.
     * User-provided fields overlay on top of bean defaults.
     */
    @SuppressWarnings("unchecked")
    private Object convertMapToBean(Map<?, ?> map) {
        Object typeVal = map.get("_type");
        if (!(typeVal instanceof String type)) {
            return null;
        }
        Class<? extends OneContentBean> beanClass = BeanTypeRegistry.resolve(type);
        if (beanClass == null) {
            return null;
        }
        try {
            // 1. Create bean with defaults via constructor
            JsonObject defaultsJson = GSON.toJsonTree(GSON.fromJson("{}", beanClass)).getAsJsonObject();
            // 2. Overlay user-provided fields on top of defaults
            JsonObject userJson = GSON.toJsonTree(map).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : userJson.entrySet()) {
                defaultsJson.add(entry.getKey(), entry.getValue());
            }
            // 3. Deserialize merged result to typed bean
            return GSON.fromJson(defaultsJson, beanClass);
        } catch (Exception e) {
            LOG.log(Level.FINE, "Could not convert map to bean for type: " + type, e);
            return null;
        }
    }

    private String deriveName(OneContentBean bean) {
        // Try to derive from bean-specific fields via reflection-free approach
        // Use getName() first if already set
        String name = bean.getName();
        if (name != null && !name.isEmpty()) return name;

        // Try common fields that subclasses may have
        try {
            var method = bean.getClass().getMethod("getHeadline");
            Object val = method.invoke(bean);
            String s = asString(val);
            if (s != null && !s.isEmpty()) return truncate(s);
        } catch (NoSuchMethodException ignored) {
            // Not an article-like type
        } catch (Exception e) {
            LOG.log(Level.FINE, "Cannot get headline", e);
        }

        try {
            var method = bean.getClass().getMethod("getTitle");
            Object val = method.invoke(bean);
            String s = asString(val);
            if (s != null && !s.isEmpty()) return truncate(s);
        } catch (NoSuchMethodException ignored) {
            // Not an image-like type
        } catch (Exception e) {
            LOG.log(Level.FINE, "Cannot get title", e);
        }

        try {
            var method = bean.getClass().getMethod("getCaption");
            Object val = method.invoke(bean);
            String s = asString(val);
            if (s != null && !s.isEmpty()) return truncate(s);
        } catch (NoSuchMethodException ignored) {
            // Not a captioned type
        } catch (Exception e) {
            LOG.log(Level.FINE, "Cannot get caption", e);
        }

        return null;
    }

    private static String asString(Object val) {
        if (val instanceof String s) return s;
        if (val instanceof StructuredText st) return st.getText();
        return val != null ? val.toString() : null;
    }

    private static String truncate(String s) {
        // Strip HTML and limit length for name
        String clean = s.replaceAll("<[^>]*>", "").trim();
        return clean.length() > 200 ? clean.substring(0, 200) : clean;
    }
}
