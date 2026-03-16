package com.atex.desk.integration.schedule;

import com.atex.desk.integration.publish.PublishingService;
import com.atex.onecms.content.ContentId;
import com.atex.onecms.content.IdUtil;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Change handler that triggers publishing when content is updated.
 * Ported from gong/desk scheduled publishing and change-driven publish flow.
 *
 * <p>Listens for content updates and publishes content to the remote CMS
 * when the workflow status transitions to a publishable state.
 */
@Component
@ConditionalOnProperty(name = "desk.integration.publishing.enabled", havingValue = "true")
public class PublishChangeHandler implements ChangeProcessor.ChangeHandler {

    private static final Logger LOG = Logger.getLogger(PublishChangeHandler.class.getName());

    private final PublishingService publishingService;

    public PublishChangeHandler(PublishingService publishingService) {
        this.publishingService = publishingService;
    }

    @Override
    public boolean accepts(ChangeProcessor.ChangeEvent event) {
        return event.isUpdate();
    }

    @Override
    public void handle(ChangeProcessor.ChangeEvent event) {
        try {
            ContentId contentId = IdUtil.fromString(event.contentId());
            if (contentId == null) return;

            String remoteId = publishingService.publish(contentId);
            if (remoteId != null) {
                LOG.info("Published via change handler: " + event.contentId() + " -> " + remoteId);
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Publish handler error for " + event.contentId(), e);
        }
    }
}
