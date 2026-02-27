package com.atex.onecms.app.dam.publish;

import com.atex.onecms.app.dam.importer.ModuleImporter;
import com.atex.onecms.content.ContentId;

public interface DamPublisher extends DamRemoteImporter {
    void addConverter(DamPublishConverter converter);
    ContentId publish(ContentId contentId);
    ContentId unpublish(ContentId contentId);
    String getRemotePublicationUrl(ContentId sourceId, ContentId remoteId);
    DamBeanPublisher getPublisher();
    ModulePublisher createModulePublisher();
    ModuleImporter createModuleImporter();
    String getUserName();
}
