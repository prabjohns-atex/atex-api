package com.atex.onecms.app.dam.lifecycle.onecontent;

import com.atex.onecms.app.dam.standard.aspects.OneContentBean;
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

import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Pre-store hook for all OneContentBean-based content.
 * Handles:
 * - Setting creation date if null (new content)
 * - Initializing InsertionInfoAspectBean
 * - Deriving name from headline/title/caption when empty
 * - Adding "Copy of" prefix for duplicated content
 */
public class OneContentPreStore implements LifecyclePreStore<Object, Object> {

    private static final Logger LOG = Logger.getLogger(OneContentPreStore.class.getName());
    private static final String COPY_PREFIX = "Copy of ";

    @Override
    public ContentWrite<Object> preStore(ContentWrite<Object> input, Content<Object> existing,
                                          LifecycleContextPreStore<Object> context)
            throws CallbackException {
        Object data = input.getContentData();
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

            // Initialize InsertionInfoAspectBean for new content
            if (isCreate) {
                Object iiObj = input.getAspect(InsertionInfoAspectBean.ASPECT_NAME);
                if (iiObj == null) {
                    InsertionInfoAspectBean iiBean = new InsertionInfoAspectBean();
                    ContentWriteBuilder<Object> builder = ContentWriteBuilder.from(input);
                    builder.mainAspectData(bean);
                    builder.aspect(InsertionInfoAspectBean.ASPECT_NAME, iiBean);
                    return builder.build();
                }
            }

            return ContentWriteBuilder.from(input).mainAspectData(bean).build();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error in OneContentPreStore", e);
            return input;
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
            if (val instanceof String s && !s.isEmpty()) return truncate(s);
        } catch (NoSuchMethodException ignored) {
            // Not an article-like type
        } catch (Exception e) {
            LOG.log(Level.FINE, "Cannot get headline", e);
        }

        try {
            var method = bean.getClass().getMethod("getTitle");
            Object val = method.invoke(bean);
            if (val instanceof String s && !s.isEmpty()) return truncate(s);
        } catch (NoSuchMethodException ignored) {
            // Not an image-like type
        } catch (Exception e) {
            LOG.log(Level.FINE, "Cannot get title", e);
        }

        try {
            var method = bean.getClass().getMethod("getCaption");
            Object val = method.invoke(bean);
            if (val instanceof String s && !s.isEmpty()) return truncate(s);
        } catch (NoSuchMethodException ignored) {
            // Not a captioned type
        } catch (Exception e) {
            LOG.log(Level.FINE, "Cannot get caption", e);
        }

        return null;
    }

    private static String truncate(String s) {
        // Strip HTML and limit length for name
        String clean = s.replaceAll("<[^>]*>", "").trim();
        return clean.length() > 200 ? clean.substring(0, 200) : clean;
    }
}
