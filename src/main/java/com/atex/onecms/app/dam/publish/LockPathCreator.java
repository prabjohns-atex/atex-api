package com.atex.onecms.app.dam.publish;

import com.atex.onecms.content.ContentId;
import com.atex.onecms.content.IdUtil;

public class LockPathCreator {
    public static String createPublishLockPath(ContentId contentId) {
        return "/atex/onecms/desk/publish/locks/" + IdUtil.toIdString(contentId);
    }
}
