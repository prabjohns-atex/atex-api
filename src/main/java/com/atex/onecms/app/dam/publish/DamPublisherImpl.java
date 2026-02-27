package com.atex.onecms.app.dam.publish;

import com.atex.onecms.app.dam.importer.ModuleImporter;
import com.atex.onecms.app.dam.remote.RemoteContentRefBean;
import com.atex.onecms.content.ContentId;
import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.ContentResult;
import com.atex.onecms.content.ContentVersionId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class DamPublisherImpl implements DamPublisher {
    private static final Logger LOG = LoggerFactory.getLogger(DamPublisherImpl.class);

    private final ContentManager contentManager;
    private final PublishingContext publishingContext;
    private final List<DamPublishConverter> converters = new ArrayList<>();
    private DamBeanPublisher publisher;

    public DamPublisherImpl(ContentManager contentManager, PublishingContext publishingContext) {
        this.contentManager = contentManager;
        this.publishingContext = publishingContext;
    }

    public DamPublisherImpl converter(DamPublishConverter converter) {
        this.converters.add(converter);
        return this;
    }

    public DamPublisherImpl publisher(DamBeanPublisher publisher) {
        this.publisher = publisher;
        return this;
    }

    @Override
    public void addConverter(DamPublishConverter converter) {
        converters.add(converter);
    }

    @Override
    public ContentId publish(ContentId contentId) {
        try {
            // Resolve content and apply converters
            ContentVersionId vid = contentManager.resolve(contentId, publishingContext.getSubject());
            if (vid == null) {
                LOG.warn("Cannot resolve content for publish: {}", contentId);
                return null;
            }
            ContentResult<Object> cr = contentManager.get(vid, Object.class, publishingContext.getSubject());
            if (cr == null || !cr.getStatus().isSuccess()) {
                LOG.warn("Cannot get content for publish: {}", contentId);
                return null;
            }

            // Apply matching converters
            ContentId publishId = contentId;
            Object contentData = cr.getContent().getContentData();
            for (DamPublishConverter converter : converters) {
                if (converter.test(contentData)) {
                    ContentId converted = converter.convert(publishId);
                    if (converted != null) {
                        publishId = converted;
                    }
                }
            }

            // Delegate to bean publisher
            if (publisher != null) {
                return publisher.publish(publishId);
            }
            LOG.warn("No bean publisher configured — publish is a no-op");
            return null;
        } catch (Exception e) {
            LOG.error("Error publishing {}: {}", contentId, e.getMessage(), e);
            return null;
        }
    }

    @Override
    public ContentId unpublish(ContentId contentId) {
        try {
            if (publisher != null) {
                return publisher.unpublish(contentId);
            }
            LOG.warn("No bean publisher configured — unpublish is a no-op");
            return null;
        } catch (Exception e) {
            LOG.error("Error unpublishing {}: {}", contentId, e.getMessage(), e);
            return null;
        }
    }

    @Override
    public String getRemotePublicationUrl(ContentId sourceId, ContentId remoteId) {
        if (publisher != null) {
            return publisher.getRemotePublicationUrl(sourceId, remoteId);
        }
        return null;
    }

    @Override
    public DamBeanPublisher getPublisher() {
        return publisher;
    }

    @Override
    public ModulePublisher createModulePublisher() {
        if (publisher != null) {
            return publisher.createModulePublisher();
        }
        return new ModulePublisher();
    }

    @Override
    public ModuleImporter createModuleImporter() {
        if (publisher != null) {
            return publisher.createModuleImporter();
        }
        return new ModuleImporter();
    }

    @Override
    public String getUserName() {
        return publishingContext.getCaller() != null ?
            publishingContext.getCaller().getLoginName() : null;
    }

    @Override
    public ContentId importContent(String ref) {
        if (publisher != null) {
            return publisher.importContent(ref);
        }
        return null;
    }

    @Override
    public String getContent(ContentId contentId) {
        if (publisher != null) {
            return publisher.getContent(contentId);
        }
        return null;
    }

    @Override
    public RemoteContentRefBean getContentReference(ContentId contentId) {
        if (publisher != null) {
            return publisher.getContentReference(contentId);
        }
        return new RemoteContentRefBean();
    }
}
