package com.atex.onecms.app.dam.publish;

import com.atex.onecms.app.dam.engagement.EngagementAspect;
import com.atex.onecms.app.dam.engagement.EngagementDesc;
import com.atex.onecms.app.dam.engagement.EngagementElement;
import com.atex.onecms.app.dam.util.DamEngagementUtils;
import com.atex.onecms.content.ContentId;
import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.IdUtil;
import com.atex.onecms.content.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class DamEngagement {

    private static final Logger LOG = LoggerFactory.getLogger(DamEngagement.class);
    private static final Subject SYSTEM_SUBJECT = new Subject("98", null);

    public static final String ATTR_ID = "id";
    public static final String ATTR_VERSIONED_ID = "versionedId";
    public static final String ATTR_TYPE = "type";

    private final ContentManager contentManager;
    private final DamEngagementUtils engagementUtils;

    public DamEngagement(ContentManager contentManager) {
        this.contentManager = contentManager;
        this.engagementUtils = new DamEngagementUtils(contentManager);
    }

    public ContentId getEngagementId(ContentId sourceId) {
        try {
            return getIdFromEngagement(sourceId);
        } catch (Exception e) {
            LOG.error("Error getting engagement ID for {}", sourceId, e);
            return null;
        }
    }

    public ContentId getEngagementId(EngagementAspect engagementAspect) {
        return getIdFromEngagement(engagementAspect);
    }

    private ContentId getIdFromEngagement(ContentId sourceId) {
        EngagementAspect aspect = engagementUtils.getEngagement(sourceId, SYSTEM_SUBJECT);
        return getIdFromEngagement(aspect);
    }

    private ContentId getIdFromEngagement(EngagementAspect aspect) {
        if (aspect == null) return null;
        List<EngagementDesc> engagements = aspect.getEngagementList();
        if (engagements == null || engagements.isEmpty()) return null;

        // Look for the first engagement with an appPk
        for (EngagementDesc eng : engagements) {
            String appPk = eng.getAppPk();
            if (appPk != null && !appPk.isEmpty()) {
                try {
                    return IdUtil.fromString(appPk);
                } catch (Exception e) {
                    LOG.debug("Cannot parse appPk as ContentId: {}", appPk);
                }
            }
        }
        return null;
    }

    public EngagementDesc findEngagement(EngagementAspect aspect, String appType) {
        if (aspect == null || appType == null) return null;
        for (EngagementDesc eng : aspect.getEngagementList()) {
            if (appType.equals(eng.getAppType())) {
                return eng;
            }
        }
        return null;
    }
}
