package com.atex.onecms.app.dam.publish;

import com.atex.onecms.content.ContentId;

public interface DamPublishConverter {
    boolean test(Object contentData);
    ContentId convert(ContentId contentId);
}
