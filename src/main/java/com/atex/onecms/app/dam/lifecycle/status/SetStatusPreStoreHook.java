package com.atex.onecms.app.dam.lifecycle.status;

import com.atex.onecms.app.dam.workflow.WFContentStatusAspectBean;
import com.atex.onecms.app.dam.workflow.WFStatusBean;
import com.atex.onecms.app.dam.workflow.WFStatusUtils;
import com.atex.onecms.app.dam.workflow.WebContentStatusAspectBean;
import com.atex.onecms.content.Content;
import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.ContentOperation;
import com.atex.onecms.content.ContentWrite;
import com.atex.onecms.content.ContentWriteBuilder;
import com.atex.onecms.content.SetStatusOperation;
import com.atex.onecms.content.callback.CallbackException;
import com.atex.onecms.content.lifecycle.LifecycleContextPreStore;
import com.atex.onecms.content.lifecycle.LifecyclePreStore;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Pre-store hook that processes SetStatusOperation from the content write operations list.
 * Resolves status ID to WFStatusBean and updates the workflow status aspects.
 */
public class SetStatusPreStoreHook implements LifecyclePreStore<Object, Object> {

    private static final Logger LOG = Logger.getLogger(SetStatusPreStoreHook.class.getName());

    @Override
    public ContentWrite<Object> preStore(ContentWrite<Object> input, Content<Object> existing,
                                          LifecycleContextPreStore<Object> context)
            throws CallbackException {
        SetStatusOperation statusOp = findStatusOperation(input);
        if (statusOp == null) {
            return input;
        }

        try {
            ContentManager cm = context.getContentManager();
            WFStatusUtils statusUtils = new WFStatusUtils(cm);

            String statusId = statusOp.getStatusId();
            WFStatusBean wfStatus = statusUtils.getStatusById(statusId);
            WFStatusBean webStatus = statusUtils.getWebStatusById(statusId);

            ContentWriteBuilder<Object> builder = ContentWriteBuilder.from(input);

            // Update content workflow status
            if (wfStatus != null) {
                WFContentStatusAspectBean wfAspect = new WFContentStatusAspectBean(
                    wfStatus, statusOp.getComment());
                builder.aspect(WFContentStatusAspectBean.ASPECT_NAME, wfAspect);
            }

            // Update web workflow status
            if (webStatus != null) {
                WebContentStatusAspectBean webAspect = new WebContentStatusAspectBean(
                    webStatus, statusOp.getComment());
                builder.aspect(WebContentStatusAspectBean.ASPECT_NAME, webAspect);
            }

            // Remove the SetStatusOperation from operations (it's been processed)
            builder.filterOperations(op -> !(op instanceof SetStatusOperation));

            return builder.build();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error processing SetStatusOperation", e);
            return input;
        }
    }

    private SetStatusOperation findStatusOperation(ContentWrite<Object> input) {
        for (ContentOperation op : input.getOperations()) {
            if (op instanceof SetStatusOperation sso) {
                return sso;
            }
        }
        return null;
    }
}
