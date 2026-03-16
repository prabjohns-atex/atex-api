package com.atex.desk.integration.schedule;

import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.ContentResult;
import com.atex.onecms.content.ContentVersionId;
import com.atex.onecms.content.Subject;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Change handler that processes inbox events.
 * Ported from gong/desk inbox processing logic.
 *
 * <p>When content is created or updated, this handler checks if it needs
 * to be added to an inbox queue for editorial review.
 */
@Component
@ConditionalOnProperty(name = "desk.integration.inbox.enabled", havingValue = "true")
public class InboxChangeHandler implements ChangeProcessor.ChangeHandler {

    private static final Logger LOG = Logger.getLogger(InboxChangeHandler.class.getName());
    private static final Subject SYSTEM_SUBJECT = new Subject("98", null);

    private final ContentManager contentManager;

    public InboxChangeHandler(ContentManager contentManager) {
        this.contentManager = contentManager;
    }

    @Override
    public boolean accepts(ChangeProcessor.ChangeEvent event) {
        return event.isCreate() || event.isUpdate();
    }

    @Override
    public void handle(ChangeProcessor.ChangeEvent event) {
        try {
            ContentVersionId vid = contentManager.resolve(event.contentId(), SYSTEM_SUBJECT);
            if (vid == null) return;

            ContentResult<Object> cr = contentManager.get(vid, null, Object.class,
                Collections.emptyMap(), SYSTEM_SUBJECT);
            if (!cr.getStatus().isSuccess()) return;

            String inputTemplate = cr.getContent().getContentDataType();
            if (inputTemplate == null) return;

            // Wire articles go to inbox
            if (inputTemplate.contains("OneArticleBean") && isWireContent(cr)) {
                LOG.info("Inbox: wire article created/updated: " + event.contentId());
                // The inbox aspect is managed by the pre-store hook chain;
                // this handler could trigger notifications or additional processing
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Inbox handler error for " + event.contentId(), e);
        }
    }

    private boolean isWireContent(ContentResult<Object> cr) {
        var data = cr.getContent().getContentData();
        if (data instanceof com.atex.onecms.app.dam.standard.aspects.OneArticleBean article) {
            String template = article.getInputTemplate();
            return template != null && template.contains("wire");
        }
        return false;
    }
}
