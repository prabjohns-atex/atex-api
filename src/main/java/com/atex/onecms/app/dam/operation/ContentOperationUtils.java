package com.atex.onecms.app.dam.operation;

import com.atex.onecms.content.ContentId;
import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.ContentResult;
import com.atex.onecms.content.ContentVersionId;
import com.atex.onecms.content.ContentWrite;
import com.atex.onecms.content.ContentWriteBuilder;
import com.atex.onecms.content.IdUtil;
import com.atex.onecms.content.Subject;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ContentOperationUtils {

    private static final Logger LOG = LoggerFactory.getLogger(ContentOperationUtils.class);
    private static final Subject SYSTEM_SUBJECT = new Subject("98", null);

    private ContentManager contentManager;

    public static ContentOperationUtils getInstance() {
        return new ContentOperationUtils();
    }

    public void configure(ContentManager cm, Object fileService) {
        this.contentManager = cm;
    }

    // === Extract helpers ===

    public List<String> extract(JsonArray arr) {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) {
            list.add(arr.get(i).getAsString());
        }
        return list;
    }

    public Map<String, String> extract(JsonObject obj) {
        Map<String, String> map = new HashMap<>();
        for (var entry : obj.entrySet()) {
            map.put(entry.getKey(), entry.getValue().getAsString());
        }
        return map;
    }

    public List<String> extract(List<ContentId> ids) {
        List<String> list = new ArrayList<>();
        for (ContentId id : ids) {
            list.add(IdUtil.toIdString(id));
        }
        return list;
    }

    public Map<String, String> extract(Map<ContentId, ContentId> map) {
        Map<String, String> result = new LinkedHashMap<>();
        for (var e : map.entrySet()) {
            result.put(IdUtil.toIdString(e.getKey()), IdUtil.toIdString(e.getValue()));
        }
        return result;
    }

    // === Content operations ===

    public List<ContentId> duplicate(List<String> ids, Subject subject, String loginName) {
        List<ContentId> duplicated = new ArrayList<>();
        if (contentManager == null) {
            LOG.warn("ContentOperationUtils not configured — contentManager is null");
            return duplicated;
        }

        Subject resolveSubject = subject != null ? subject : SYSTEM_SUBJECT;

        for (String idStr : ids) {
            try {
                ContentId sourceId = IdUtil.fromString(idStr);
                ContentVersionId latestVersion = contentManager.resolve(sourceId, resolveSubject);
                if (latestVersion == null) {
                    LOG.warn("duplicate: cannot resolve {}", idStr);
                    continue;
                }

                ContentResult<Object> cr = contentManager.get(latestVersion, null, Object.class, null, resolveSubject);
                if (cr == null || cr.getContent() == null) {
                    LOG.warn("duplicate: cannot get content for {}", idStr);
                    continue;
                }

                // Build a new content write with same data
                ContentWriteBuilder<Object> cwb = new ContentWriteBuilder<>();
                cwb.type(cr.getContent().getContentDataType());
                cwb.mainAspect(cr.getContent().getContentAspect());
                cwb.aspects(cr.getContent().getAspects());

                ContentWrite<Object> create = cwb.buildCreate();
                ContentResult<Object> result = contentManager.create(create, resolveSubject);
                if (result != null && result.getContentId() != null) {
                    duplicated.add(result.getContentId().getContentId());
                }
            } catch (Exception e) {
                LOG.error("Error duplicating {}: {}", idStr, e.getMessage(), e);
            }
        }
        return duplicated;
    }

    public Map<ContentId, ContentId> copyContent(List<String> entries, Map<String, String> imageMap,
                                                  Subject subject, String loginName) {
        Map<ContentId, ContentId> resultMap = new LinkedHashMap<>();
        if (contentManager == null) {
            LOG.warn("ContentOperationUtils not configured — contentManager is null");
            return resultMap;
        }

        Subject resolveSubject = subject != null ? subject : SYSTEM_SUBJECT;

        for (String idStr : entries) {
            try {
                ContentId sourceId = IdUtil.fromString(idStr);
                ContentVersionId latestVersion = contentManager.resolve(sourceId, resolveSubject);
                if (latestVersion == null) {
                    LOG.warn("copyContent: cannot resolve {}", idStr);
                    continue;
                }

                ContentResult<Object> cr = contentManager.get(latestVersion, null, Object.class, null, resolveSubject);
                if (cr == null || cr.getContent() == null) {
                    LOG.warn("copyContent: cannot get content for {}", idStr);
                    continue;
                }

                ContentWriteBuilder<Object> cwb = new ContentWriteBuilder<>();
                cwb.type(cr.getContent().getContentDataType());
                cwb.mainAspect(cr.getContent().getContentAspect());
                cwb.aspects(cr.getContent().getAspects());

                ContentWrite<Object> create = cwb.buildCreate();
                ContentResult<Object> result = contentManager.create(create, resolveSubject);
                if (result != null && result.getContentId() != null) {
                    resultMap.put(sourceId, result.getContentId().getContentId());
                }
            } catch (Exception e) {
                LOG.error("Error copying {}: {}", idStr, e.getMessage(), e);
            }
        }
        return resultMap;
    }
}
