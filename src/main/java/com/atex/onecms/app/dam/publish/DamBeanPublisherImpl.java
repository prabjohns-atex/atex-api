package com.atex.onecms.app.dam.publish;

import com.atex.onecms.app.dam.engagement.EngagementAspect;
import com.atex.onecms.app.dam.engagement.EngagementDesc;
import com.atex.onecms.app.dam.engagement.EngagementElement;
import com.atex.onecms.app.dam.importer.ModuleImporter;
import com.atex.onecms.app.dam.publish.config.RemoteConfigBean;
import com.atex.onecms.app.dam.remote.RemoteContentRefBean;
import com.atex.onecms.app.dam.util.DamEngagementUtils;
import com.atex.onecms.content.Content;
import com.atex.onecms.content.ContentId;
import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.ContentResult;
import com.atex.onecms.content.ContentVersionId;
import com.atex.onecms.content.IdUtil;
import com.atex.onecms.content.Subject;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class DamBeanPublisherImpl implements DamBeanPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(DamBeanPublisherImpl.class);
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();
    private static final Subject SYSTEM_SUBJECT = new Subject("98", null);

    private final ContentManager contentManager;
    private final PublishingContext context;
    private final DamEngagement damEngagement;
    private final DamEngagementUtils engagementUtils;

    public DamBeanPublisherImpl(ContentManager contentManager, PublishingContext context) {
        this.contentManager = contentManager;
        this.context = context;
        this.damEngagement = new DamEngagement(contentManager);
        this.engagementUtils = new DamEngagementUtils(contentManager);
    }

    @Override
    public ContentId publish(ContentId contentId) throws ContentPublisherException {
        Subject subject = context.getSubject();

        // Resolve and fetch source content
        ContentVersionId vid = contentManager.resolve(contentId, subject);
        if (vid == null) {
            throw new ContentPublisherException("Cannot resolve content: " + contentId);
        }
        ContentResult<Object> cr = contentManager.get(vid, Object.class, subject);
        if (cr == null || !cr.getStatus().isSuccess() || cr.getContent() == null) {
            throw new ContentPublisherException("Cannot get content: " + contentId);
        }

        Content<Object> content = cr.getContent();

        // Check for existing remote ID via engagement
        ContentId existingRemoteId = damEngagement.getEngagementId(contentId);

        // Serialize content to JSON
        String json = GSON.toJson(content.getContentData());

        // Create content publisher
        ModulePublisher modulePublisher = createModulePublisher();
        String username = context.getCaller() != null ? context.getCaller().getLoginName() : null;
        ContentPublisher publisher = modulePublisher.createContentPublisher(username);

        // Publish or update
        PublishResult result;
        if (existingRemoteId != null) {
            LOG.info("Updating existing remote content {} for source {}", existingRemoteId, contentId);
            result = publisher.publishContentUpdate(existingRemoteId, json);
        } else {
            LOG.info("Publishing new content for source {}", contentId);
            result = publisher.publishContent(json);
        }

        // Record engagement
        ContentId remoteId = result.getId() != null ? IdUtil.fromString(result.getId()) : existingRemoteId;
        if (remoteId != null && existingRemoteId == null) {
            recordEngagement(contentId, remoteId, username, subject);
        }

        return remoteId;
    }

    @Override
    public ContentId unpublish(ContentId contentId) throws ContentPublisherException {
        Subject subject = context.getSubject();

        ContentId remoteId = damEngagement.getEngagementId(contentId);
        if (remoteId == null) {
            LOG.warn("No remote engagement found for {}, nothing to unpublish", contentId);
            return null;
        }

        ModulePublisher modulePublisher = createModulePublisher();
        String username = context.getCaller() != null ? context.getCaller().getLoginName() : null;
        ContentPublisher publisher = modulePublisher.createContentPublisher(username);

        publisher.unpublish(remoteId, "{}");
        LOG.info("Unpublished remote content {} for source {}", remoteId, contentId);

        return remoteId;
    }

    @Override
    public String getRemotePublicationUrl(ContentId sourceId, ContentId remoteId) {
        RemoteConfigBean config = context.getRemoteConfiguration();
        if (config == null || config.getRemoteApiUrl() == null) return null;
        if (remoteId == null) return null;
        return config.getRemoteApiUrl() + "/content/contentid/" + IdUtil.toIdString(remoteId);
    }

    @Override
    public ModulePublisher createModulePublisher() {
        RemoteConfigBean config = context.getRemoteConfiguration();
        if (config == null) {
            return new ModulePublisher();
        }
        return new ModulePublisher(config);
    }

    @Override
    public ModuleImporter createModuleImporter() {
        return new ModuleImporter();
    }

    @Override
    public ContentId importContent(String ref) {
        try {
            RemoteConfigBean config = context.getRemoteConfiguration();
            if (config == null) return null;

            String username = context.getCaller() != null ? context.getCaller().getLoginName() : null;
            ModulePublisher modulePublisher = createModulePublisher();
            ContentPublisher publisher = modulePublisher.createContentPublisher(username);

            ContentId remoteId = publisher.resolve(ref);
            if (remoteId == null) {
                LOG.warn("Cannot resolve remote ref: {}", ref);
                return null;
            }

            String json = publisher.getContent(remoteId);
            if (json == null) return null;

            // TODO: full import logic (create local content from remote JSON)
            LOG.info("Import from remote not fully implemented yet, resolved ref={} to {}", ref, remoteId);
            return remoteId;
        } catch (Exception e) {
            LOG.error("Error importing content ref={}: {}", ref, e.getMessage(), e);
            return null;
        }
    }

    @Override
    public String getContent(ContentId contentId) {
        try {
            ModulePublisher modulePublisher = createModulePublisher();
            String username = context.getCaller() != null ? context.getCaller().getLoginName() : null;
            ContentPublisher publisher = modulePublisher.createContentPublisher(username);
            return publisher.getContent(contentId);
        } catch (Exception e) {
            LOG.error("Error getting remote content {}: {}", contentId, e.getMessage(), e);
            return null;
        }
    }

    @Override
    public RemoteContentRefBean getContentReference(ContentId contentId) {
        RemoteContentRefBean ref = new RemoteContentRefBean();
        ref.setContentId(contentId);
        ContentId remoteId = damEngagement.getEngagementId(contentId);
        if (remoteId != null) {
            ref.setRemoteId(IdUtil.toIdString(remoteId));
            String url = getRemotePublicationUrl(contentId, remoteId);
            ref.setRemoteUrl(url);
        }
        RemoteConfigBean config = context.getRemoteConfiguration();
        if (config != null) {
            ref.setRemoteName(config.getId());
        }
        return ref;
    }

    private void recordEngagement(ContentId sourceId, ContentId remoteId, String username, Subject subject) {
        try {
            RemoteConfigBean config = context.getRemoteConfiguration();
            String appType = null;
            if (config != null && config.getEngagement() != null) {
                appType = config.getEngagement().getAppType();
            }
            if (appType == null) {
                appType = "publish";
            }

            EngagementDesc engagement = new EngagementDesc();
            engagement.setAppPk(IdUtil.toIdString(remoteId));
            engagement.setAppType(appType);
            engagement.setUserName(username != null ? username : "system");
            engagement.setTimestamp(DamEngagementUtils.getNowInMillisString());

            List<EngagementElement> attributes = new ArrayList<>();
            EngagementElement idAttr = new EngagementElement();
            idAttr.setName(DamEngagement.ATTR_ID);
            idAttr.setValue(IdUtil.toIdString(remoteId));
            attributes.add(idAttr);

            EngagementElement typeAttr = new EngagementElement();
            typeAttr.setName(DamEngagement.ATTR_TYPE);
            typeAttr.setValue(appType);
            attributes.add(typeAttr);
            engagement.setAttributes(attributes);

            engagementUtils.addEngagement(sourceId, engagement, subject);
        } catch (Exception e) {
            LOG.error("Error recording engagement for {} -> {}: {}", sourceId, remoteId, e.getMessage(), e);
        }
    }
}
