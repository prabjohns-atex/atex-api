package com.atex.onecms.app.dam.lifecycle.collection;

import com.atex.onecms.app.dam.standard.aspects.DamCollectionAspectBean;
import com.atex.onecms.app.dam.standard.aspects.DamContentAccessAspectBean;
import com.atex.onecms.app.dam.standard.aspects.OneContentBean;
import com.atex.onecms.app.dam.standard.aspects.OneImageBean;
import com.atex.onecms.content.Content;
import com.atex.onecms.content.ContentId;
import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.ContentResult;
import com.atex.onecms.content.ContentVersionId;
import com.atex.onecms.content.ContentWrite;
import com.atex.onecms.content.ContentWriteBuilder;
import com.atex.onecms.content.InsertionInfoAspectBean;
import com.atex.onecms.content.Subject;
import com.atex.onecms.content.callback.CallbackException;
import com.atex.onecms.content.lifecycle.LifecycleContextPreStore;
import com.atex.onecms.content.lifecycle.LifecyclePreStore;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
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
            List<ContentId> newIds = bean.getContents();
            List<ContentId> existingIds = null;
            if (existing != null) {
                Object existingData = existing.getContentData();
                if (existingData instanceof DamCollectionAspectBean existingBean) {
                    existingIds = existingBean.getContents();
                }
            }

            if (newIds != null && !newIds.isEmpty()) {
                List<ContentId> addedIds = new ArrayList<>(newIds);
                if (existingIds != null) {
                    addedIds.removeAll(existingIds);
                }

                // Copy collection fields to newly added children
                if (!addedIds.isEmpty()) {
                    LOG.fine(() -> "Collection: " + addedIds.size() + " items added");
                    propagateFieldsToChildren(bean, addedIds, cm, subject);
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

    /**
     * Propagate collection fields (headline, description, caption, byline, credit)
     * to newly added child content items. Only fills empty fields on the child.
     */
    private void propagateFieldsToChildren(DamCollectionAspectBean collection,
                                            List<ContentId> childIds,
                                            ContentManager cm, Subject subject) {
        for (ContentId childId : childIds) {
            try {
                ContentVersionId vid = cm.resolve(childId, subject);
                if (vid == null) continue;

                ContentResult<Object> cr = cm.get(vid, null, Object.class,
                    Collections.emptyMap(), subject);
                if (!cr.getStatus().isSuccess()) continue;

                Object childData = cr.getContent().getContentData();
                if (!(childData instanceof OneContentBean childBean)) continue;

                boolean childModified = false;

                // Copy headline if child's is empty
                childModified |= copyFieldIfEmpty(childData, "headline",
                    collection.getHeadline());
                // Copy description
                childModified |= copyFieldIfEmpty(childData, "description",
                    collection.getDescription());
                // Copy caption
                childModified |= copyFieldIfEmpty(childData, "caption",
                    collection.getCaption());

                // For images, copy additional fields from collection
                if (childData instanceof OneImageBean imageBean) {
                    if ((imageBean.getByline() == null || imageBean.getByline().isEmpty())) {
                        String byline = getStringField(collection, "byline");
                        if (byline != null && !byline.isEmpty()) {
                            imageBean.setByline(byline);
                            childModified = true;
                        }
                    }
                    if (imageBean.getReporter() == null || imageBean.getReporter().isEmpty()) {
                        String reporter = getStringField(collection, "reporter");
                        if (reporter != null && !reporter.isEmpty()) {
                            imageBean.setReporter(reporter);
                            childModified = true;
                        }
                    }
                    if (imageBean.getCredit() == null || imageBean.getCredit().isEmpty()) {
                        String credit = getStringField(collection, "credit");
                        if (credit != null && !credit.isEmpty()) {
                            imageBean.setCredit(credit);
                            childModified = true;
                        }
                    }
                }

                // Copy InsertionInfo from collection to child if not set
                Object childII = cr.getContent().getAspectData(InsertionInfoAspectBean.ASPECT_NAME);
                InsertionInfoAspectBean collectionII = null;
                // Check if collection has insertion info to propagate
                // (this is handled via the ContentWrite aspects, not from the bean)

                if (childModified) {
                    ContentWriteBuilder<Object> childBuilder = new ContentWriteBuilder<>();
                    childBuilder.mainAspectData(childData);
                    childBuilder.type(cr.getContent().getContentDataType());
                    cm.update(vid.getContentId(), childBuilder.buildUpdate(), subject);
                    LOG.fine(() -> "Updated child content from collection fields");
                }
            } catch (Exception e) {
                LOG.log(Level.FINE, "Could not propagate fields to child " + childId, e);
            }
        }
    }

    private boolean copyFieldIfEmpty(Object bean, String fieldName, String sourceValue) {
        if (sourceValue == null || sourceValue.isEmpty()) return false;
        try {
            Method getter = bean.getClass().getMethod(
                "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1));
            Object current = getter.invoke(bean);
            if (current != null && !current.toString().isEmpty()) return false;

            Method setter = bean.getClass().getMethod(
                "set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1), String.class);
            setter.invoke(bean, sourceValue);
            return true;
        } catch (NoSuchMethodException ignored) {
            return false;
        } catch (Exception e) {
            LOG.log(Level.FINE, "Could not copy field " + fieldName, e);
            return false;
        }
    }

    private String getStringField(Object bean, String fieldName) {
        try {
            Method getter = bean.getClass().getMethod(
                "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1));
            Object val = getter.invoke(bean);
            return val instanceof String s ? s : null;
        } catch (Exception e) {
            return null;
        }
    }
}
