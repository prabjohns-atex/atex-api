package com.atex.onecms.app.dam.mapping;

import com.atex.onecms.app.dam.cfg.BeanMapperConfiguration;
import com.atex.onecms.app.dam.engagement.EngagementDesc;
import com.atex.onecms.app.dam.engagement.EngagementElement;
import com.atex.onecms.app.dam.standard.aspects.OneContentBean;
import com.atex.onecms.app.dam.util.DamEngagementUtils;
import com.atex.onecms.content.Content;
import com.atex.onecms.content.ContentId;
import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.ContentResult;
import com.atex.onecms.content.ContentVersionId;
import com.atex.onecms.content.ContentWrite;
import com.atex.onecms.content.ContentWriteBuilder;
import com.atex.onecms.content.IdUtil;
import com.atex.onecms.content.InsertionInfoAspectBean;
import com.atex.onecms.content.Subject;
import com.atex.onecms.content.aspects.Aspect;
import com.atex.onecms.content.metadata.MetadataInfo;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.polopoly.user.server.Caller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class BeanMapper {

    private static final Logger LOG = LoggerFactory.getLogger(BeanMapper.class);
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();
    private static final Subject SYSTEM_SUBJECT = new Subject("98", null);

    private final ContentManager contentManager;

    public BeanMapper(ContentManager contentManager) {
        this.contentManager = contentManager;
    }

    /**
     * Export (deep-copy) content to a new content item.
     *
     * @param contentId     source content ID
     * @param caller        caller performing the export
     * @param appType       application type for engagement tracking
     * @param domain        destination domain (security parent external ID)
     * @param inputTemplate input template to set on the exported content
     * @param destination   destination identifier
     * @param status        status label to set
     * @return new content ID string
     */
    public String export(ContentId contentId, Caller caller, String appType,
                         String domain, String inputTemplate,
                         String destination, String status) {
        Subject subject = caller != null ? new Subject(caller.getLoginName(), null) : SYSTEM_SUBJECT;

        // Resolve and fetch source content
        ContentVersionId vid = contentManager.resolve(contentId, subject);
        if (vid == null) {
            throw new IllegalStateException("Cannot resolve content: " + contentId);
        }
        ContentResult<Object> cr = contentManager.get(vid, Object.class, subject);
        if (cr == null || !cr.getStatus().isSuccess() || cr.getContent() == null) {
            throw new IllegalStateException("Cannot get content: " + contentId);
        }

        Content<Object> sourceContent = cr.getContent();

        // Deep-copy main bean via Gson
        Object sourceBean = sourceContent.getContentData();
        Object clonedBean = sourceBean;
        if (sourceBean != null) {
            String json = GSON.toJson(sourceBean);
            clonedBean = GSON.fromJson(json, sourceBean.getClass());
        }

        // Reset creation date for OneContentBean subtypes
        if (clonedBean instanceof OneContentBean ocb) {
            ocb.setCreationdate(new Date());
        } else if (clonedBean instanceof Map<?,?> map) {
            // If it's a map (generic), reset creationdate field
            @SuppressWarnings("unchecked")
            Map<String, Object> mutableMap = (Map<String, Object>) clonedBean;
            if (mutableMap.containsKey("creationdate")) {
                mutableMap.put("creationdate", System.currentTimeMillis());
            }
        }

        // Set input template on cloned bean if provided
        if (inputTemplate != null && !inputTemplate.isEmpty() && clonedBean instanceof OneContentBean ocb) {
            ocb.setInputTemplate(inputTemplate);
        }

        // Build ContentWrite with cloned bean and copied aspects
        ContentWriteBuilder<Object> cwb = new ContentWriteBuilder<>();
        cwb.type(sourceContent.getContentDataType());
        cwb.mainAspectData(clonedBean);

        // Copy aspects (deep-copy each via Gson)
        for (Aspect aspect : sourceContent.getAspects()) {
            String aspectName = aspect.getName();
            Object aspectData = aspect.getData();
            if (aspectData != null) {
                String aspectJson = GSON.toJson(aspectData);
                Object clonedAspect = GSON.fromJson(aspectJson, aspectData.getClass());
                cwb.aspect(aspectName, clonedAspect);
            }
        }

        // Set InsertionInfoAspectBean with security parent from destination domain
        if (domain != null && !domain.isEmpty()) {
            InsertionInfoAspectBean insertionInfo = new InsertionInfoAspectBean();
            insertionInfo.setSecurityParentIdString(domain);
            insertionInfo.setInsertParentId(domain);
            cwb.aspect(InsertionInfoAspectBean.ASPECT_NAME, insertionInfo);
        }

        // Apply filtering from BeanMapperConfiguration
        BeanMapperConfiguration config = BeanMapperConfiguration.fetch(contentManager, subject);
        if (config != null) {
            for (String excludeAspect : config.getExcludeAspects()) {
                cwb.removeAspect(excludeAspect);
            }

            // Remove excluded dimensions from metadata aspect
            if (!config.getExcludeDimensions().isEmpty()) {
                // MetadataInfo filtering would be done here if present
                LOG.debug("Dimension exclusion configured but metadata filtering is a no-op for now");
            }
        }

        // Remove engagement aspect from export (new content should not inherit engagements)
        cwb.removeAspect("atex.Engagement");

        // Create exported content
        ContentWrite<Object> write = cwb.buildCreate();
        ContentResult<Object> result = contentManager.create(write, subject);
        if (result == null || !result.getStatus().isSuccess()) {
            throw new IllegalStateException("Failed to create exported content from " + contentId);
        }

        ContentId newContentId = result.getContentId().getContentId();
        String newIdStr = IdUtil.toIdString(newContentId);

        // Record engagement on source content
        recordExportEngagement(contentId, newContentId, caller, appType, subject);

        LOG.info("Exported content {} -> {}", IdUtil.toIdString(contentId), newIdStr);
        return newIdStr;
    }

    private void recordExportEngagement(ContentId sourceId, ContentId exportedId,
                                         Caller caller, String appType, Subject subject) {
        try {
            DamEngagementUtils engUtils = new DamEngagementUtils(contentManager);

            EngagementDesc engagement = new EngagementDesc();
            engagement.setAppPk(IdUtil.toIdString(exportedId));
            engagement.setAppType(appType != null ? appType : "export");
            engagement.setUserName(caller != null ? caller.getLoginName() : "system");
            engagement.setTimestamp(DamEngagementUtils.getNowInMillisString());

            List<EngagementElement> attributes = new ArrayList<>();
            EngagementElement idAttr = new EngagementElement();
            idAttr.setName("id");
            idAttr.setValue(IdUtil.toIdString(exportedId));
            attributes.add(idAttr);

            EngagementElement typeAttr = new EngagementElement();
            typeAttr.setName("type");
            typeAttr.setValue(appType != null ? appType : "export");
            attributes.add(typeAttr);
            engagement.setAttributes(attributes);

            engUtils.addEngagement(sourceId, engagement, subject);
        } catch (Exception e) {
            LOG.warn("Failed to record export engagement on {}: {}", sourceId, e.getMessage());
        }
    }
}
