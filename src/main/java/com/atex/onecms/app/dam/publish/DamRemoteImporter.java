package com.atex.onecms.app.dam.publish;

import com.atex.onecms.app.dam.remote.RemoteContentRefBean;
import com.atex.onecms.content.ContentId;

public interface DamRemoteImporter {
    ContentId importContent(String ref);
    String getContent(ContentId contentId);
    RemoteContentRefBean getContentReference(ContentId contentId);
}
