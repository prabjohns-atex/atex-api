package com.atex.onecms.app.dam.publish;

import com.atex.onecms.app.dam.importer.ModuleImporter;
import com.atex.onecms.content.ContentId;

public interface DamBeanPublisher extends DamRemoteImporter {
    ContentId publish(ContentId contentId) throws ContentPublisherException;
    ContentId unpublish(ContentId contentId) throws ContentPublisherException;
    String getRemotePublicationUrl(ContentId sourceId, ContentId remoteId);
    ModulePublisher createModulePublisher();
    ModuleImporter createModuleImporter();
}
