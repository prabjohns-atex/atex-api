package com.atex.onecms.app.dam.lifecycle.engagement;

import com.atex.onecms.app.dam.util.DamEngagementUtils;
import com.atex.onecms.content.AddEngagement;
import com.atex.onecms.content.Content;
import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.ContentOperation;
import com.atex.onecms.content.ContentWrite;
import com.atex.onecms.content.ContentWriteBuilder;
import com.atex.onecms.content.Subject;
import com.atex.onecms.content.callback.CallbackException;
import com.atex.onecms.content.lifecycle.LifecycleContextPreStore;
import com.atex.onecms.content.lifecycle.LifecyclePreStore;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Pre-store hook that processes AddEngagement operations.
 * Delegates to DamEngagementUtils to add engagement records to source content.
 */
public class AddEngagementPreStoreHook implements LifecyclePreStore<Object, Object> {

    private static final Logger LOG = Logger.getLogger(AddEngagementPreStoreHook.class.getName());

    @Override
    public ContentWrite<Object> preStore(ContentWrite<Object> input, Content<Object> existing,
                                          LifecycleContextPreStore<Object> context)
            throws CallbackException {
        boolean hasEngagementOps = false;
        for (ContentOperation op : input.getOperations()) {
            if (op instanceof AddEngagement) {
                hasEngagementOps = true;
                break;
            }
        }
        if (!hasEngagementOps) {
            return input;
        }

        try {
            ContentManager cm = context.getContentManager();
            Subject subject = context.getSubject();
            DamEngagementUtils engUtils = new DamEngagementUtils(cm);

            for (ContentOperation op : input.getOperations()) {
                if (op instanceof AddEngagement addEng) {
                    if (addEng.getSourceId() != null && addEng.getEngagement() != null) {
                        engUtils.addEngagement(addEng.getSourceId(), addEng.getEngagement(), subject);
                    }
                }
            }

            // Remove processed AddEngagement operations
            ContentWriteBuilder<Object> builder = ContentWriteBuilder.from(input);
            builder.filterOperations(op -> !(op instanceof AddEngagement));
            return builder.build();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error processing AddEngagement operations", e);
            return input;
        }
    }
}
