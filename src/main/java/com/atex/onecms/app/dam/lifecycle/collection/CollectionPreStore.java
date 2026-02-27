package com.atex.onecms.app.dam.lifecycle.collection;

import com.atex.onecms.app.dam.standard.aspects.DamCollectionAspectBean;
import com.atex.onecms.app.dam.standard.aspects.DamContentAccessAspectBean;
import com.atex.onecms.content.Content;
import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.ContentWrite;
import com.atex.onecms.content.ContentWriteBuilder;
import com.atex.onecms.content.Subject;
import com.atex.onecms.content.callback.CallbackException;
import com.atex.onecms.content.lifecycle.LifecycleContextPreStore;
import com.atex.onecms.content.lifecycle.LifecyclePreStore;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Pre-store hook for collection content.
 * When items are added to a collection:
 * - Fills empty description/headline from collection
 * - Adds DamContentAccessAspectBean with creator/assignee info
 */
public class CollectionPreStore implements LifecyclePreStore<Object, Object> {

    private static final Logger LOG = Logger.getLogger(CollectionPreStore.class.getName());

    @Override
    public ContentWrite<Object> preStore(ContentWrite<Object> input, Content<Object> existing,
                                          LifecycleContextPreStore<Object> context)
            throws CallbackException {
        Object data = input.getContentData();
        if (!(data instanceof DamCollectionAspectBean bean)) {
            return input;
        }

        try {
            ContentManager cm = context.getContentManager();
            Subject subject = context.getSubject();
            ContentWriteBuilder<Object> builder = ContentWriteBuilder.from(input);
            boolean modified = false;

            // Set name from headline if empty
            if (bean.getName() == null || bean.getName().isEmpty()) {
                if (bean.getHeadline() != null && !bean.getHeadline().isEmpty()) {
                    bean.setName(bean.getHeadline());
                    modified = true;
                }
            }

            // Initialize DamContentAccessAspectBean for new collections
            if (existing == null) {
                Object accessObj = input.getAspect(DamContentAccessAspectBean.ASPECT_NAME);
                if (accessObj == null && subject != null) {
                    DamContentAccessAspectBean accessBean = new DamContentAccessAspectBean();
                    accessBean.setCreator(subject.getPrincipalId());
                    accessBean.setAssignees(new ArrayList<>());
                    builder.aspect(DamContentAccessAspectBean.ASPECT_NAME, accessBean);
                    modified = true;
                }
            }

            // Detect newly added items
            List<String> newIds = bean.getContentIds();
            List<String> existingIds = null;
            if (existing != null) {
                Object existingData = existing.getContentData();
                if (existingData instanceof DamCollectionAspectBean existingBean) {
                    existingIds = existingBean.getContentIds();
                }
            }

            if (newIds != null && !newIds.isEmpty()) {
                List<String> addedIds = new ArrayList<>(newIds);
                if (existingIds != null) {
                    addedIds.removeAll(existingIds);
                }

                // Log newly added items
                if (!addedIds.isEmpty()) {
                    LOG.fine(() -> "Collection: " + addedIds.size() + " items added");
                }
            }

            if (modified) {
                builder.mainAspectData(bean);
                return builder.build();
            }

            return input;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error in CollectionPreStore", e);
            return input;
        }
    }
}
