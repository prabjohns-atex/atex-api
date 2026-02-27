package com.atex.onecms.app.dam.publish;

import com.atex.onecms.content.ContentManager;
import com.polopoly.cm.client.CmClient;

public class DamPublisherBuilder {
    private CmClient cmClient;
    private ContentManager contentManager;
    private PublishingContext context;

    public DamPublisherBuilder cmClient(CmClient cmClient) {
        this.cmClient = cmClient;
        return this;
    }

    public DamPublisherBuilder contentManager(ContentManager contentManager) {
        this.contentManager = contentManager;
        return this;
    }

    public DamPublisherBuilder context(PublishingContext context) {
        this.context = context;
        return this;
    }

    public DamPublisher build() {
        DamPublisherImpl publisher = new DamPublisherImpl(contentManager, context);

        // Wire DamBeanPublisherImpl when a remote configuration is available
        if (context != null && context.getRemoteConfiguration() != null
                && context.getRemoteConfiguration().getRemoteApiUrl() != null) {
            DamBeanPublisher beanPublisher = new DamBeanPublisherImpl(contentManager, context);
            publisher.publisher(beanPublisher);
        }

        return publisher;
    }
}
