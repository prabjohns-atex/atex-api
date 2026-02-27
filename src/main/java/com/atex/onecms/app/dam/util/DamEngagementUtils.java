package com.atex.onecms.app.dam.util;

import com.atex.onecms.app.dam.engagement.EngagementAspect;
import com.atex.onecms.app.dam.engagement.EngagementDesc;
import com.atex.onecms.app.dam.engagement.EngagementElement;
import com.atex.onecms.content.ContentId;
import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.ContentResult;
import com.atex.onecms.content.ContentVersionId;
import com.atex.onecms.content.ContentWrite;
import com.atex.onecms.content.ContentWriteBuilder;
import com.atex.onecms.content.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DamEngagementUtils {

    private static final Logger LOG = LoggerFactory.getLogger(DamEngagementUtils.class);
    private static final Subject SYSTEM_SUBJECT = new Subject("98", null);

    private final ContentManager contentManager;

    public DamEngagementUtils(ContentManager contentManager) {
        this.contentManager = contentManager;
    }

    public ContentResult<Object> addEngagement(ContentId contentId, EngagementDesc engagement, Subject subject) {
        if (contentId == null || engagement == null) {
            LOG.warn("addEngagement: contentId or engagement is null");
            return null;
        }
        if (engagement.getUserName() == null || engagement.getUserName().isEmpty()) {
            LOG.warn("addEngagement: userName is empty");
            return null;
        }
        if (engagement.getTimestamp() == null) {
            engagement.setTimestamp(getNowInMillisString());
        }

        try {
            Subject resolveSubject = subject != null ? subject : SYSTEM_SUBJECT;
            ContentVersionId latestVersion = contentManager.resolve(contentId, resolveSubject);
            if (latestVersion == null) {
                LOG.warn("addEngagement: content not found: {}", contentId);
                return null;
            }

            ContentResult<Object> cr = contentManager.get(latestVersion, null, Object.class, null, resolveSubject);
            if (cr == null || cr.getContent() == null) {
                return null;
            }

            EngagementAspect engagementAspect = cr.getContent().getAspectData(EngagementAspect.ASPECT_NAME);
            if (engagementAspect == null) {
                engagementAspect = new EngagementAspect();
            }

            if (engagement.getTimestamp() == null) {
                engagement.setTimestamp(getNowInMillisString());
            }
            engagementAspect.getEngagementList().add(engagement);

            ContentWriteBuilder<Object> cwb = new ContentWriteBuilder<>();
            cwb.origin(cr.getContent().getId());
            cwb.type(cr.getContent().getContentDataType());
            cwb.aspects(cr.getContent().getAspects());
            cwb.mainAspect(cr.getContent().getContentAspect());
            cwb.aspect(EngagementAspect.ASPECT_NAME, engagementAspect);

            ContentWrite<Object> update = cwb.buildUpdate();
            return contentManager.update(contentId, update, resolveSubject);
        } catch (Exception e) {
            LOG.error("Error adding engagement to {}", contentId, e);
            return null;
        }
    }

    public ContentResult<Object> updateEngagement(ContentId contentId, EngagementDesc engagement, Subject subject) {
        if (contentId == null || engagement == null) {
            LOG.warn("updateEngagement: contentId or engagement is null");
            return null;
        }
        String appPk = engagement.getAppPk();
        if (appPk == null || appPk.isEmpty()) {
            LOG.warn("updateEngagement: appPk is empty");
            return null;
        }
        if (engagement.getUserName() == null || engagement.getUserName().isEmpty()) {
            LOG.warn("updateEngagement: userName is empty");
            return null;
        }

        try {
            Subject resolveSubject = subject != null ? subject : SYSTEM_SUBJECT;
            ContentVersionId latestVersion = contentManager.resolve(contentId, resolveSubject);
            if (latestVersion == null) {
                LOG.warn("updateEngagement: content not found: {}", contentId);
                return null;
            }

            ContentResult<Object> cr = contentManager.get(latestVersion, null, Object.class, null, resolveSubject);
            if (cr == null || cr.getContent() == null) {
                return null;
            }

            EngagementAspect engagementAspect = cr.getContent().getAspectData(EngagementAspect.ASPECT_NAME);
            if (engagementAspect == null) {
                LOG.warn("updateEngagement: no engagement aspect on {}", contentId);
                return null;
            }

            // Find and update matching engagement by appPk
            boolean found = false;
            for (EngagementDesc existing : engagementAspect.getEngagementList()) {
                if (appPk.equals(existing.getAppPk())) {
                    existing.setUserName(engagement.getUserName());
                    existing.setTimestamp(engagement.getTimestamp() != null ? engagement.getTimestamp() : getNowInMillisString());
                    existing.setAppType(engagement.getAppType());

                    // Merge attributes
                    if (engagement.getAttributes() != null && !engagement.getAttributes().isEmpty()) {
                        Map<String, EngagementElement> attrMap = new LinkedHashMap<>();
                        if (existing.getAttributes() != null) {
                            for (EngagementElement el : existing.getAttributes()) {
                                attrMap.put(el.getName(), el);
                            }
                        }
                        for (EngagementElement el : engagement.getAttributes()) {
                            attrMap.put(el.getName(), el);
                        }
                        existing.setAttributes(new java.util.ArrayList<>(attrMap.values()));
                    }
                    found = true;
                    break;
                }
            }

            if (!found) {
                LOG.warn("updateEngagement: no engagement found with appPk={}", appPk);
                return null;
            }

            ContentWriteBuilder<Object> cwb = new ContentWriteBuilder<>();
            cwb.origin(cr.getContent().getId());
            cwb.type(cr.getContent().getContentDataType());
            cwb.aspects(cr.getContent().getAspects());
            cwb.mainAspect(cr.getContent().getContentAspect());
            cwb.aspect(EngagementAspect.ASPECT_NAME, engagementAspect);

            ContentWrite<Object> update = cwb.buildUpdate();
            return contentManager.update(contentId, update, resolveSubject);
        } catch (Exception e) {
            LOG.error("Error updating engagement on {}", contentId, e);
            return null;
        }
    }

    public EngagementAspect getEngagement(ContentId contentId, Subject subject) {
        try {
            Subject resolveSubject = subject != null ? subject : SYSTEM_SUBJECT;
            ContentVersionId latestVersion = contentManager.resolve(contentId, resolveSubject);
            if (latestVersion == null) return null;
            ContentResult<Object> cr = contentManager.get(latestVersion, null, Object.class, null, resolveSubject);
            if (cr == null || cr.getContent() == null) return null;
            return cr.getContent().getAspectData(EngagementAspect.ASPECT_NAME);
        } catch (Exception e) {
            LOG.error("Error getting engagement for {}", contentId, e);
            return null;
        }
    }

    public static String getNowInMillisString() {
        return Long.toString(System.currentTimeMillis());
    }
}
