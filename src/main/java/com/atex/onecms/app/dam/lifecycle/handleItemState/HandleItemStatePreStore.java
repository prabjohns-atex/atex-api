package com.atex.onecms.app.dam.lifecycle.handleItemState;

import com.atex.onecms.app.dam.standard.aspects.OneContentBean;
import com.atex.onecms.app.dam.standard.aspects.PrestigeItemStateAspectBean;
import com.atex.onecms.app.dam.standard.aspects.PrestigeItemStateAspectBean.ItemState;
import com.atex.onecms.content.CachingFetcher;
import com.atex.onecms.content.Content;
import com.atex.onecms.content.ContentId;
import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.ContentVersionId;
import com.atex.onecms.content.ContentWrite;
import com.atex.onecms.content.ContentWriteBuilder;
import com.atex.onecms.content.IdUtil;
import com.atex.onecms.content.InsertionInfoAspectBean;
import com.atex.onecms.content.Subject;
import com.atex.onecms.content.callback.CallbackException;
import com.atex.onecms.content.lifecycle.LifecycleContextPreStore;
import com.atex.onecms.content.lifecycle.LifecyclePreStore;
import com.polopoly.metadata.Dimension;
import com.polopoly.metadata.Entity;
import com.polopoly.metadata.Metadata;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Pre-store hook for handling spike/unspike item state transitions.
 * SPIKED: moves content to trash security parent, updates partition to "trash".
 * PRODUCTION: restores original security parent from previousSecParent field.
 */
public class HandleItemStatePreStore implements LifecyclePreStore<Object, Object> {

    private static final Logger LOG = Logger.getLogger(HandleItemStatePreStore.class.getName());
    private static final String PARTITION_DIMENSION_ID = "dimension.partition";
    private static final String METADATA_ASPECT_NAME = "p.Metadata";
    private static final String TRASH_PARTITION = "trash";
    private static final String TRASH_SEC_PARENT_EXT_ID = "site.trash.d";

    @Override
    public ContentWrite<Object> preStore(ContentWrite<Object> input, Content<Object> existing,
                                          LifecycleContextPreStore<Object> context)
            throws CallbackException {
        Object data = input.getContentData();
        if (!(data instanceof OneContentBean)) {
            return input;
        }

        Object stateObj = input.getAspect(PrestigeItemStateAspectBean.ASPECT_NAME);
        if (!(stateObj instanceof PrestigeItemStateAspectBean stateBean)) {
            return input;
        }

        ItemState newState = stateBean.getItemState();
        if (newState == null) {
            return input;
        }

        // Determine previous state
        ItemState previousState = null;
        if (existing != null) {
            Object prevStateObj = existing.getAspectData(PrestigeItemStateAspectBean.ASPECT_NAME);
            if (prevStateObj instanceof PrestigeItemStateAspectBean prevBean) {
                previousState = prevBean.getItemState();
            }
        }

        // Only act on state transitions
        if (newState == previousState) {
            return input;
        }

        try {
            ContentManager cm = context.getContentManager();
            Subject subject = context.getSubject();
            ContentWriteBuilder<Object> builder = ContentWriteBuilder.from(input);

            if (newState == ItemState.SPIKED) {
                // Save current security parent before moving to trash
                Object iiObj = input.getAspect(InsertionInfoAspectBean.ASPECT_NAME);
                if (iiObj instanceof InsertionInfoAspectBean ii && ii.getSecurityParentId() != null) {
                    stateBean.setPreviousSecParent(IdUtil.toIdString(ii.getSecurityParentId()));
                }

                // Move to trash security parent
                CachingFetcher fetcher = CachingFetcher.create(cm, subject);
                ContentVersionId trashVid = fetcher.resolve(TRASH_SEC_PARENT_EXT_ID, subject);
                if (trashVid != null) {
                    InsertionInfoAspectBean ii;
                    if (iiObj instanceof InsertionInfoAspectBean existing2) {
                        ii = existing2;
                    } else {
                        ii = new InsertionInfoAspectBean();
                    }
                    ii.setSecurityParentId(trashVid.getContentId());
                    builder.aspect(InsertionInfoAspectBean.ASPECT_NAME, ii);
                }

                // Update partition to trash
                Object metaObj = input.getAspect(METADATA_ASPECT_NAME);
                Metadata metadata = metaObj instanceof Metadata m ? m : new Metadata();
                Dimension trashDim = new Dimension(PARTITION_DIMENSION_ID, "partition", true);
                trashDim.addEntities(new Entity(TRASH_PARTITION, TRASH_PARTITION));
                metadata.replaceDimension(trashDim);
                builder.aspect(METADATA_ASPECT_NAME, metadata);
                builder.aspect(PrestigeItemStateAspectBean.ASPECT_NAME, stateBean);

                return builder.build();
            }

            if (newState == ItemState.PRODUCTION && previousState == ItemState.SPIKED) {
                // Restore previous security parent
                String prevSecParent = stateBean.getPreviousSecParent();
                if (prevSecParent != null && !prevSecParent.isEmpty()) {
                    ContentId prevSecParentId = IdUtil.fromString(prevSecParent);
                    InsertionInfoAspectBean ii;
                    Object iiObj = input.getAspect(InsertionInfoAspectBean.ASPECT_NAME);
                    if (iiObj instanceof InsertionInfoAspectBean existing2) {
                        ii = existing2;
                    } else {
                        ii = new InsertionInfoAspectBean();
                    }
                    ii.setSecurityParentId(prevSecParentId);
                    builder.aspect(InsertionInfoAspectBean.ASPECT_NAME, ii);
                }

                // Clear previous sec parent reference
                stateBean.setPreviousSecParent(null);
                builder.aspect(PrestigeItemStateAspectBean.ASPECT_NAME, stateBean);

                return builder.build();
            }

            return input;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error in HandleItemStatePreStore", e);
            return input;
        }
    }
}
