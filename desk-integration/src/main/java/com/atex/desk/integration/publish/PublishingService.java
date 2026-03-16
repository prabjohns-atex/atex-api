package com.atex.desk.integration.publish;

import com.atex.onecms.app.dam.engagement.EngagementAspect;
import com.atex.onecms.app.dam.engagement.EngagementDesc;
import com.atex.onecms.app.dam.engagement.EngagementElement;
import com.atex.onecms.app.dam.workflow.WFContentStatusAspectBean;
import com.atex.onecms.content.ContentId;
import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.ContentResult;
import com.atex.onecms.content.ContentVersionId;
import com.atex.onecms.content.ContentWriteBuilder;
import com.atex.onecms.content.Subject;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main publishing orchestrator.
 * Ported from gong/desk DamPublisherImpl → DamBeanPublisherImpl → BeanPublisher pipeline.
 *
 * <p>Publishes content from local CMS to a remote CMS via REST API.
 * Handles bean mapping, file upload, engagement tracking, and status validation.
 */
@Service
@ConditionalOnProperty(name = "desk.integration.publishing.enabled", havingValue = "true")
public class PublishingService {

    private static final Logger LOG = Logger.getLogger(PublishingService.class.getName());
    private static final Subject SYSTEM_SUBJECT = new Subject("98", null);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String ENGAGEMENT_TYPE_PUBLISH = "publish";
    private static final String ATTR_REMOTE_ID = "remoteContentId";

    private final ContentManager contentManager;
    private final PublishingConfig config;
    private final RemoteContentPublisher remotePublisher;

    public PublishingService(ContentManager contentManager, PublishingConfig config) {
        this.contentManager = contentManager;
        this.config = config;
        this.remotePublisher = new RemoteContentPublisher(config.getRemoteUrl(), config.getRemoteToken());
    }

    /**
     * Publish content to the remote CMS.
     *
     * @param contentId the local content ID to publish
     * @return the remote content ID, or null on failure
     */
    public String publish(ContentId contentId) {
        try {
            ContentVersionId vid = contentManager.resolve(contentId.getKey(), SYSTEM_SUBJECT);
            if (vid == null) {
                LOG.warning("Cannot resolve content for publish: " + contentId);
                return null;
            }

            ContentResult<Object> cr = contentManager.get(vid, null, Object.class,
                Collections.emptyMap(), SYSTEM_SUBJECT);
            if (!cr.getStatus().isSuccess()) {
                LOG.warning("Cannot read content for publish: " + contentId);
                return null;
            }

            // Check if content is publishable (status check)
            if (!isPublishable(cr)) {
                LOG.fine(() -> "Content not publishable: " + contentId);
                return null;
            }

            // Check if already published (engagement tracking)
            String existingRemoteId = getEngagementId(cr);

            // Serialize content for remote publish
            String json = GSON.toJson(cr.getContent().getContentData());

            String remoteId;
            if (existingRemoteId != null) {
                remotePublisher.publishContentUpdate(existingRemoteId, json);
                remoteId = existingRemoteId;
                LOG.info("Updated published content: " + contentId + " -> " + remoteId);
            } else {
                String response = remotePublisher.publishContent(json);
                remoteId = response;
                LOG.info("Published new content: " + contentId + " -> " + remoteId);
            }

            // Record engagement
            addPublishEngagement(vid, remoteId);
            return remoteId;

        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to publish content: " + contentId, e);
            return null;
        }
    }

    /**
     * Unpublish content from the remote CMS.
     */
    public boolean unpublish(ContentId contentId) {
        try {
            ContentVersionId vid = contentManager.resolve(contentId.getKey(), SYSTEM_SUBJECT);
            if (vid == null) return false;

            ContentResult<Object> cr = contentManager.get(vid, null, Object.class,
                Collections.emptyMap(), SYSTEM_SUBJECT);
            if (!cr.getStatus().isSuccess()) return false;

            String remoteId = getEngagementId(cr);
            if (remoteId == null) {
                LOG.fine(() -> "Content not published, nothing to unpublish: " + contentId);
                return true;
            }

            remotePublisher.unpublish(remoteId);
            LOG.info("Unpublished content: " + contentId + " (remote: " + remoteId + ")");
            return true;

        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to unpublish content: " + contentId, e);
            return false;
        }
    }

    private boolean isPublishable(ContentResult<Object> cr) {
        var statusAspect = cr.getContent().getAspect(WFContentStatusAspectBean.ASPECT_NAME);
        if (statusAspect != null && statusAspect.getData() instanceof WFContentStatusAspectBean wfStatus) {
            String statusName = wfStatus.getStatus() != null ? wfStatus.getStatus().getName() : null;
            String contentType = cr.getContent().getContentDataType();

            Map<String, List<String>> nonPublishable = config.getNonPublishableStatus();
            if (nonPublishable.containsKey(contentType)
                && nonPublishable.get(contentType).contains(statusName)) {
                return false;
            }
            if (nonPublishable.containsKey("*")
                && nonPublishable.get("*").contains(statusName)) {
                return false;
            }
        }
        return true;
    }

    private String getEngagementId(ContentResult<Object> cr) {
        var engAspect = cr.getContent().getAspect(EngagementAspect.ASPECT_NAME);
        if (engAspect != null && engAspect.getData() instanceof EngagementAspect engagement) {
            for (EngagementDesc desc : engagement.getEngagementList()) {
                if (ENGAGEMENT_TYPE_PUBLISH.equals(desc.getType())) {
                    for (EngagementElement attr : desc.getAttributes()) {
                        if (ATTR_REMOTE_ID.equals(attr.getKey())) {
                            return attr.getValue();
                        }
                    }
                }
            }
        }
        return null;
    }

    private void addPublishEngagement(ContentVersionId vid, String remoteId) {
        try {
            ContentResult<Object> cr = contentManager.get(vid, null, Object.class,
                Collections.emptyMap(), SYSTEM_SUBJECT);
            if (!cr.getStatus().isSuccess()) return;

            EngagementAspect engagement;
            var engAspect = cr.getContent().getAspect(EngagementAspect.ASPECT_NAME);
            if (engAspect != null && engAspect.getData() instanceof EngagementAspect existing) {
                engagement = existing;
            } else {
                engagement = new EngagementAspect();
            }

            EngagementDesc desc = new EngagementDesc();
            desc.setType(ENGAGEMENT_TYPE_PUBLISH);
            desc.setTimestamp(String.valueOf(System.currentTimeMillis()));

            EngagementElement remoteIdAttr = new EngagementElement();
            remoteIdAttr.setKey(ATTR_REMOTE_ID);
            remoteIdAttr.setValue(remoteId);
            desc.getAttributes().add(remoteIdAttr);

            engagement.getEngagementList().add(desc);

            ContentWriteBuilder<Object> builder = new ContentWriteBuilder<>();
            builder.mainAspectData(cr.getContent().getContentData());
            builder.type(cr.getContent().getContentDataType());
            builder.aspect(EngagementAspect.ASPECT_NAME, engagement);
            contentManager.update(vid.getContentId(), builder.buildUpdate(), SYSTEM_SUBJECT);

        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to record publish engagement for " + vid, e);
        }
    }
}
